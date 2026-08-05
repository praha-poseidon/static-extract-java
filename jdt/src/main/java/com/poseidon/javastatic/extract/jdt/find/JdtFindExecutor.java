package com.poseidon.javastatic.extract.jdt.find;

import com.poseidon.javastatic.extract.jdt.support.JdtAnnotationSupport;
import com.poseidon.javastatic.extract.jdt.support.JdtMethodSupport;
import com.poseidon.javastatic.extract.rule.FindSpec;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JdtFindExecutor {

    public List<ASTNode> find(FindSpec find, TypeDeclaration typeDeclaration) {
        if (find.annotation() != null) {
            return findByAnnotation(find, typeDeclaration);
        }
        if (find.target() == JavaElementKind.CLASS) {
            return List.of(typeDeclaration);
        }
        if (find.target() == JavaElementKind.FIELD) {
            List<ASTNode> fields = new ArrayList<>();
            Collections.addAll(fields, typeDeclaration.getFields());
            fields.removeIf(field -> !matchesFieldName(find, (org.eclipse.jdt.core.dom.FieldDeclaration) field));
            return fields;
        }
        if (find.method() != null) {
            return findByMethodInvocation(find, typeDeclaration);
        }
        return List.of();
    }

    private List<ASTNode> findByAnnotation(FindSpec find, TypeDeclaration typeDeclaration) {
        List<ASTNode> out = new ArrayList<>();
        if (find.target() == JavaElementKind.CLASS) {
            if (JdtAnnotationSupport.hasAnnotation(typeDeclaration.modifiers(), find.annotation())) {
                out.add(typeDeclaration);
            }
            return out;
        }
        if (find.target() == JavaElementKind.FIELD) {
            Collections.addAll(out, typeDeclaration.getFields());
            out.removeIf(field -> !JdtAnnotationSupport.hasAnnotation(
                    ((org.eclipse.jdt.core.dom.FieldDeclaration) field).modifiers(),
                    find.annotation()));
            return out;
        }
        for (MethodDeclaration method : typeDeclaration.getMethods()) {
            if (JdtAnnotationSupport.hasAnnotation(method.modifiers(), find.annotation())) {
                out.add(method);
            }
        }
        return out;
    }

    private List<ASTNode> findByMethodInvocation(FindSpec find, TypeDeclaration typeDeclaration) {
        List<ASTNode> out = new ArrayList<>();
        for (MethodDeclaration method : typeDeclaration.getMethods()) {
            if (method.getBody() == null) {
                continue;
            }
            method.getBody().accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodInvocation node) {
                    if (JdtMethodSupport.matchesMethod(node, find.method())) {
                        out.add(node);
                    }
                    return true;
                }
            });
        }
        return out;
    }

    private boolean matchesFieldName(FindSpec find, org.eclipse.jdt.core.dom.FieldDeclaration field) {
        if (find.name() == null) {
            return true;
        }
        for (Object fragmentObject : field.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
            if (find.name().equals(fragment.getName().getIdentifier())) {
                return true;
            }
        }
        return false;
    }
}
