package com.poseidon.javastatic.extract.jdt;

import com.poseidon.javastatic.extract.jdt.trace.JdtTraceOptions;
import com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver;
import com.poseidon.javastatic.extract.language.AntlrSerRuleParser;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FunctionalCapabilityMatrixTest {

    private final AntlrSerRuleParser parser = new AntlrSerRuleParser();

    @Test
    void extractsGenericAnnotationFieldParameterReturnAndNewElements() {
        StaticExtractRule rule =
                parser.parse(
                        """
                        rule "Generic Annotated Operation"
                        endpoint BUSINESS inbound
                        find method with annotation @OperationDoc

                        let aggregate =
                          from annotation on class @EntityDoc take attr(value)

                        let operation =
                          from annotation on method @OperationDoc take attr(name)

                        let declaredPath =
                          from annotation on method @OperationDoc take attr(path)

                        let parameterName =
                          from parameter accountId take name

                        let parameterLookup =
                          from annotation on parameter @Input take attr(value)

                        let prefix =
                          from field PREFIX take value

                        let returnedValue =
                          from return take value

                        let allocatedType =
                          from new AuditRecord take type

                        build {
                          aggregate: aggregate
                          operation: operation
                          declaredPath: declaredPath | normalize pathVariable
                          parameterName: parameterName
                          parameterLookup: parameterLookup
                          prefix: prefix
                          returnedValue: returnedValue | normalize pathVariable
                          allocatedType: allocatedType
                        }
                        """);
        CompilationUnit cu =
                parse(
                        """
                        package demo;

                        @interface EntityDoc { String value(); }
                        @interface OperationDoc {
                            String name();
                            String path();
                        }
                        @interface Input { String value(); }
                        class AuditRecord {}

                        @EntityDoc("account")
                        class AccountFacade {
                            private static final String PREFIX = "/internal";

                            @OperationDoc(name = "load", path = "/accounts/{accountId}")
                            String load(@Input("account.id") String accountId) {
                                AuditRecord record = new AuditRecord();
                                String route = PREFIX + "/accounts/" + accountId;
                                return route;
                            }
                        }
                        """);

        Map<String, String> fields = singleResult(rule, cu, "AccountFacade").fields();

        assertEquals("account", fields.get("aggregate"));
        assertEquals("load", fields.get("operation"));
        assertEquals("/accounts/{param}", fields.get("declaredPath"));
        assertEquals("accountId", fields.get("parameterName"));
        assertEquals("account.id", fields.get("parameterLookup"));
        assertEquals("/internal", fields.get("prefix"));
        assertEquals("/internal/accounts/{param}", fields.get("returnedValue"));
        assertEquals("AuditRecord", fields.get("allocatedType"));
    }

    @Test
    void extractsGenericMethodCallArgumentsAndCallMetadata() {
        StaticExtractRule rule =
                parser.parse(
                        """
                        rule "Generic Method Call"
                        endpoint BUSINESS outbound
                        find method Gateway.submit

                        let action =
                          from argument[0] take value

                        let target =
                          from argument[1] take value

                        let callName =
                          from call take name

                        let callOwner =
                          from call take owner

                        let callRaw =
                          from call take raw

                        build {
                          action: action | map {
                            create: CREATE
                          }
                          target: target | normalize pathVariable
                          callName: callName
                          callOwner: callOwner
                          callRaw: callRaw
                        }
                        """);
        CompilationUnit cu =
                parse(
                        """
                        package demo;

                        class Gateway {
                            void submit(String action, String target) {}
                        }

                        class Workflow {
                            private Gateway gateway;
                            void create(String id) {
                                String base = "/workflows";
                                String target = base + "/" + id;
                                gateway.submit("create", target);
                            }
                        }
                        """);

        Map<String, String> fields = singleResult(rule, cu, "Workflow").fields();

        assertEquals("CREATE", fields.get("action"));
        assertEquals("/workflows/{param}", fields.get("target"));
        assertEquals("submit", fields.get("callName"));
        assertEquals("demo.Gateway", fields.get("callOwner"));
        assertEquals("gateway.submit(\"create\",target)", fields.get("callRaw"));
    }

    @Test
    void resolvesGenericExternalValuesFromFieldAnnotationAndMethodCallTraceRules() {
        StaticExtractRule rule =
                parser.parse(
                        """
                        rule "Generic External Trace"
                        endpoint BUSINESS outbound
                        find method with annotation @OperationDoc

                        let fromAnnotatedField =
                          from field annotatedBase take value

                        let fromLookupCall =
                          from field lookupBase take value

                        let returnedValue =
                          from return take value

                        build {
                          annotatedPath: fromAnnotatedField
                          lookupPath: fromLookupCall
                          returnedValue: returnedValue
                        }
                        """);
        StaticTraceRuleSet traceRules =
                parser.parseTrace(
                        """
                        trace "Generic External Entries"

                        from field
                        when annotation @ConfigRef on field

                        let rawValue =
                          from annotation on field @ConfigRef take attr(value)

                        build {
                          namespace: "config"
                          lookup: rawValue | normalize placeholderLookup
                          default: rawValue | normalize placeholderDefault
                        }

                        from call
                        when method ConfigStore.lookup

                        let lookupValue =
                          from argument[0] take value

                        build {
                          namespace: "config"
                          lookup: lookupValue
                        }
                        """);
        CompilationUnit cu =
                parse(
                        """
                        package demo;

                        @interface OperationDoc {}
                        @interface ConfigRef { String value(); }
                        class ConfigStore {
                            String lookup(String name) { return null; }
                        }

                        class Client {
                            @ConfigRef("${client.annotated:/default-annotated}")
                            private String annotatedBase;
                            private String lookupBase;

                            Client(ConfigStore store) {
                                lookupBase = store.lookup("client.lookup");
                            }

                            @OperationDoc
                            String call() {
                                return annotatedBase + lookupBase;
                            }
                        }
                        """);
        JdtTraceOptions options =
                JdtTraceOptions.of(
                        List.of(traceRules),
                        new MapExternalValueResolver(Map.of(
                                "config",
                                Map.of(
                                        "client.annotated", List.of("/annotated"),
                                        "client.lookup", List.of("/lookup")))));

        Map<String, String> fields =
                singleResult(new DefaultJdtStaticExtractEngine(options), rule, cu, "Client").fields();

        assertEquals("/annotated", fields.get("annotatedPath"));
        assertEquals("/lookup", fields.get("lookupPath"));
        assertEquals("/annotated/lookup", fields.get("returnedValue"));
    }

    @Test
    void findsFieldsByAnnotationAndResolvesExternalDictionaryValues() {
        StaticExtractRule rule =
                parser.parse(
                        """
                        rule "Config Fields"
                        endpoint CONFIG inbound
                        find field with annotation @ConfigProperty

                        let fieldName =
                          from field take name

                        let resolvedValue =
                          from field take value

                        build {
                          field: fieldName
                          value: resolvedValue
                        }
                        """);
        StaticTraceRuleSet traceRules =
                parser.parseTrace(
                        """
                        trace "ConfigProperty Trace"

                        from field
                        when annotation @ConfigProperty on field

                        let lookupValue =
                          from annotation on field @ConfigProperty take attr(name)

                        let defaultValue =
                          from annotation on field @ConfigProperty take attr(defaultValue)

                        build {
                          namespace: "config"
                          lookup: lookupValue
                          default: defaultValue
                        }
                        """);
        CompilationUnit cu =
                parse(
                        """
                        package demo;

                        @interface ConfigProperty {
                            String name();
                            String defaultValue() default "";
                        }

                        class ConfiguredClient {
                            @ConfigProperty(name = "client.url", defaultValue = "http://default")
                            String baseUrl;
                        }
                        """);
        JdtTraceOptions options =
                JdtTraceOptions.of(
                        List.of(traceRules),
                        new MapExternalValueResolver(Map.of(
                                "config", Map.of("client.url", List.of("http://real")))));

        Map<String, String> fields =
                singleResult(new DefaultJdtStaticExtractEngine(options), rule, cu, "ConfiguredClient").fields();

        assertEquals("baseUrl", fields.get("field"));
        assertEquals("http://real", fields.get("value"));
    }

    @Test
    void findsFieldByNameWithoutFrameworkSpecificCode() {
        StaticExtractRule rule =
                parser.parse(
                        """
                        rule "Named Field Value"
                        endpoint BUSINESS inbound
                        find field baseUrl

                        let fieldName =
                          from field take name

                        let fieldType =
                          from field take type

                        let fieldValue =
                          from field take value

                        build {
                          field: fieldName
                          type: fieldType
                          value: fieldValue
                        }
                        """);
        CompilationUnit cu =
                parse(
                        """
                        package demo;

                        class Client {
                            private String ignored = "no";
                            private String baseUrl = "http://service";
                        }
                        """);

        Map<String, String> fields = singleResult(rule, cu, "Client").fields();

        assertEquals("baseUrl", fields.get("field"));
        assertEquals("String", fields.get("type"));
        assertEquals("http://service", fields.get("value"));
    }

    private StaticExtractResult singleResult(StaticExtractRule rule, CompilationUnit cu, String typeName) {
        return singleResult(new DefaultJdtStaticExtractEngine(), rule, cu, typeName);
    }

    private StaticExtractResult singleResult(
            DefaultJdtStaticExtractEngine engine,
            StaticExtractRule rule,
            CompilationUnit cu,
            String typeName) {
        TypeDeclaration type = typeNamed(cu, typeName);
        List<StaticExtractResult> results = engine.execute(rule, cu, type, typeName + ".java", null);
        assertEquals(1, results.size());
        return results.get(0);
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

    private TypeDeclaration typeNamed(CompilationUnit cu, String name) {
        for (Object type : cu.types()) {
            if (type instanceof TypeDeclaration declaration
                    && name.equals(declaration.getName().getIdentifier())) {
                return declaration;
            }
        }
        throw new IllegalArgumentException("Missing type: " + name);
    }
}
