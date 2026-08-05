package com.poseidon.javastatic.extract.jdt.source;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public record JdtEvalContext(
        CompilationUnit compilationUnit,
        TypeDeclaration typeDeclaration,
        ASTNode anchorNode) {}
