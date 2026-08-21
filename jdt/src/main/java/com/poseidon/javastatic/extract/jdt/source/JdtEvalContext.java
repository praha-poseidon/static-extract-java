package com.poseidon.javastatic.extract.jdt.source;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.Map;

public record JdtEvalContext(
        CompilationUnit compilationUnit,
        TypeDeclaration typeDeclaration,
        ASTNode anchorNode,
        Map<String, String> identityDict) {

    public JdtEvalContext(
            CompilationUnit compilationUnit,
            TypeDeclaration typeDeclaration,
            ASTNode anchorNode) {
        this(compilationUnit, typeDeclaration, anchorNode, Map.of());
    }

    public JdtEvalContext {
        identityDict = identityDict == null ? Map.of() : identityDict;
    }
}
