package com.poseidon.javastatic.extract.jdt.trace.spi;

import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import java.util.List;

public interface JdtTraceResolver {

    default List<String> resolveField(
            FieldDeclaration field,
            VariableDeclarationFragment fragment,
            TypeDeclaration typeDeclaration,
            JdtTraceContext context) {
        return List.of();
    }

    default List<String> resolveMethodCall(
            MethodInvocation invocation,
            TypeDeclaration typeDeclaration,
            MethodDeclaration method,
            JdtTraceContext context) {
        return List.of();
    }
}
