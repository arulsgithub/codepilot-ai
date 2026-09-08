package com.codepilot.parsing.service;

import com.codepilot.parsing.dto.CodeUnit;
import com.codepilot.parsing.dto.CodeUnitType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Turns one Java source file's text into a flat list of CodeUnits (classes, methods,
 * constructors, fields). We use a VoidVisitorAdapter rather than manually recursing the AST
 * ourselves - JavaParser's visitor already handles nested classes/interfaces/enums correctly,
 * which is easy to get subtly wrong by hand (e.g. missing a static nested class).
 */
@Component
public class JavaAstParser {

    // StaticJavaParser defaults to LanguageLevel.POPULAR (~Java 8 grammar), which rejects
    // records, switch expressions, text blocks and pattern instanceof - most of a modern
    // codebase. Use a dedicated parser pinned to the project's language level (Java 21).
    private final JavaParser javaParser = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    /**
     * @param relativeFilePath path shown in results (e.g. "com/codepilot/chat/service/ChatService.java")
     * @param sourceCode       full text of the .java file
     * @return every class/interface/enum/method/constructor/field declared directly in this file
     * @throws com.github.javaparser.ParseProblemException if the file has invalid syntax -
     *         caller decides whether to skip the file or fail the whole scan.
     */
    public List<CodeUnit> parse(String relativeFilePath, String sourceCode) {
        ParseResult<CompilationUnit> parseResult = javaParser.parse(sourceCode);
        CompilationUnit compilationUnit = parseResult.getResult()
                .filter(cu -> parseResult.isSuccessful())
                .orElseThrow(() -> new ParseProblemException(parseResult.getProblems()));
        List<CodeUnit> units = new ArrayList<>();

        compilationUnit.accept(new VoidVisitorAdapter<Void>() {

            @Override
            public void visit(ClassOrInterfaceDeclaration decl, Void arg) {
                units.add(toCodeUnit(
                        decl.isInterface() ? CodeUnitType.INTERFACE : CodeUnitType.CLASS,
                        relativeFilePath,
                        qualifiedNameOf(decl),
                        decl.getNameAsString(),
                        decl
                ));
                super.visit(decl, arg); // still descend into methods/fields inside
            }

            @Override
            public void visit(EnumDeclaration decl, Void arg) {
                units.add(toCodeUnit(
                        CodeUnitType.ENUM,
                        relativeFilePath,
                        qualifiedNameOf(decl),
                        decl.getNameAsString(),
                        decl
                ));
                super.visit(decl, arg);
            }

            @Override
            public void visit(MethodDeclaration decl, Void arg) {
                units.add(toCodeUnit(
                        CodeUnitType.METHOD,
                        relativeFilePath,
                        qualifiedNameOf(decl) + "#" + decl.getNameAsString(),
                        decl.getDeclarationAsString(false, false, true),
                        decl
                ));
                super.visit(decl, arg);
            }

            @Override
            public void visit(ConstructorDeclaration decl, Void arg) {
                units.add(toCodeUnit(
                        CodeUnitType.CONSTRUCTOR,
                        relativeFilePath,
                        qualifiedNameOf(decl) + "#<init>",
                        decl.getDeclarationAsString(false, false, true),
                        decl
                ));
                super.visit(decl, arg);
            }

            @Override
            public void visit(FieldDeclaration decl, Void arg) {
                // One FieldDeclaration can declare multiple variables (e.g. "int a, b;") -
                // emit one CodeUnit per variable so each is individually addressable.
                decl.getVariables().forEach(variable -> units.add(toCodeUnit(
                        CodeUnitType.FIELD,
                        relativeFilePath,
                        qualifiedNameOf(decl) + "#" + variable.getNameAsString(),
                        decl.toString().trim(),
                        decl
                )));
                super.visit(decl, arg);
            }

        }, null);

        return units;
    }

    private CodeUnit toCodeUnit(CodeUnitType type, String relativeFilePath, String qualifiedName,
                                String signature, com.github.javaparser.ast.Node node) {
        int startLine = node.getBegin().map(p -> p.line).orElse(0);
        int endLine = node.getEnd().map(p -> p.line).orElse(0);
        return new CodeUnit(type, relativeFilePath, qualifiedName, signature, node.toString(), startLine, endLine);
    }

    /**
     * Builds "com.codepilot.chat.service.ChatService" style names by walking up through any
     * enclosing type declarations and prefixing the file's package - needed so two classes
     * named "Builder" in different packages don't collide in the vector store later.
     */
    private String qualifiedNameOf(BodyDeclaration<?> decl) {
        Optional<CompilationUnit> compilationUnit = decl.findCompilationUnit();
        String packageName = compilationUnit
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getNameAsString())
                .orElse("");

        Optional<TypeDeclaration> enclosingType = decl.findAncestor(TypeDeclaration.class);
        String typeName = enclosingType.map(TypeDeclaration::getNameAsString).orElse("Unknown");

        return packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }
}