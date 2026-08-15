package com.poseidon.javastatic.extract.jdt.external;

import com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver;
import com.poseidon.javastatic.extract.rule.EndpointSpec;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class EndpointIdentityOverrideTest {

    @Test
    void methodKeyIsFqcnMethodNoProject() {
        assertEquals(
                "com.example.CooperAssetConsumer.run()",
                EndpointIdentityOverride.methodKey("com.example.CooperAssetConsumer", "run", 0));
        assertEquals(
                "com.example.Foo.bar().1",
                EndpointIdentityOverride.methodKey("com.example.Foo", "bar", 1));
    }

    @Test
    void mqTopicHitFromFlatIdentity() {
        CompilationUnit cu = parse(
                """
                package com.example;
                class CooperAssetConsumer {
                  public void run() {}
                }
                """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        MethodDeclaration method = null;
        for (Object b : type.bodyDeclarations()) {
            if (b instanceof MethodDeclaration md && "run".equals(md.getName().getIdentifier())) {
                method = md;
                break;
            }
        }

        StaticExtractRule rule = new StaticExtractRule(
                "mq",
                null,
                true,
                0,
                null,
                Map.of(),
                new EndpointSpec("MQ", "inbound"),
                null,
                List.of(),
                null);

        Map<String, String> fields = new HashMap<>();
        fields.put("topic", "dirty");
        fields.put("operation", "CONSUME");

        var resolver = MapExternalValueResolver.ofIdentity(Map.of(
                "com.example.CooperAssetConsumer.run()", "cg_cooper_ep_ip_usercneter_employee_event"));

        Map<String, String> out = EndpointIdentityOverride.apply(
                fields, rule, type, method, "gemini", resolver, new HashMap<>());

        assertEquals("cg_cooper_ep_ip_usercneter_employee_event", out.get("topic"));
        assertEquals("config", out.get("parseLevel"));
    }

    @Test
    void missKeepsSerFields() {
        CompilationUnit cu = parse(
                """
                package com.example;
                class Producer {
                  public void send() {}
                }
                """);
        TypeDeclaration type = (TypeDeclaration) cu.types().get(0);
        MethodDeclaration method = (MethodDeclaration) type.bodyDeclarations().get(0);

        StaticExtractRule rule = new StaticExtractRule(
                "mq", null, true, 0, null, Map.of(), new EndpointSpec("MQ", "outbound"), null, List.of(), null);

        Map<String, String> fields = Map.of("topic", "{topic}");
        var resolver = MapExternalValueResolver.ofIdentity(Map.of(
                "com.other.Key.x()", "nope"));

        Map<String, String> out = EndpointIdentityOverride.apply(
                fields, rule, type, method, "demo", resolver, new HashMap<>());
        assertSame(fields, out);
        assertNull(out.get("parseLevel"));
    }

    @SuppressWarnings("deprecation")
    private static CompilationUnit parse(String source) {
        ASTParser parser = ASTParser.newParser(AST.JLS17);
        parser.setKind(ASTParser.K_COMPILATION_UNIT);
        parser.setSource(source.toCharArray());
        parser.setUnitName("T.java");
        parser.setResolveBindings(true);
        parser.setBindingsRecovery(true);
        parser.setEnvironment(new String[0], new String[0], null, true);
        parser.setCompilerOptions(JavaCore.getOptions());
        return (CompilationUnit) parser.createAST(null);
    }
}
