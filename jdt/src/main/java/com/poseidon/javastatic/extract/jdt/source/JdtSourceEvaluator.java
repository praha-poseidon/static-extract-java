package com.poseidon.javastatic.extract.jdt.source;

import com.poseidon.javastatic.extract.jdt.support.JdtAnnotationSupport;
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
        if (source.element() == JavaElementKind.ANNOTATION) {
            return annotationValues(source, context);
        }
        if (source.element() == JavaElementKind.ARGUMENT && context.anchorNode() instanceof MethodInvocation invocation) {
            return argumentValues(source, context, invocation);
        }
        if (source.element() == JavaElementKind.CALL && context.anchorNode() instanceof MethodInvocation invocation) {
            return callValues(source, invocation);
        }
        if (source.element() == JavaElementKind.METHOD) {
            return methodValues(source, context.anchorNode());
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

    private List<String> methodValues(SourceSpec source, ASTNode anchor) {
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
