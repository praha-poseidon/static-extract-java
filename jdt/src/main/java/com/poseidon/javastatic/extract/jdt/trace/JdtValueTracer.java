package com.poseidon.javastatic.extract.jdt.trace;

import com.poseidon.javastatic.extract.jdt.build.JdtBuildEvaluator;
import com.poseidon.javastatic.extract.jdt.source.JdtEvalContext;
import com.poseidon.javastatic.extract.jdt.source.JdtLetEvaluator;
import com.poseidon.javastatic.extract.jdt.source.JdtSourceEvaluator;
import com.poseidon.javastatic.extract.jdt.support.JdtAnnotationSupport;
import com.poseidon.javastatic.extract.jdt.support.JdtMethodSupport;
import com.poseidon.javastatic.extract.jdt.support.JdtNodeSupport;
import com.poseidon.javastatic.extract.jdt.support.ValueSupport;
import com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceContext;
import com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceResolver;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import com.poseidon.javastatic.extract.trace.ExternalValueEntryRule;
import com.poseidon.javastatic.extract.trace.TraceTargetKind;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Expression;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.InfixExpression;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.ParenthesizedExpression;
import org.eclipse.jdt.core.dom.QualifiedName;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.StringLiteral;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.VariableDeclarationStatement;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class JdtValueTracer {

    private final JdtTraceOptions options;
    private final JdtLetEvaluator letEvaluator;
    private final JdtBuildEvaluator buildEvaluator;

    public JdtValueTracer() {
        this(JdtTraceOptions.empty());
    }

    public JdtValueTracer(JdtTraceOptions options) {
        this.options = options != null ? options : JdtTraceOptions.empty();
        this.letEvaluator = new JdtLetEvaluator(new JdtSourceEvaluator(this));
        this.buildEvaluator = new JdtBuildEvaluator();
    }

    public List<String> trace(Expression expression, TypeDeclaration typeDeclaration, MethodDeclaration method) {
        return traceValue(expression, typeDeclaration, method, new LinkedHashSet<>());
    }

    public List<String> traceField(
            FieldDeclaration field,
            VariableDeclarationFragment fragment,
            TypeDeclaration typeDeclaration) {
        List<String> values = traceValue(fragment.getInitializer(), typeDeclaration, null, new LinkedHashSet<>());
        if (!values.isEmpty()) {
            return values;
        }
        values = resolveFieldAssignments(fragment.getName().getIdentifier(), typeDeclaration, new LinkedHashSet<>());
        if (!values.isEmpty()) {
            return values;
        }
        return resolveExternalField(field, fragment, typeDeclaration, new LinkedHashSet<>());
    }

    private List<String> traceValue(
            Expression expression,
            TypeDeclaration typeDeclaration,
            MethodDeclaration method,
            Set<ASTNode> visited) {
        if (expression == null || visited.contains(expression)) {
            return List.of();
        }
        visited.add(expression);

        if (expression instanceof StringLiteral literal) {
            return List.of(literal.getLiteralValue());
        }
        if (expression instanceof ParenthesizedExpression parenthesized) {
            return traceValue(parenthesized.getExpression(), typeDeclaration, method, visited);
        }
        if (expression instanceof InfixExpression infix && infix.getOperator() == InfixExpression.Operator.PLUS) {
            return traceConcat(infix, typeDeclaration, method, visited);
        }
        if (expression instanceof SimpleName simpleName) {
            Expression resolved = resolveSimpleName(simpleName, typeDeclaration, method);
            if (resolved != null) {
                return traceValue(resolved, typeDeclaration, method, visited);
            }
            List<String> external = resolveExternalField(simpleName, typeDeclaration, visited);
            if (!external.isEmpty()) {
                return external;
            }
            external = resolveExternalParameter(simpleName, typeDeclaration, method);
            if (!external.isEmpty()) {
                return external;
            }
            return List.of("{" + simpleName.getIdentifier() + "}");
        }
        if (expression instanceof QualifiedName qualifiedName) {
            return List.of(qualifiedName.toString());
        }
        if (expression instanceof MethodInvocation invocation) {
            List<String> external = resolveExternalCall(invocation, typeDeclaration, method, visited);
            if (!external.isEmpty()) {
                return external;
            }
        }
        return List.of(expression.toString());
    }

    private List<String> traceConcat(
            InfixExpression infix,
            TypeDeclaration typeDeclaration,
            MethodDeclaration method,
            Set<ASTNode> visited) {
        List<Expression> operands = new ArrayList<>();
        operands.add(infix.getLeftOperand());
        operands.add(infix.getRightOperand());
        for (Object operand : infix.extendedOperands()) {
            operands.add((Expression) operand);
        }
        List<String> acc = new ArrayList<>();
        acc.add("");
        for (Expression operand : operands) {
            List<String> values = traceValue(operand, typeDeclaration, method, visited);
            List<String> next = new ArrayList<>();
            for (String prefix : acc) {
                for (String value : values) {
                    next.add(prefix + value);
                }
            }
            acc = next;
        }
        return acc;
    }

    private Expression resolveSimpleName(
            SimpleName name,
            TypeDeclaration typeDeclaration,
            MethodDeclaration method) {
        if (method != null && method.getBody() != null) {
            LocalVariableFinder finder = new LocalVariableFinder(name);
            method.getBody().accept(finder);
            if (finder.initializer != null) {
                return finder.initializer;
            }
        }
        for (FieldDeclaration field : typeDeclaration.getFields()) {
            for (Object fragmentObject : field.fragments()) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
                if (name.getIdentifier().equals(fragment.getName().getIdentifier())) {
                    return fragment.getInitializer();
                }
            }
        }
        return null;
    }

    private List<String> resolveExternalField(
            SimpleName name,
            TypeDeclaration typeDeclaration,
            Set<ASTNode> visited) {
        for (FieldDeclaration field : typeDeclaration.getFields()) {
            for (Object fragmentObject : field.fragments()) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
                if (name.getIdentifier().equals(fragment.getName().getIdentifier())) {
                    return resolveExternalField(field, fragment, typeDeclaration, visited);
                }
            }
        }
        return List.of();
    }

    private List<String> resolveExternalField(
            FieldDeclaration field,
            VariableDeclarationFragment fragment,
            TypeDeclaration typeDeclaration,
            Set<ASTNode> visited) {
        List<String> assignedValues = resolveFieldAssignments(fragment.getName().getIdentifier(), typeDeclaration, visited);
        if (!assignedValues.isEmpty()) {
            return assignedValues;
        }

        JdtTraceContext context = traceContext(typeDeclaration, null, visited);
        List<String> custom = new ArrayList<>();
        for (JdtTraceResolver resolver : options.traceResolvers()) {
            custom.addAll(resolver.resolveField(field, fragment, typeDeclaration, context));
        }
        if (!custom.isEmpty()) {
            return ValueSupport.dedupe(custom);
        }

        List<String> out = new ArrayList<>();
        for (ExternalValueEntryRule rule : options.externalEntries()) {
            if (rule.target() != TraceTargetKind.FIELD) {
                continue;
            }
            if (!matchesFieldRule(rule, field, typeDeclaration)) {
                continue;
            }
            out.addAll(resolveExternalRule(rule, new JdtEvalContext(null, typeDeclaration, field)));
        }
        return ValueSupport.dedupe(out);
    }

    private List<String> resolveFieldAssignments(
            String fieldName,
            TypeDeclaration typeDeclaration,
            Set<ASTNode> visited) {
        List<String> out = new ArrayList<>();
        typeDeclaration.accept(new ASTVisitor() {
            @Override
            public boolean visit(Assignment node) {
                if (!isFieldAssignment(node, fieldName)) {
                    return true;
                }
                MethodDeclaration method = JdtNodeSupport.enclosingMethod(node);
                List<String> values = traceValue(
                        node.getRightHandSide(),
                        typeDeclaration,
                        method,
                        new LinkedHashSet<>(visited));
                if (values.isEmpty()) {
                    values = resolveExternalAssignment(node, typeDeclaration, fieldName);
                }
                out.addAll(values);
                return true;
            }
        });
        return ValueSupport.dedupe(out);
    }

    private List<String> resolveExternalAssignment(
            Assignment assignment,
            TypeDeclaration typeDeclaration,
            String fieldName) {
        List<String> out = new ArrayList<>();
        for (ExternalValueEntryRule rule : options.externalEntries()) {
            if (rule.target() != TraceTargetKind.ASSIGNMENT) {
                continue;
            }
            if (rule.match().assignmentField() != null && !rule.match().assignmentField().equals(fieldName)) {
                continue;
            }
            out.addAll(resolveExternalRule(rule, new JdtEvalContext(null, typeDeclaration, assignment)));
        }
        return ValueSupport.dedupe(out);
    }

    private boolean isFieldAssignment(Assignment assignment, String fieldName) {
        Expression left = assignment.getLeftHandSide();
        if (left instanceof SimpleName simpleName) {
            return fieldName.equals(simpleName.getIdentifier());
        }
        if (left instanceof FieldAccess fieldAccess) {
            return fieldName.equals(fieldAccess.getName().getIdentifier());
        }
        if (left instanceof QualifiedName qualifiedName) {
            return fieldName.equals(qualifiedName.getName().getIdentifier());
        }
        return false;
    }

    private List<String> resolveExternalCall(
            MethodInvocation invocation,
            TypeDeclaration typeDeclaration,
            MethodDeclaration method,
            Set<ASTNode> visited) {
        JdtTraceContext context = traceContext(typeDeclaration, method, visited);
        List<String> custom = new ArrayList<>();
        for (JdtTraceResolver resolver : options.traceResolvers()) {
            custom.addAll(resolver.resolveMethodCall(invocation, typeDeclaration, method, context));
        }
        if (!custom.isEmpty()) {
            return ValueSupport.dedupe(custom);
        }

        List<String> out = new ArrayList<>();
        for (ExternalValueEntryRule rule : options.externalEntries()) {
            if (rule.target() != TraceTargetKind.METHOD_CALL) {
                continue;
            }
            if (!matchesCallRule(rule, invocation)) {
                continue;
            }
            out.addAll(resolveExternalRule(rule, new JdtEvalContext(null, typeDeclaration, invocation)));
        }
        return ValueSupport.dedupe(out);
    }

    private List<String> resolveExternalParameter(
            SimpleName name,
            TypeDeclaration typeDeclaration,
            MethodDeclaration method) {
        if (method == null) {
            return List.of();
        }
        for (Object parameterObject : method.parameters()) {
            SingleVariableDeclaration parameter = (SingleVariableDeclaration) parameterObject;
            if (name.getIdentifier().equals(parameter.getName().getIdentifier())) {
                return resolveExternalParameter(parameter, typeDeclaration);
            }
        }
        return List.of();
    }

    private List<String> resolveExternalParameter(
            SingleVariableDeclaration parameter,
            TypeDeclaration typeDeclaration) {
        List<String> out = new ArrayList<>();
        for (ExternalValueEntryRule rule : options.externalEntries()) {
            if (rule.target() != TraceTargetKind.PARAMETER) {
                continue;
            }
            if (!matchesParameterRule(rule, parameter)) {
                continue;
            }
            out.addAll(resolveExternalRule(rule, new JdtEvalContext(null, typeDeclaration, parameter)));
        }
        return ValueSupport.dedupe(out);
    }

    private List<String> resolveExternalMethod(
            MethodDeclaration method,
            TypeDeclaration typeDeclaration) {
        List<String> out = new ArrayList<>();
        for (ExternalValueEntryRule rule : options.externalEntries()) {
            if (rule.target() != TraceTargetKind.METHOD) {
                continue;
            }
            if (!matchesMethodRule(rule, method)) {
                continue;
            }
            out.addAll(resolveExternalRule(rule, new JdtEvalContext(null, typeDeclaration, method)));
        }
        return ValueSupport.dedupe(out);
    }

    private boolean matchesFieldRule(
            ExternalValueEntryRule rule,
            FieldDeclaration field,
            TypeDeclaration typeDeclaration) {
        if (rule.match().annotation() != null) {
            boolean annotationMatched = switch (rule.match().annotation().on()) {
                case FIELD -> JdtAnnotationSupport.hasAnnotation(field.modifiers(), rule.match().annotation());
                case CLASS -> JdtAnnotationSupport.hasAnnotation(typeDeclaration.modifiers(), rule.match().annotation());
                default -> false;
            };
            if (!annotationMatched) {
                return false;
            }
        }
        return matchesFieldName(rule, field) && matchesFieldType(rule, field);
    }

    private boolean matchesFieldName(ExternalValueEntryRule rule, FieldDeclaration field) {
        if (rule.match().fieldName() == null) {
            return true;
        }
        for (Object fragmentObject : field.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
            if (rule.match().fieldName().equals(fragment.getName().getIdentifier())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesFieldType(ExternalValueEntryRule rule, FieldDeclaration field) {
        return rule.match().fieldType() == null
                || rule.match().fieldType().equals(field.getType().toString())
                || field.getType().toString().endsWith("." + rule.match().fieldType());
    }

    private boolean matchesCallRule(ExternalValueEntryRule rule, MethodInvocation invocation) {
        if (rule.match().method() != null && !JdtMethodSupport.matchesMethod(invocation, rule.match().method())) {
            return false;
        }
        if (rule.match().callName() != null
                && !rule.match().callName().equals(invocation.getName().getIdentifier())) {
            return false;
        }
        if (rule.match().callOwner() == null) {
            return true;
        }
        String owner = JdtMethodSupport.invocationOwnerType(invocation);
        return owner != null
                && (rule.match().callOwner().equals(owner)
                || owner.endsWith("." + rule.match().callOwner())
                || JdtNodeSupport.simpleTypeName(owner).equals(rule.match().callOwner()));
    }

    private boolean matchesParameterRule(ExternalValueEntryRule rule, SingleVariableDeclaration parameter) {
        if (rule.match().annotation() != null
                && rule.match().annotation().on() == JavaElementKind.PARAMETER
                && !JdtAnnotationSupport.hasAnnotation(parameter.modifiers(), rule.match().annotation())) {
            return false;
        }
        if (rule.match().parameterName() != null
                && !rule.match().parameterName().equals(parameter.getName().getIdentifier())) {
            return false;
        }
        return rule.match().parameterType() == null
                || rule.match().parameterType().equals(parameter.getType().toString())
                || parameter.getType().toString().endsWith("." + rule.match().parameterType());
    }

    private boolean matchesMethodRule(ExternalValueEntryRule rule, MethodDeclaration method) {
        if (rule.match().annotation() != null
                && rule.match().annotation().on() == JavaElementKind.METHOD
                && !JdtAnnotationSupport.hasAnnotation(method.modifiers(), rule.match().annotation())) {
            return false;
        }
        return rule.match().methodName() == null
                || rule.match().methodName().equals(method.getName().getIdentifier());
    }

    private List<String> resolveExternalRule(ExternalValueEntryRule rule, JdtEvalContext evalContext) {
        List<String> out = new ArrayList<>();
        var values = letEvaluator.evaluate(rule.lets(), evalContext);
        for (var row : buildEvaluator.evaluate(rule.build(), values)) {
            String namespace = row.get("namespace");
            String key = row.get("lookup");
            String defaultValue = row.get("default");
            if (namespace == null || namespace.isBlank() || key == null || key.isBlank()) {
                continue;
            }
            out.addAll(resolvedOrLiteral(namespace, key, defaultValue));
        }
        return ValueSupport.dedupe(out);
    }

    private JdtTraceContext traceContext(
            TypeDeclaration typeDeclaration,
            MethodDeclaration method,
            Set<ASTNode> visited) {
        return new JdtTraceContext() {
            @Override
            public List<String> trace(Expression expression) {
                return traceValue(expression, typeDeclaration, method, new LinkedHashSet<>(visited));
            }

            @Override
            public List<String> resolveExternalValue(String namespace, String key) {
                return options.externalValueResolver().resolve(namespace, key);
            }
        };
    }

    private List<String> resolvedOrLiteral(String namespace, String key, String defaultValue) {
        List<String> resolved = options.externalValueResolver().resolve(namespace, key);
        if (!resolved.isEmpty()) {
            return resolved;
        }
        if (defaultValue != null) {
            return List.of(defaultValue);
        }
        return List.of("{" + key + "}");
    }

    private static final class LocalVariableFinder extends ASTVisitor {
        private final SimpleName use;
        private Expression initializer;

        private LocalVariableFinder(SimpleName use) {
            this.use = use;
        }

        @Override
        public boolean visit(VariableDeclarationStatement node) {
            if (initializer != null) {
                return false;
            }
            for (Object fragmentObject : node.fragments()) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObject;
                if (use.getIdentifier().equals(fragment.getName().getIdentifier())
                        && fragment.getStartPosition() < use.getStartPosition()) {
                    initializer = fragment.getInitializer();
                    return false;
                }
            }
            return true;
        }
    }
}
