package com.poseidon.javastatic.extract.jdt.source;

import com.poseidon.javastatic.extract.jdt.support.JdtAnnotationSupport;
import com.poseidon.javastatic.extract.jdt.external.EndpointIdentityOverride;
import com.poseidon.javastatic.extract.jdt.support.JdtMethodSupport;
import com.poseidon.javastatic.extract.jdt.support.JdtNodeSupport;
import com.poseidon.javastatic.extract.jdt.support.ValueSupport;
import com.poseidon.javastatic.extract.jdt.trace.JdtValueTracer;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import com.poseidon.javastatic.extract.source.SourceSpec;
import com.poseidon.javastatic.extract.source.TakeKind;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.ClassInstanceCreation;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.ITypeBinding;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;

import java.util.ArrayList;
import java.util.List;

public class JdtSourceEvaluator {

    private final JdtValueTracer valueTracer;

    public JdtSourceEvaluator(JdtValueTracer valueTracer) {
        this.valueTracer = valueTracer;
    }

    public List<String> evaluate(SourceSpec source, JdtEvalContext context) {
        if (source.take() == null || source.take().kind() == null) {
            return List.of();
        }
        // Resolve fluent chain relative to the call anchor when requested.
        ASTNode chainResolved = resolveChainAnchor(source, context.anchorNode());
        if (source.element() == JavaElementKind.ANNOTATION) {
            return annotationValues(source, context);
        }
        if (source.element() == JavaElementKind.ARGUMENT) {
            MethodInvocation invocation = asInvocation(chainResolved != null ? chainResolved : context.anchorNode());
            if (invocation != null) {
                return argumentValues(source, context, invocation);
            }
            return List.of();
        }
        if (source.element() == JavaElementKind.CALL) {
            MethodInvocation invocation = asInvocation(chainResolved != null ? chainResolved : context.anchorNode());
            if (invocation != null) {
                return callValues(source, invocation);
            }
            return List.of();
        }
        if (source.element() == JavaElementKind.METHOD) {
            return methodValues(source, context);
        }
        if (source.element() == JavaElementKind.CLASS) {
            return classValues(source, context.typeDeclaration());
        }
        if (source.element() == JavaElementKind.FIELD) {
            return fieldValues(source, context);
        }
        if (source.element() == JavaElementKind.PARAMETER) {
            return parameterValues(source, context.anchorNode());
        }
        if (source.element() == JavaElementKind.RETURN) {
            return returnValues(source, context);
        }
        if (source.element() == JavaElementKind.ASSIGNMENT && context.anchorNode() instanceof Assignment assignment) {
            return assignmentValues(source, context, assignment);
        }
        if (source.element() == JavaElementKind.NEW) {
            return newExpressionValues(source, context.anchorNode());
        }
        if (source.element() == JavaElementKind.LITERAL) {
            return List.of(source.literalValue());
        }
        return List.of();
    }

    private List<String> annotationValues(SourceSpec source, JdtEvalContext context) {
        List<Annotation> annotations =
                switch (source.on()) {
                    case CLASS -> JdtAnnotationSupport.annotations(context.typeDeclaration().modifiers());
                    case METHOD -> JdtAnnotationSupport.annotations(
                            methodAnnotations(context));
                    case FIELD -> context.anchorNode() instanceof FieldDeclaration field
                            ? JdtAnnotationSupport.annotations(field.modifiers())
                            : List.of();
                    case PARAMETER -> parameterAnnotations(context);
                    default -> List.of();
                };
        List<String> out = new ArrayList<>();
        for (Annotation annotation : annotations) {
            if (!JdtAnnotationSupport.matchesAnnotation(annotation, source.annotation())) {
                continue;
            }
            if (source.take().kind() == TakeKind.NAME) {
                out.add(JdtAnnotationSupport.simpleAnnotationName(annotation));
            } else if (source.take().kind() == TakeKind.ATTRIBUTE) {
                out.addAll(JdtAnnotationSupport.readAnnotationAttributes(annotation, source.take().attributes()));
            } else if (source.take().kind() == TakeKind.RAW) {
                out.add(annotation.toString());
            }
        }
        return ValueSupport.dedupe(out);
    }

