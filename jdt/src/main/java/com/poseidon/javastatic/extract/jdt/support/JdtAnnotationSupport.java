package com.poseidon.javastatic.extract.jdt.support;

import com.poseidon.javastatic.extract.source.AnnotationSelector;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.ArrayInitializer;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.MemberValuePair;
import org.eclipse.jdt.core.dom.Name;
import org.eclipse.jdt.core.dom.NormalAnnotation;
import org.eclipse.jdt.core.dom.SingleMemberAnnotation;
import org.eclipse.jdt.core.dom.StringLiteral;

import java.util.ArrayList;
import java.util.List;

public final class JdtAnnotationSupport {

    private JdtAnnotationSupport() {}

    public static boolean hasAnnotation(List<?> modifiers, AnnotationSelector selector) {
        for (Annotation annotation : annotations(modifiers)) {
            if (matchesAnnotation(annotation, selector)) {
                return true;
            }
        }
        return false;
    }

    public static boolean matchesAnnotation(Annotation annotation, AnnotationSelector selector) {
        if (selector == null) {
            return true;
        }
        String simpleName = simpleAnnotationName(annotation);
        if (selector.names() != null && !selector.names().isEmpty() && selector.names().contains(simpleName)) {
            return true;
        }
        return selector.namePattern() != null && simpleName.matches(selector.namePattern());
    }

    public static List<Annotation> annotations(List<?> modifiers) {
        if (modifiers == null) {
            return List.of();
        }
        List<Annotation> out = new ArrayList<>();
        for (Object modifier : modifiers) {
            if (modifier instanceof Annotation annotation) {
                out.add(annotation);
            }
        }
        return out;
    }

    public static String simpleAnnotationName(Annotation annotation) {
        String name = annotation.getTypeName().toString();
        return JdtNodeSupport.simpleTypeName(name);
    }

    public static List<String> readAnnotationAttributes(Annotation annotation, List<String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return List.of();
        }
        for (String attribute : attributes) {
            List<String> values = readAnnotationAttribute(annotation, attribute);
            if (!values.isEmpty()) {
                return values;
            }
        }
        return List.of();
    }

    private static List<String> readAnnotationAttribute(Annotation annotation, String attribute) {
        if (annotation instanceof SingleMemberAnnotation singleMemberAnnotation) {
            if ("value".equals(attribute)) {
                return expressionValues(singleMemberAnnotation.getValue());
            }
            return List.of();
        }
        if (annotation instanceof NormalAnnotation normalAnnotation) {
            for (Object value : normalAnnotation.values()) {
                MemberValuePair pair = (MemberValuePair) value;
                if (attribute.equals(pair.getName().getIdentifier())) {
                    return expressionValues(pair.getValue());
                }
            }
        }
        return List.of();
    }

    private static List<String> expressionValues(Expression expression) {
        if (expression instanceof StringLiteral literal) {
            return List.of(literal.getLiteralValue());
        }
        if (expression instanceof ArrayInitializer arrayInitializer) {
            List<String> out = new ArrayList<>();
            for (Object item : arrayInitializer.expressions()) {
                if (item instanceof Expression itemExpression) {
                    out.addAll(expressionValues(itemExpression));
                }
            }
            return out;
        }
        if (expression instanceof Name name) {
            return List.of(name.toString());
        }
        return List.of(expression.toString());
    }
}
