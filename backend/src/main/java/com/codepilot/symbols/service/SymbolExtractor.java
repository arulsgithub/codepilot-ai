package com.codepilot.symbols.service;

import com.codepilot.parsing.service.JavaNames;
import com.codepilot.symbols.dto.FileSymbols;
import com.codepilot.symbols.dto.ReferenceKind;
import com.codepilot.symbols.dto.SymbolKind;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Extracts declarations (symbols) and usages (references) from Java source.
 *
 * Symbol resolution strategy: a CombinedTypeSolver over the JDK (ReflectionTypeSolver) plus every
 * src/main/java root found in the repository. That resolves the project's OWN types - which is
 * what "find all usages" is actually asking about - and generally will NOT resolve third-party
 * types (Spring, Lombok), because doing so would require feeding every dependency JAR from the
 * local Maven repository to the solver: slow and brittle.
 *
 * Unresolved references are still recorded, with resolved=false and a null qualified name, so
 * the imprecision is explicit in the data instead of being silently dropped or silently wrong.
 */
@Component
public class SymbolExtractor {

    private static final Logger log = LoggerFactory.getLogger(SymbolExtractor.class);

    /**
     * Builds a parser wired to a symbol solver scoped to this repository. Build it ONCE per
     * indexing run and reuse it - constructing type solvers is expensive.
     */
    public JavaParser createParser(String repositoryRoot) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver();
        typeSolver.add(new ReflectionTypeSolver());   // JDK types