    /**
     * Navigate a fluent MethodInvocation chain from the find anchor.
     *
     * <pre>
     * client.post().uri("/x").body(...)
     *        ^post   ^uri     ^body
     * prev of uri = post; next of post = uri
     * </pre>
     */
    private ASTNode resolveChainAnchor(SourceSpec source, ASTNode anchor) {
        if (source == null) {
            return null;
        }
        Integer offset = source.chainOffset();
        String callName = source.chainCallName();
        if ((offset == null || offset == 0) && (callName == null || callName.isBlank())) {
            return null;
        }
        MethodInvocation current = asInvocation(anchor);
        if (current == null) {
            return null;
        }
        if (callName != null && !callName.isBlank()) {
            int direction = offset != null && offset < 0 ? -1 : 1;
            int maxHops = 32;
            MethodInvocation cursor = direction > 0 ? nextInChain(current) : prevInChain(current);
            while (cursor != null && maxHops-- > 0) {
                if (chainNameMatches(callName, cursor.getName().getIdentifier())) {
                    return cursor;
                }
                cursor = direction > 0 ? nextInChain(cursor) : prevInChain(cursor);
            }
            if (chainNameMatches(callName, current.getName().getIdentifier())) {
                return current;
            }
            return null;
        }
        int steps = Math.abs(offset);
        MethodInvocation cursor = current;
        for (int i = 0; i < steps; i++) {
            cursor = offset > 0 ? nextInChain(cursor) : prevInChain(cursor);
            if (cursor == null) {
                return null;
            }
        }
        return cursor;
    }

    private static MethodInvocation asInvocation(ASTNode node) {
        if (node instanceof MethodInvocation inv) {
            return inv;
        }
        return null;
    }

