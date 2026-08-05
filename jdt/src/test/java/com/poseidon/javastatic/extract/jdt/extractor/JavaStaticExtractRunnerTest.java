package com.poseidon.javastatic.extract.jdt.extractor;

import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.jdt.load.SerRuleLoader;
import com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceResolver;
import com.poseidon.javastatic.extract.language.AntlrSerRuleParser;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JavaStaticExtractRunnerTest {

    @Test
    void extractsWithProgrammaticRulesAndTraceConfiguration() {
        AntlrSerRuleParser parser = new AntlrSerRuleParser();
        StaticExtractRule rule =
                parser.parse(
                        """
                        rule "Trace Field Value"
                        endpoint CUSTOM inbound
                        find method with annotation @Endpoint

                        let basePath =
                          from field basePath take value

                        build {
                          path: basePath
                        }
                        """);
        StaticTraceRuleSet traceRuleSet =
                parser.parseTrace(
                        """
                        trace "Spring Config Trace"

                        from field
when annotation @Value on field

let rawValue =
  from annotation on field @Value take attr(value)

build {
  namespace: "config"
  lookup: rawValue | normalize placeholderLookup
  default: rawValue | normalize placeholderDefault
}
                        """);
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @interface Endpoint {}
                        @interface Value {
                            String value();
                        }

                        class Client {
                            @Value("${service.base-url:http://default}")
                            private String basePath;

                            @Endpoint
                            String load() {
                                return basePath;
                            }
                        }
                        """);
        JavaStaticExtractRunner runner =
                JavaStaticExtractRunner.builder()
                        .classpathRules(false)
                        .classpathTraceRules(false)
                        .addRule(rule)
                        .addTraceRuleSet(traceRuleSet)
                        .externalValues(Map.of(
                                "config", Map.of("service.base-url", List.of("http://users"))))
                        .build();

        List<StaticExtractResult> results = runner.extract(cu, "Client.java", null);

        assertEquals(1, results.size());
        assertEquals("http://users", results.get(0).fields().get("path"));
    }

    @Test
    void shipsJavaExtractorRulesByDefault() {
        JavaStaticExtractRunner runner = JavaStaticExtractRunner.builder().build();

        assertEquals(
                List.of("Spring MVC HTTP Inbound", "RestTemplate HTTP Outbound"),
                runner.rules().stream().map(StaticExtractRule::name).toList());
    }

    @Test
    void supportsCustomJdtTraceResolver() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "Custom Trace Resolver"
                                endpoint CUSTOM inbound
                                find method with annotation @Endpoint

                                let basePath =
                                  from field basePath take value

                                build {
                                  path: basePath
                                }
                                """);
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @interface Endpoint {}

                        class Client {
                            private String basePath;

                            @Endpoint
                            String load() {
                                return basePath;
                            }
                        }
                        """);
        JdtTraceResolver resolver =
                new JdtTraceResolver() {
                    @Override
                    public List<String> resolveField(
                            FieldDeclaration field,
                            VariableDeclarationFragment fragment,
                            TypeDeclaration typeDeclaration,
                            com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceContext context) {
                        return "basePath".equals(fragment.getName().getIdentifier())
                                ? List.of("/custom")
                                : List.of();
                    }
                };
        JavaStaticExtractRunner runner =
                JavaStaticExtractRunner.builder()
                        .classpathRules(false)
                        .classpathTraceRules(false)
                        .addRule(rule)
                        .addTraceResolver(resolver)
                        .build();

        List<StaticExtractResult> results = runner.extract(cu, "Client.java", null);

        assertEquals(1, results.size());
        assertEquals("/custom", results.get(0).fields().get("path"));
    }

    @Test
    void builderAcceptsNullsAndLoaderBasedInputs() {
        AntlrSerRuleParser parser = new AntlrSerRuleParser();
        StaticExtractRule rule =
                parser.parse(
                        """
                        rule "Loaded"
                        endpoint CUSTOM inbound
                        find class

                        build {
                          kind: "loaded"
                        }
                        """);
        StaticTraceRuleSet traceRuleSet =
                parser.parseTrace(
                        """
                        trace "Empty Trace"
                        """);
        SerRuleLoader loader =
                new SerRuleLoader(null, parser) {
                    @Override
                    public List<StaticExtractRule> loadAll() {
                        return List.of(rule);
                    }

                    @Override
                    public List<StaticTraceRuleSet> loadApplicationTraceRules() {
                        return List.of(traceRuleSet);
                    }

                    @Override
                    public List<StaticExtractRule> loadRulesFromDirectory(java.nio.file.Path directory) {
                        return List.of(rule);
                    }

                    @Override
                    public List<StaticTraceRuleSet> loadTraceRulesFromDirectory(java.nio.file.Path directory) {
                        return List.of(traceRuleSet);
                    }

                    @Override
                    public List<StaticExtractRule> loadRulesFromFiles(List<java.nio.file.Path> files) {
                        return List.of(rule);
                    }

                    @Override
                    public List<StaticTraceRuleSet> loadTraceRulesFromFiles(List<java.nio.file.Path> files) {
                        return List.of(traceRuleSet);
                    }
                };

        JavaStaticExtractRunner runner =
                JavaStaticExtractRunner.builder(loader)
                        .addRule(null)
                        .addRules(null)
                        .addTraceRuleSet(null)
                        .addTraceRuleSets(null)
                        .addTraceResolver(null)
                        .addTraceResolvers(null)
                        .externalValueResolver(null)
                        .rulesFromDirectory(java.nio.file.Path.of("rules"))
                        .traceRulesFromDirectory(java.nio.file.Path.of("traces"))
                        .rulesFromFiles(List.of(java.nio.file.Path.of("a.ser")))
                        .traceRulesFromFiles(List.of(java.nio.file.Path.of("t.ser")))
                        .build();

        assertEquals(3, runner.rules().size());
        assertEquals(List.of(), runner.extract(null, "Missing.java", null));
        assertEquals(List.of(), runner.extract(parse("class A {}"), null, "A.java", null));
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
