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
        if (!matchesEnclosingClass(find, typeDeclaration)) {
            return List.of();
        }
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
        // find method (declarations on the type, including interface methods with no body)
        if (find.target() == JavaElementKind.METHOD && find.method() == null && find.annotation() == null) {
            return findMethodDeclarations(find, typeDeclaration);
        }
        // find call Owner.method / find method with method selector → invocations in bodies
        if (find.method() != null) {
            return findByMethodInvocation(find, typeDeclaration);
        }
        return List.of();
    }

    /**
     * All method declarations on the type (class or interface). Optional {@code find name} filters simple name.
     */
    private List<ASTNode> findMethodDeclarations(FindSpec find, TypeDeclaration typeDeclaration) {
        List<ASTNode> out = new ArrayList<>();
        for (MethodDeclaration method : typeDeclaration.getMethods()) {
            if (!matchesMethodDeclarationName(find.name(), typeDeclaration, method)) {
                continue;
            }
            out.add(method);
        }
        return out;
    }

    private boolean matchesMethodDeclarationName(
            String selector, TypeDeclaration typeDeclaration, MethodDeclaration method) {
        if (selector == null || selector.isBlank() || "*".equals(selector.trim())) {
            return true;
        }
        String simpleType = typeDeclaration.getName().getIdentifier();
        String qualifiedType = typeDeclaration.resolveBinding() != null
                ? typeDeclaration.resolveBinding().getQualifiedName()
                : simpleType;
        String raw = selector.replace(" ", "").trim();
        List<String> specs = new ArrayList<>();
        int ownerList = raw.indexOf(".[");
        if (ownerList > 0 && raw.endsWith("]")) {
            String owner = raw.substring(0, ownerList);
            for (String name : raw.substring(ownerList + 2, raw.length() - 1).split(",")) {
                specs.add(owner + "." + name);
            }
        } else {
            String body = raw.startsWith("[") && raw.endsWith("]")
                    ? raw.substring(1, raw.length() - 1)
                    : raw;
            Collections.addAll(specs, body.split(","));
        }
        for (String spec : specs) {
            int dot = spec.lastIndexOf('.');
            String expectedName = dot >= 0 ? spec.substring(dot + 1) : spec;
            String expectedOwner = dot >= 0 ? spec.substring(0, dot) : "";
            if (!"*".equals(expectedName)
                    && !expectedName.equals(method.getName().getIdentifier())) {
                continue;
            }
            if (expectedOwner.isBlank()
                    || "*".equals(expectedOwner)
                    || expectedOwner.equals(simpleType)
                    || expectedOwner.equals(qualifiedType)
                    || qualifiedType.endsWith("." + expectedOwner)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Narrow find to a specific type: class name and/or annotation on the class
     * (e.g. only {@code UserController}, or only {@code @RestController}).
     */
    private boolean matchesEnclosingClass(FindSpec find, TypeDeclaration typeDeclaration) {
        if (find == null || typeDeclaration == null) {
            return true;
        }
        if (find.className() != null && !find.className().isBlank()) {
            String simple = typeDeclaration.getName().getIdentifier();
            String fqn = typeDeclaration.resolveBinding() != null
                    ? typeDeclaration.resolveBinding().getQualifiedName()
                    : simple;
            String expected = find.className().trim();
            if (find.classNameRegex()) {
                if (!regexMatches(expected, simple) && !regexMatches(expected, fqn)) {
                    return false;
                }
            } else if (!simple.equals(expected)
                    && !simple.matches(globToRegex(expected))
                    && !fqn.equals(expected)
                    && !fqn.endsWith("." + expected)) {
                return false;
            }
        }
        if (find.classAnnotation() != null) {
            return JdtAnnotationSupport.hasAnnotation(typeDeclaration.modifiers(), find.classAnnotation());
        }
        return true;
    }

    private static boolean regexMatches(String pattern, String value) {
        if (pattern == null || value == null) {
            return false;
        }
        try {
            return java.util.regex.Pattern.compile(pattern).matcher(value).find();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static String globToRegex(String glob) {
        StringBuilder sb = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                sb.append(".*");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                sb.append('\\').append(c);
            } else {
                sb.append(c);
            }
        }
        sb.append('$');
        return sb.toString();
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