    private static boolean chainNameMatches(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        if (expected.equals(actual) || "*".equals(expected)) {
            return true;
        }
        try {
            return java.util.regex.Pattern.compile(expected).matcher(actual).find();
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** Receiver side: foo().bar() → prev of bar is foo(). */
    private static MethodInvocation prevInChain(MethodInvocation invocation) {
        Expression expr = invocation.getExpression();
        while (expr instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression parens) {
            expr = parens.getExpression();
        }
        if (expr instanceof MethodInvocation prev) {
            return prev;
        }
        return null;
    }

    /** Outer call: foo().bar() → next of foo is bar(). */
    private static MethodInvocation nextInChain(MethodInvocation invocation) {
        ASTNode parent = invocation.getParent();
        while (parent instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression) {
            parent = parent.getParent();
        }
        if (parent instanceof MethodInvocation outer) {
            Expression expr = outer.getExpression();
            while (expr instanceof org.eclipse.jdt.core.dom.ParenthesizedExpression parens) {
                expr = parens.getExpression();
            }
            if (expr == invocation) {
                return outer;
            }
        }
        return null;
    }

    private List<String> argumentValues(SourceSpec source, JdtEvalContext context, MethodInvocation invocation) {
        int index = source.argumentIndex() != null ? source.argumentIndex() : -1;
        if (index < 0 || index >= invocation.arguments().size()) {
            return List.of();
        }
        Expression expression = (Expression) invocation.arguments().get(index);
        return switch (source.take().kind()) {
            case RAW -> List.of(expression.toString());
            case VALUE -> valueTracer.trace(
                    expression, context.typeDeclaration(), JdtNodeSupport.enclosingMethod(invocation));
            case NAME -> expression instanceof SimpleName sn ? List.of(sn.getIdentifier()) : List.of(expression.toString());
            case TYPE -> List.of(JdtNodeSupport.typeName(expression.resolveTypeBinding()));
            default -> List.of();
        };
    }

    private List<String> callValues(SourceSpec source, MethodInvocation invocation) {
        return switch (source.take().kind()) {
            case NAME -> List.of(invocation.getName().getIdentifier());
            case OWNER -> List.of(JdtMethodSupport.invocationOwnerType(invocation));
            case RAW -> List.of(invocation.toString());
            case TYPE -> List.of(JdtNodeSupport.typeName(invocation.resolveTypeBinding()));
            default -> List.of();
        };
    }

    private List<String> methodValues(SourceSpec source, JdtEvalContext context) {
        ASTNode anchor = context.anchorNode();
        if (source.take().kind() == TakeKind.NAME) {
            if (anchor instanceof MethodInvocation invocation) {
                return List.of(invocation.getName().getIdentifier());
            }
            if (anchor instanceof MethodDeclaration declaration) {
                return List.of(declaration.getName().getIdentifier());
            }
        }
        if (source.take().kind() == TakeKind.RAW) {
            return List.of(anchor.toString());
        }
        if (source.take().kind() == TakeKind.SIGNATURE && anchor instanceof MethodDeclaration declaration) {
            return List.of(methodSignature(declaration));
        }
        if (source.take().kind() == TakeKind.TYPE && anchor instanceof MethodDeclaration declaration) {
            return List.of(declaration.getReturnType2() != null ? declaration.getReturnType2().toString() : "void");
        }
        if (source.take().kind() == TakeKind.VALUE) {
            MethodDeclaration declaration = EndpointIdentityOverride.enclosingMethod(anchor);
            if (declaration == null || context.identityDict().isEmpty()) {
                return List.of();
            }
            String baseKey = EndpointIdentityOverride.methodKey(
                    EndpointIdentityOverride.fqcnOf(context.typeDeclaration()),
                    declaration.getName().getIdentifier(),
                    0);
            String value = context.identityDict().get(baseKey);
            if (value == null || value.isBlank()) {
                return List.of();
            }
            if (!(anchor instanceof MethodDeclaration)) {
                return List.of(value.trim());
            }
            List<String> values = new ArrayList<>();
            values.add(value.trim());
            for (int index = 1; ; index++) {
                String indexed = context.identityDict().get(baseKey + "." + index);
                if (indexed == null || indexed.isBlank()) {
                    break;
                }
                values.add(indexed.trim());
            }
            return List.copyOf(values);
        }
        return List.of();
    }

    private List<String> classValues(SourceSpec source, TypeDeclaration typeDeclaration) {
        return switch (source.take().kind()) {
            case NAME -> List.of(typeDeclaration.getName().getIdentifier());
            case RAW -> List.of(typeDeclaration.toString());
            case TYPE -> {
                ITypeBinding binding = typeDeclaration.resolveBinding();
                yield List.of(binding != null
                        ? JdtNodeSupport.typeName(binding)
                        : typeDeclaration.getName().getIdentifier());
            }
            default -> List.of();
        };
    }

    private List<String> fieldValues(SourceSpec source, JdtEvalContext context) {
        List<String> out = new ArrayList<>();
        if (context.anchorNode() instanceof FieldDeclaration field) {
            collectFieldValues(source, context.typeDeclaration(), out, field);
            return ValueSupport.dedupe(out);
        }
        for (FieldDeclaration field : context.typeDeclaration().getFields()) {
            collectFieldValues(source, context.typeDeclaration(), out, field);
        }
        return ValueSupport.dedupe(out);
    }

    private void collectFieldValues(
            SourceSpec source,
            TypeDeclaration typeDeclaration,
            List<String> out,
            FieldDeclaration field) {
        for (Object fragmentObject : field.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
            if (source.name() != null && !source.name().equals(fragment.getName().getIdentifier())) {
                continue;
            }
            switch (source.take().kind()) {
                case NAME -> out.add(fragment.getName().getIdentifier());
                case TYPE -> out.add(field.getType().toString());
                case RAW -> out.add(fragment.toString());
                case VALUE -> out.addAll(valueTracer.traceField(field, fragment, typeDeclaration));
                default -> {
                }
            }
        }
    }

    private List<String> parameterValues(SourceSpec source, ASTNode anchor) {
        MethodDeclaration method = JdtNodeSupport.enclosingMethod(anchor);
        if (method == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object paramObject : method.parameters()) {
            SingleVariableDeclaration parameter = (SingleVariableDeclaration) paramObject;
            if (source.name() != null && !source.name().equals(parameter.getName().getIdentifier())) {
                continue;
            }
            switch (source.take().kind()) {
                case NAME -> out.add(parameter.getName().getIdentifier());
                case TYPE -> out.add(parameter.getType().toString());
                case RAW -> out.add(parameter.toString());
                case VALUE -> {
                    // Parameters do not have a source value by themselves. Trace rules can still
                    // read their name/type/annotations and build an external lookup.
                }
                default -> {
                }
            }
        }
        return ValueSupport.dedupe(out);
    }

    private List<String> assignmentValues(SourceSpec source, JdtEvalContext context, Assignment assignment) {
        return switch (source.take().kind()) {
            case VALUE -> valueTracer.trace(assignment.getRightHandSide(), context.typeDeclaration(), JdtNodeSupport.enclosingMethod(assignment));
            case RAW -> List.of(assignment.toString());
            case NAME -> List.of(assignment.getLeftHandSide().toString());
            case TYPE -> List.of(JdtNodeSupport.typeName(assignment.getRightHandSide().resolveTypeBinding()));
            default -> List.of();
        };
    }

    private List<Annotation> methodAnnotations(JdtEvalContext context) {
        MethodDeclaration method = context.anchorNode() instanceof MethodDeclaration declaration
                ? declaration
                : JdtNodeSupport.enclosingMethod(context.anchorNode());
        return method != null ? JdtAnnotationSupport.annotations(method.modifiers()) : List.of();
    }

    private List<Annotation> parameterAnnotations(JdtEvalContext context) {
        if (context.anchorNode() instanceof SingleVariableDeclaration parameter) {
            return JdtAnnotationSupport.annotations(parameter.modifiers());
        }
        MethodDeclaration method = context.anchorNode() instanceof MethodDeclaration declaration
                ? declaration
                : JdtNodeSupport.enclosingMethod(context.anchorNode());
        if (method == null) {
            return List.of();
        }
        List<Annotation> out = new ArrayList<>();
        for (Object parameterObject : method.parameters()) {
            SingleVariableDeclaration parameter = (SingleVariableDeclaration) parameterObject;
            out.addAll(JdtAnnotationSupport.annotations(parameter.modifiers()));
        }
        return out;
    }

    private String methodSignature(MethodDeclaration declaration) {
        List<String> parameterTypes = new ArrayList<>();
        for (Object parameterObject : declaration.parameters()) {
            SingleVariableDeclaration parameter = (SingleVariableDeclaration) parameterObject;
            parameterTypes.add(parameter.getType().toString());
        }
        return declaration.getName().getIdentifier() + "(" + String.join(",", parameterTypes) + ")";
    }

    private List<String> returnValues(SourceSpec source, JdtEvalContext context) {
        MethodDeclaration method = JdtNodeSupport.enclosingMethod(context.anchorNode());
        if (method == null || method.getBody() == null) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        method.getBody().accept(new ASTVisitor() {
            @Override
            public boolean visit(ReturnStatement node) {
                if (source.take().kind() == TakeKind.RAW) {
                    out.add(node.getExpression() != null ? node.getExpression().toString() : "");
                } else if (source.take().kind() == TakeKind.VALUE) {
                    out.addAll(valueTracer.trace(node.getExpression(), context.typeDeclaration(), method));
                } else if (source.take().kind() == TakeKind.TYPE && node.getExpression() != null) {
                    out.add(JdtNodeSupport.typeName(node.getExpression().resolveTypeBinding()));
                }
                return false;
            }
        });
        return ValueSupport.dedupe(out);
    }

    private List<String> newExpressionValues(SourceSpec source, ASTNode anchor) {
        List<String> out = new ArrayList<>();
        anchor.getRoot().accept(new ASTVisitor() {
            @Override
            public boolean visit(ClassInstanceCreation node) {
                String type = node.getType().toString();
                if (source.name() == null || source.name().equals(type) || type.endsWith("." + source.name())) {
                    if (source.take().kind() == TakeKind.TYPE || source.take().kind() == TakeKind.NAME) {
                        out.add(type);
                    } else if (source.take().kind() == TakeKind.RAW) {
                        out.add(node.toString());
                    }
                }
                return true;
            }
        });
        return ValueSupport.dedupe(out);
    }
}
