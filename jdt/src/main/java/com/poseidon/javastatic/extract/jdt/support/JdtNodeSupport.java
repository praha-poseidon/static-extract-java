package com.poseidon.javastatic.extract.jdt.support;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;

public final class JdtNodeSupport {

    private JdtNodeSupport() {}

    public static MethodDeclaration enclosingMethod(ASTNode node) {
        ASTNode current = node;
        while (current != null && !(current instanceof MethodDeclaration)) {
            current = current.getParent();
        }
        return (MethodDeclaration) current;
    }

    public static int lineStart(CompilationUnit cu, ASTNode node) {
        return cu.getLineNumber(node.getStartPosition());
    }

    public static int lineEnd(CompilationUnit cu, ASTNode node) {
        return cu.getLineNumber(node.getStartPosition() + Math.max(node.getLength() - 1, 0));
    }

    public static String enclosingMethodHint(CompilationUnit cu, TypeDeclaration typeDeclaration, ASTNode anchor) {
        MethodDeclaration method = enclosingMethod(anchor);
        if (method == null) {
            return null;
        }
        IMethodBinding binding = method.resolveBinding();
        if (binding != null && binding.getDeclaringClass() != null) {
            return binding.getDeclaringClass().getQualifiedName() + "." + binding.getName();
        }
        String pkg = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() + "." : "";
        return pkg + typeDeclaration.getName().getIdentifier() + "." + method.getName().getIdentifier();
    }

    public static String typeName(ITypeBinding binding) {
        if (binding == null) {
            return "";
        }
        if (binding.isArray()) {
            return typeName(binding.getElementType()) + "[]";
        }
        if (binding.isPrimitive()) {
            return binding.getName();
        }
        String qualified = binding.getErasure().getQualifiedName();
        return qualified != null ? qualified : binding.getName();
    }

    public static String simpleTypeName(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1) : name;
    }
}
