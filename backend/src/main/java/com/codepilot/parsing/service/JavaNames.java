package com.codepilot.parsing.service;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;

import java.util.Optional;

/**
 * The single definition of how a Java element is named across CodePilot.
 *
 * Both JavaAstParser (which names code chunks) and SymbolExtractor (which names symbols and
 * references) MUST use this. If the two ever produced different strings for the same method,
 * symbol lookups would silently fail to match the chunks they describe.
 */
public final class JavaNames {

    private JavaNames() {
    }

    /** "com.codepilot.chat.service.ChatService" for anything inside that type. */
    public static String enclosingTypeName(Node node) {
        String packageName = node.findCompilationUnit()
                .flatMap(CompilationUnit::getPackageDeclaration)
                .map(pd -> pd.getNameAsString())
                .orElse("");

        Optional<TypeDeclaration> enclosingType = node.findAncestor(TypeDeclaration.class);
        String typeName = enclosingType.map(TypeDeclaration::getNameAsString).orElse("Unknown");

        return packageName.isEmpty() ? typeName : packageName + "." + typeName;
    }

    /** "com.codepilot.chat.service.ChatService#chat" */
    public static String member(String enclosingTypeName, String memberName) {
        return enclosingTypeName + "#" + memberName;
    }
}