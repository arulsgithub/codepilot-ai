package com.codepilot.parsing.service;

import com.codepilot.edit.service.LineEndings;
import com.codepilot.parsing.dto.CodeUnit;
import com.codepilot.parsing.dto.CodeUnitType;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

        // The file's real lines, kept so every unit's content is the ORIGINAL text.
        // See originalText() below for why this matters.
        // Normalise first: a CRLF file would otherwise leave a trailing '\r' on every chunk line,
        // which reaches the model as invisible noise.
        String[] sourceLines = LineEndings.normalize(sourceCode).split("\n", -1);

        List<CodeUnit> units = new ArrayList<>();

        compilationUnit.accept(new VoidVisitorAdapter<Void>() {

            @Override
            public void visit(ClassOrInterfaceDeclaration decl, Void arg) {
                units.add(toCodeUnit(
                        decl.isInterface() ? CodeUnitType.INTERFACE : CodeUnitType.CLASS,
                        relativeFilePath,
                        qualifiedNameOf(decl),
                        decl.getNameAsString(),
                        decl,
                        sourceLines
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
                        decl,
                        sourceLines
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
                        decl,
                        sourceLines
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
                        decl,
                        sourceLines
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
                        decl,
                        sourceLines
                )));
                super.visit(decl, arg);
            }

        }, null);

        return units;
    }

    private CodeUnit toCodeUnit(CodeUnitType type, String relativeFilePath, String qualifiedName,
                                String signature, Node node, String[] sourceLines) {
        int startLine = startLineOf(node);
        int endLine = node.getEnd().map(p -> p.line).orElse(0);
        return new CodeUnit(type, relativeFilePath, qualifiedName, signature,
                originalText(node, sourceLines, startLine, endLine), startLine, endLine);
    }

    /**
     * Start line INCLUDING the element's doc comment, if it has one.
     *
     * JavaParser reports a declaration's begin position after its Javadoc, but the comment is
     * genuinely part of what a reader (or an editing model) needs to see, and including it keeps
     * the stored text contiguous with what's in the file.
     */
    private int startLineOf(Node node) {
        return node.getComment()
                .flatMap(comment -> comment.getBegin())
                .map(position -> position.line)
                .orElseGet(() -> node.getBegin().map(position -> position.line).orElse(0));
    }

    /**
     * Returns the element's text EXACTLY as it appears in the file, including leading indentation.
     *
     * This must never be node.toString(): JavaParser re-prints a node from the AST rather than
     * echoing the source, which normalises indentation to column 0. Chunks built that way don't
     * match the real file - fine for answering questions, fatal for the edit feature, whose whole
     * safety model is that SEARCH text matches the file byte-for-byte.
     *
     * Slicing whole lines (rather than exact column offsets) is deliberate: it preserves the
     * leading whitespace, which is precisely what was being lost.
     */
    private String originalText(Node node, String[] sourceLines, int startLine, int endLine) {
        if (startLine < 1 || endLine < startLine || endLine > sourceLines.length) {
            // Shouldn't happen, but a re-printed body beats losing the unit entirely.
            return node.toString();
        }
        return String.join("\n", Arrays.copyOfRange(sourceLines, startLine - 1, endLine));
    }

    /**
     * Delegates to JavaNames so code chunks and symbols (SymbolExtractor) always agree on
     * qualified names - they are joined on this string.
     */
    private String qualifiedNameOf(BodyDeclaration<?> decl) {
        return JavaNames.enclosingTypeName(decl);
    }
}