        for (Path sourceRoot : findSourceRoots(Path.of(repositoryRoot))) {
            typeSolver.add(new JavaParserTypeSolver(sourceRoot));
            log.info("Symbol solver source root: {}", sourceRoot);
        }

        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));

        return new JavaParser(configuration);
    }

    /** Finds every ".../src/main/java" directory, so multi-module repos work too. */
    private List<Path> findSourceRoots(Path repositoryRoot) {
        List<Path> roots = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(repositoryRoot, 6)) {
            paths.filter(Files::isDirectory)
                    .filter(p -> p.endsWith(Path.of("src", "main", "java")))
                    .forEach(roots::add);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to locate source roots under " + repositoryRoot, e);
        }
        return roots;
    }

    public FileSymbols extract(JavaParser parser, String relativeFilePath, String sourceCode) {

        ParseResult<CompilationUnit> parseResult = parser.parse(sourceCode);
        Optional<CompilationUnit> maybeUnit = parseResult.getResult()
                .filter(cu -> parseResult.isSuccessful());
        if (maybeUnit.isEmpty()) {
            log.warn("Symbol extraction skipped for {} - could not parse", relativeFilePath);
            return new FileSymbols(List.of(), List.of());
        }

        List<FileSymbols.ExtractedSymbol> symbols = new ArrayList<>();
        List<FileSymbols.ExtractedReference> references = new ArrayList<>();

        maybeUnit.get().accept(new VoidVisitorAdapter<Void>() {

            // ---------- declarations ----------

            @Override
            public void visit(ClassOrInterfaceDeclaration decl, Void arg) {
                addSymbol(decl.isInterface() ? SymbolKind.INTERFACE : SymbolKind.CLASS,
                        JavaNames.enclosingTypeName(decl), decl.getNameAsString(),
                        decl.getNameAsString(), decl);
                super.visit(decl, arg);
            }

            @Override
            public void visit(EnumDeclaration decl, Void arg) {
                addSymbol(SymbolKind.ENUM, JavaNames.enclosingTypeName(decl),
                        decl.getNameAsString(), decl.getNameAsString(), decl);
                super.visit(decl, arg);
            }

            @Override
            public void visit(MethodDeclaration decl, Void arg) {
                addSymbol(SymbolKind.METHOD,
                        JavaNames.member(JavaNames.enclosingTypeName(decl), decl.getNameAsString()),
                        decl.getNameAsString(),
                        decl.getDeclarationAsString(false, false, true), decl);
                super.visit(decl, arg);
            }

            @Override
            public void visit(ConstructorDeclaration decl, Void arg) {
                addSymbol(SymbolKind.CONSTRUCTOR,
                        JavaNames.member(JavaNames.enclosingTypeName(decl), "<init>"),
                        decl.getNameAsString(),
                        decl.getDeclarationAsString(false, false, true), decl);
                super.visit(decl, arg);
            }

            @Override
            public void visit(FieldDeclaration decl, Void arg) {
                // One declaration can define several variables ("int a, b;") - one symbol each.
                decl.getVariables().forEach(variable -> addSymbol(SymbolKind.FIELD,
                        JavaNames.member(JavaNames.enclosingTypeName(decl), variable.getNameAsString()),
                        variable.getNameAsString(),
                        decl.toString().trim(), decl));
                super.visit(decl, arg);
            }

            // ---------- usages ----------

            @Override
            public void visit(MethodCallExpr call, Void arg) {
                String simpleName = call.getNameAsString();
                String qualifiedName = null;
                boolean resolved = false;
                try {
                    ResolvedMethodDeclaration declaration = call.resolve();
                    qualifiedName = JavaNames.member(
                            declaration.declaringType().getQualifiedName(), declaration.getName());
                    resolved = true;
                } catch (Exception e) {
                    // Expected for third-party/unresolvable types - record the name only.
                }
                addReference(ReferenceKind.METHOD_CALL, qualifiedName, simpleName, resolved, call);
                super.visit(call, arg);
            }

            @Override
            public void visit(ObjectCreationExpr creation, Void arg) {
                String simpleName = creation.getType().getNameAsString();
                String qualifiedName = null;
                boolean resolved = false;
                try {
                    ResolvedConstructorDeclaration declaration = creation.resolve();
                    qualifiedName = JavaNames.member(
                            declaration.declaringType().getQualifiedName(), "<init>");
                    resolved = true;
                } catch (Exception e) {
                    // name-only
                }
                addReference(ReferenceKind.CONSTRUCTOR_CALL, qualifiedName, simpleName, resolved, creation);
                super.visit(creation, arg);
            }

            @Override
            public void visit(ClassOrInterfaceType type, Void arg) {
                String simpleName = type.getNameAsString();
                String qualifiedName = null;
                boolean resolved = false;
                try {
                    ResolvedType resolvedType = type.resolve();
                    if (resolvedType.isReferenceType()) {
                        qualifiedName = resolvedType.asReferenceType().getQualifiedName();
                        resolved = true;
                    }
                } catch (Exception e) {
                    // name-only
                }
                addReference(ReferenceKind.TYPE_USE, qualifiedName, simpleName, resolved, type);
                super.visit(type, arg);
            }

            // ---------- helpers ----------

            private void addSymbol(SymbolKind kind, String qualifiedName, String simpleName,
                                   String signature, Node node) {
                symbols.add(new FileSymbols.ExtractedSymbol(
                        kind, qualifiedName, simpleName, signature,
                        node.getBegin().map(p -> p.line).orElse(0),
                        node.getEnd().map(p -> p.line).orElse(0)));
            }

            private void addReference(ReferenceKind kind, String targetQualifiedName,
                                      String targetSimpleName, boolean resolved, Node node) {
                references.add(new FileSymbols.ExtractedReference(
                        kind, targetQualifiedName, targetSimpleName,
                        enclosingMemberName(node), resolved,
                        node.getBegin().map(p -> p.line).orElse(0)));
            }

            /** Which method/constructor contains this usage - i.e. who the caller is. */
            private String enclosingMemberName(Node node) {
                Optional<MethodDeclaration> method = node.findAncestor(MethodDeclaration.class);
                if (method.isPresent()) {
                    return JavaNames.member(JavaNames.enclosingTypeName(method.get()),
                            method.get().getNameAsString());
                }
                Optional<ConstructorDeclaration> constructor =
                        node.findAncestor(ConstructorDeclaration.class);
                if (constructor.isPresent()) {
                    return JavaNames.member(JavaNames.enclosingTypeName(constructor.get()), "<init>");
                }
                // Field initialisers and static blocks have no enclosing member - the type will do.
                return JavaNames.enclosingTypeName(node);
            }

        }, null);

        return new FileSymbols(symbols, references);
    }
}