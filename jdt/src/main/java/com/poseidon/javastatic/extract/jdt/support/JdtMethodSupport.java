package com.poseidon.javastatic.extract.jdt.support;

import com.poseidon.javastatic.extract.source.MethodSelector;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodInvocation;

public final class JdtMethodSupport {

    private JdtMethodSupport() {}

    public static boolean matchesMethod(MethodInvocation invocation, MethodSelector selector) {
        String methodName = invocation.getName().getIdentifier();
        if (selector.names() != null && !selector.names().isEmpty() && !selector.names().contains(methodName)) {
            return false;
        }
        if (selector.namePattern() != null && !methodName.matches(selector.namePattern())) {
            return false;
        }
        if (selector.ownerType() == null && selector.ownerTypePattern() == null) {
            return true;
        }
        String owner = invocationOwnerType(invocation);
        if (owner == null) {
            return false;
        }
        if (selector.ownerTypePattern() != null) {
            return owner.matches(selector.ownerTypePattern());
        }
        return owner.equals(selector.ownerType())
                || owner.endsWith("." + selector.ownerType())
                || JdtNodeSupport.simpleTypeName(owner).equals(selector.ownerType());
    }

    public static String invocationOwnerType(MethodInvocation invocation) {
        if (invocation.getExpression() == null) {
            return null;
        }
        ITypeBinding binding = invocation.getExpression().resolveTypeBinding();
        if (binding != null) {
            String qualifiedName = binding.getQualifiedName();
            if (qualifiedName != null && !qualifiedName.isBlank()) {
                return qualifiedName;
            }
        }
        return null;
    }
}
