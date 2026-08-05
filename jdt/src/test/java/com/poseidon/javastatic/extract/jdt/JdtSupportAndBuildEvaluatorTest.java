package com.poseidon.javastatic.extract.jdt;

import com.poseidon.javastatic.extract.build.BuildAction;
import com.poseidon.javastatic.extract.build.BuildActionKind;
import com.poseidon.javastatic.extract.build.BuildExpression;
import com.poseidon.javastatic.extract.build.BuildSpec;
import com.poseidon.javastatic.extract.build.NormalizeKind;
import com.poseidon.javastatic.extract.jdt.build.JdtBuildEvaluator;
import com.poseidon.javastatic.extract.jdt.source.JdtEvalContext;
import com.poseidon.javastatic.extract.jdt.source.JdtSourceEvaluator;
import com.poseidon.javastatic.extract.jdt.support.JdtAnnotationSupport;
import com.poseidon.javastatic.extract.jdt.support.JdtMethodSupport;
import com.poseidon.javastatic.extract.jdt.support.JdtNodeSupport;
import com.poseidon.javastatic.extract.jdt.support.ValueSupport;
import com.poseidon.javastatic.extract.jdt.trace.JdtValueTracer;
import com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver;
import com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceResolver;
import com.poseidon.javastatic.extract.source.AnnotationSelector;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import com.poseidon.javastatic.extract.source.MethodSelector;
import com.poseidon.javastatic.extract.source.SourceSpec;
import com.poseidon.javastatic.extract.source.TakeKind;
import com.poseidon.javastatic.extract.source.TakeSpec;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Annotation;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.MethodInvocation;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdtSupportAndBuildEvaluatorTest {

    @Test
    void evaluatesBuildActionsAndValueHelpers() {
        Map<String, BuildExpression> fields = new LinkedHashMap<>();
        fields.put(
                "path",
                new BuildExpression(
                        "rawPath",
                        null,
                        null,
                        List.of(
                                action(BuildActionKind.REPLACE, "old", null, "new", null, null),
                                action(BuildActionKind.REGEX, "https?://[^/]+(/.*)", 1, null, null, null),
                                action(BuildActionKind.NORMALIZE, null, null, null, NormalizeKind.PATH_VARIABLE, null))));
        fields.put(
                "placeholder",
                new BuildExpression(
                        "placeholder",
                        null,
                        null,
                        List.of(action(BuildActionKind.NORMALIZE, null, null, null, NormalizeKind.PLACEHOLDER_LOOKUP, null))));
        fields.put(
                "defaultValue",
                new BuildExpression(
                        "placeholder",
                        null,
                        null,
                        List.of(action(BuildActionKind.NORMALIZE, null, null, null, NormalizeKind.PLACEHOLDER_DEFAULT, null))));
        fields.put(
                "kebab",
                new BuildExpression(
                        "camel",
                        null,
                        null,
                        List.of(action(BuildActionKind.NORMALIZE, null, null, null, NormalizeKind.KEBAB, null))));
        fields.put(
                "mapped",
                new BuildExpression(
                        "method",
                        null,
                        null,
                        List.of(action(BuildActionKind.MAP, null, null, null, null, Map.of("get", "GET")))));
        fields.put("upper", new BuildExpression("method", null, null, List.of(action(BuildActionKind.UPPER, null, null, null, null, null))));
        fields.put("lower", new BuildExpression("constant", null, null, List.of(action(BuildActionKind.LOWER, null, null, null, null, null))));
        fields.put("concat", new BuildExpression(null, null, List.of("prefix", "/", "suffix"), List.of()));

        List<Map<String, String>> rows = new JdtBuildEvaluator()
                .evaluate(
                        new BuildSpec(fields),
                        Map.of(
                                "rawPath", List.of("http://host/old/users/{id}/"),
                                "placeholder", List.of("${service.url:http://fallback}"),
                                "camel", List.of("baseUrl"),
                                "method", List.of("get"),
                                "constant", List.of("POST"),
                                "prefix", List.of("/api"),
                                "suffix", List.of("users")));

        Map<String, String> row = rows.get(0);
        assertEquals("/new/users/{param}/", row.get("path"));
        assertEquals("service.url", row.get("placeholder"));
        assertEquals("http://fallback", row.get("defaultValue"));
        assertEquals("base-url", row.get("kebab"));
        assertEquals("GET", row.get("mapped"));
        assertEquals("GET", row.get("upper"));
        assertEquals("post", row.get("lower"));
        assertEquals("/api/users", row.get("concat"));
        assertEquals(List.of("a", "b"), ValueSupport.dedupe(List.of("a", "a", "b")));
        assertEquals(List.of("x"), ValueSupport.applyMapping(List.of("x"), Map.of()));
    }

    @Test
    void readsAnnotationsAndMatchesMethods() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @interface RequestMapping { String[] path() default {}; }
                        @interface Named { String value(); }
                        class Names { static final String USERS = "users"; }
                        class Router { void get(String path) {} }

                        @RequestMapping(path = {"/api", "/open"})
                        @Named(Names.USERS)
                        class Routes {
                            Router router;
                            void init() {
                                router.get("/users");
                            }
                        }
                        """);
        TypeDeclaration type = typeDeclaration(cu, "Routes");
        List<Annotation> annotations = JdtAnnotationSupport.annotations(type.modifiers());

        assertTrue(JdtAnnotationSupport.hasAnnotation(
                type.modifiers(), new AnnotationSelector(JavaElementKind.CLASS, List.of("RequestMapping"), null)));
        assertTrue(JdtAnnotationSupport.hasAnnotation(
                type.modifiers(), new AnnotationSelector(JavaElementKind.CLASS, List.of(), ".*Mapping")));
        assertFalse(JdtAnnotationSupport.hasAnnotation(
                type.modifiers(), new AnnotationSelector(JavaElementKind.CLASS, List.of("Missing"), null)));
        assertEquals("RequestMapping", JdtAnnotationSupport.simpleAnnotationName(annotations.get(0)));
        assertEquals(List.of("/api", "/open"), JdtAnnotationSupport.readAnnotationAttributes(annotations.get(0), List.of("path")));
        assertEquals(List.of("Names.USERS"), JdtAnnotationSupport.readAnnotationAttributes(annotations.get(1), List.of("value")));
        assertEquals(List.of(), JdtAnnotationSupport.readAnnotationAttributes(annotations.get(1), List.of("missing")));

        MethodInvocation invocation = firstInvocation(cu);
        assertTrue(JdtMethodSupport.matchesMethod(
                invocation, new MethodSelector("Router", null, List.of("get"), null)));
        assertTrue(JdtMethodSupport.matchesMethod(
                invocation, new MethodSelector(null, null, List.of(), "g.*")));
        assertFalse(JdtMethodSupport.matchesMethod(
                invocation, new MethodSelector("Router", null, List.of("post"), null)));
        assertEquals("Routes", JdtNodeSupport.simpleTypeName("com.example.Routes"));
        assertEquals("int", JdtNodeSupport.typeName(cu.getAST().resolveWellKnownType("int")));
    }

    @Test
    void coversExternalResolverAndTraceResolverDefaults() {
        MapExternalValueResolver empty = new MapExternalValueResolver(null);
        MapExternalValueResolver resolver = new MapExternalValueResolver(Map.of("config", Map.of("name", List.of("value"))));
        JdtTraceResolver traceResolver = new JdtTraceResolver() {};

        assertEquals(List.of(), empty.resolve("config", "name"));
        assertEquals(List.of(), resolver.resolve("missing", "name"));
        assertEquals(List.of("value"), resolver.resolve("config", "name"));
        assertEquals(List.of(), traceResolver.resolveField(null, null, null, null));
        assertEquals(List.of(), traceResolver.resolveMethodCall(null, null, null, null));
    }

    @Test
    void evaluatesAllCommonSourceShapesDirectly() {
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @interface Tag { String value(); }
                        @Tag("class-tag")
                        class SourceCases {
                            @Tag("field-tag")
                            private String base = "/api";

                            @Tag("method-tag")
                            String route(@Tag("param-tag") String id) {
                                String local = "users";
                                this.base = "/override";
                                new UserDto();
                                return base + "/" + local + "/" + id;
                            }
                        }
                        class UserDto {}
                        """);
        TypeDeclaration type = typeDeclaration(cu, "SourceCases");
        MethodDeclaration method = methodDeclaration(type, "route");
        FieldDeclaration field = type.getFields()[0];
        SingleVariableDeclaration parameter = (SingleVariableDeclaration) method.parameters().get(0);
        MethodInvocation noInvocation = null;
        Assignment assignment = firstAssignment(cu);
        ReturnStatement returnStatement = firstReturn(cu);
        JdtSourceEvaluator evaluator = new JdtSourceEvaluator(new JdtValueTracer());

        assertEquals(List.of("class-tag"), evaluator.evaluate(
                source(JavaElementKind.ANNOTATION, JavaElementKind.CLASS, null, ann(JavaElementKind.CLASS, "Tag"), TakeKind.ATTRIBUTE),
                new JdtEvalContext(cu, type, method)));
        assertEquals(List.of("method-tag"), evaluator.evaluate(
                source(JavaElementKind.ANNOTATION, JavaElementKind.METHOD, null, ann(JavaElementKind.METHOD, "Tag"), TakeKind.ATTRIBUTE),
                new JdtEvalContext(cu, type, returnStatement)));
        assertEquals(List.of("field-tag"), evaluator.evaluate(
                source(JavaElementKind.ANNOTATION, JavaElementKind.FIELD, null, ann(JavaElementKind.FIELD, "Tag"), TakeKind.ATTRIBUTE),
                new JdtEvalContext(cu, type, field)));
        assertEquals(List.of("param-tag"), evaluator.evaluate(
                source(JavaElementKind.ANNOTATION, JavaElementKind.PARAMETER, null, ann(JavaElementKind.PARAMETER, "Tag"), TakeKind.ATTRIBUTE),
                new JdtEvalContext(cu, type, parameter)));
        assertEquals(List.of("SourceCases"), evaluator.evaluate(
                source(JavaElementKind.CLASS, null, null, null, TakeKind.NAME),
                new JdtEvalContext(cu, type, method)));
        assertEquals(List.of("route(String)"), evaluator.evaluate(
                source(JavaElementKind.METHOD, null, null, null, TakeKind.SIGNATURE),
                new JdtEvalContext(cu, type, method)));
        assertEquals(List.of("String"), evaluator.evaluate(
                source(JavaElementKind.METHOD, null, null, null, TakeKind.TYPE),
                new JdtEvalContext(cu, type, method)));
        assertEquals(List.of("base"), evaluator.evaluate(
                source(JavaElementKind.FIELD, null, "base", null, TakeKind.NAME),
                new JdtEvalContext(cu, type, method)));
        assertEquals(List.of("String"), evaluator.evaluate(
                source(JavaElementKind.FIELD, null, "base", null, TakeKind.TYPE),
                new JdtEvalContext(cu, type, method)));
        assertEquals(List.of("id"), evaluator.evaluate(
                source(JavaElementKind.PARAMETER, null, "id", null, TakeKind.NAME),
                new JdtEvalContext(cu, type, returnStatement)));
        assertEquals(List.of("String"), evaluator.evaluate(
                source(JavaElementKind.PARAMETER, null, "id", null, TakeKind.TYPE),
                new JdtEvalContext(cu, type, returnStatement)));
        assertEquals(List.of("this.base=\"/override\""), evaluator.evaluate(
                source(JavaElementKind.ASSIGNMENT, null, null, null, TakeKind.RAW),
                new JdtEvalContext(cu, type, assignment)));
        assertEquals(List.of("this.base"), evaluator.evaluate(
                source(JavaElementKind.ASSIGNMENT, null, null, null, TakeKind.NAME),
                new JdtEvalContext(cu, type, assignment)));
        assertEquals(List.of("/override"), evaluator.evaluate(
                source(JavaElementKind.ASSIGNMENT, null, null, null, TakeKind.VALUE),
                new JdtEvalContext(cu, type, assignment)));
        assertEquals(List.of("/api/users/{id}"), evaluator.evaluate(
                source(JavaElementKind.RETURN, null, null, null, TakeKind.VALUE),
                new JdtEvalContext(cu, type, returnStatement)));
        assertEquals(List.of("UserDto"), evaluator.evaluate(
                source(JavaElementKind.NEW, null, "UserDto", null, TakeKind.TYPE),
                new JdtEvalContext(cu, type, method)));
        assertEquals(List.of(), evaluator.evaluate(
                source(JavaElementKind.CALL, null, null, null, TakeKind.NAME),
                new JdtEvalContext(cu, type, noInvocation)));
    }

    private BuildAction action(
            BuildActionKind kind,
            String pattern,
            Integer group,
            String replacement,
            NormalizeKind normalize,
            Map<String, String> mapping) {
        return new BuildAction(kind, pattern, group, replacement, normalize, mapping);
    }

    private SourceSpec source(
            JavaElementKind element,
            JavaElementKind on,
            String name,
            AnnotationSelector annotation,
            TakeKind take) {
        return new SourceSpec(element, on, name, null, annotation, null, null, new TakeSpec(take, List.of("value")));
    }

    private AnnotationSelector ann(JavaElementKind on, String name) {
        return new AnnotationSelector(on, List.of(name), null);
    }

    private MethodInvocation firstInvocation(CompilationUnit cu) {
        final MethodInvocation[] found = new MethodInvocation[1];
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(MethodInvocation node) {
                found[0] = node;
                return false;
            }
        });
        return found[0];
    }

    private TypeDeclaration typeDeclaration(CompilationUnit cu, String name) {
        final TypeDeclaration[] found = new TypeDeclaration[1];
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                if (name.equals(node.getName().getIdentifier())) {
                    found[0] = node;
                    return false;
                }
                return true;
            }
        });
        assertNotNull(found[0]);
        return found[0];
    }

    private MethodDeclaration methodDeclaration(TypeDeclaration type, String name) {
        for (MethodDeclaration method : type.getMethods()) {
            if (name.equals(method.getName().getIdentifier())) {
                return method;
            }
        }
        throw new IllegalArgumentException("Missing method: " + name);
    }

    private Assignment firstAssignment(CompilationUnit cu) {
        final Assignment[] found = new Assignment[1];
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(Assignment node) {
                found[0] = node;
                return false;
            }
        });
        assertNotNull(found[0]);
        return found[0];
    }

    private ReturnStatement firstReturn(CompilationUnit cu) {
        final ReturnStatement[] found = new ReturnStatement[1];
        cu.accept(new ASTVisitor() {
            @Override
            public boolean visit(ReturnStatement node) {
                found[0] = node;
                return false;
            }
        });
        assertNotNull(found[0]);
        return found[0];
    }

    private CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName("Test.java");
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setCompilerOptions(JavaCore.getOptions());
        return (CompilationUnit) parser.createAST(null);
    }
}
