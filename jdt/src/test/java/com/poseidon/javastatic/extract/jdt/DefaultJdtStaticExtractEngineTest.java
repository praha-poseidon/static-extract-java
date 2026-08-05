package com.poseidon.javastatic.extract.jdt;

import com.poseidon.javastatic.extract.language.AntlrSerRuleParser;
import com.poseidon.javastatic.extract.jdt.trace.JdtTraceOptions;
import com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver;
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

class DefaultJdtStaticExtractEngineTest {

    @Test
    void extractsSpringMvcInboundEndpointFields() {
        StaticExtractRule rule = loadRule("Spring MVC HTTP Inbound");
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @RequestMapping("/api")
                        class UserController {
                            @GetMapping("/users")
                            String list() {
                                return "ok";
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "UserController");

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine().execute(rule, cu, type, "UserController.java", null);

        assertEquals(1, results.size());
        Map<String, String> fields = results.get(0).fields();
        assertEquals("GET", fields.get("httpMethod"));
        assertEquals("/api/users", fields.get("path"));
    }

    @Test
    void expandsMultipleAnnotationPaths() {
        StaticExtractRule rule = loadRule("Spring MVC HTTP Inbound");
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @RequestMapping({"/api", "/openapi"})
                        class UserController {
                            @GetMapping({"/users", "/members"})
                            String list() {
                                return "ok";
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "UserController");

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine().execute(rule, cu, type, "UserController.java", null);

        List<String> paths = results.stream().map(r -> r.fields().get("path")).sorted().toList();
        assertEquals(List.of("/api/members", "/api/users", "/openapi/members", "/openapi/users"), paths);
    }

    @Test
    void extractsRouterInboundPathFromArgument() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "Router HTTP Inbound"
                                endpoint HTTP inbound
                                find method Router.[get,post]

                                let path =
                                  from argument[0] take value

                                let httpMethod =
                                  from method take name
                                  map {
                                    get: GET
                                    post: POST
                                  }

                                build {
                                  httpMethod: httpMethod
                                  path: path | normalize pathVariable
                                }
                                """);
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        class Router {
                            Route get(String path) { return null; }
                            Route post(String path) { return null; }
                        }
                        class Route {}
                        class Routes {
                            private Router router;
                            void init() {
                                String base = "/api";
                                String users = base + "/users/{id}";
                                router.get(users);
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "Routes");

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine().execute(rule, cu, type, "Routes.java", null);

        assertEquals(1, results.size());
        Map<String, String> fields = results.get(0).fields();
        assertEquals("GET", fields.get("httpMethod"));
        assertEquals("/api/users/{param}", fields.get("path"));
    }

    @Test
    void extractsRestTemplateOutboundPathAndMethod() {
        StaticExtractRule rule = loadRule("RestTemplate HTTP Outbound");
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        class RestTemplate {
                            Object getForObject(String url, Class<?> type) { return null; }
                        }
                        class Client {
                            private RestTemplate restTemplate;
                            private static final String BASE_URL = "http://users";
                            void load(String id) {
                                String url = BASE_URL + "/api/users/" + id;
                                restTemplate.getForObject(url, Object.class);
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "Client");

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine().execute(rule, cu, type, "Client.java", null);

        assertEquals(1, results.size());
        Map<String, String> fields = results.get(0).fields();
        assertEquals("GET", fields.get("httpMethod"));
        assertEquals("/api/users/{param}", fields.get("path"));
    }

    @Test
    void supportsClassFieldParameterReturnLiteralAndNewSources() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "General Java Elements"
                                endpoint CUSTOM inbound
                                find method with annotation @Endpoint

                                let className =
                                  from class take name

                                let fieldValue =
                                  from field BASE take value

                                let parameterType =
                                  from parameter id take type

                                let returnValue =
                                  from return take value

                                let literalValue =
                                  from literal "fixed" take value

                                let newType =
                                  from new UserDto take type

                                build {
                                  className: className
                                  fieldValue: fieldValue
                                  parameterType: parameterType
                                  returnValue: returnValue
                                  literalValue: literalValue
                                  newType: newType
                                }
                                """);
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        class UserDto {}
                        class UserController {
                            private static final String BASE = "/api";
                            @Endpoint
                            String list(String id) {
                                UserDto dto = new UserDto();
                                return BASE + "/" + id;
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "UserController");

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine().execute(rule, cu, type, "UserController.java", null);

        assertEquals(1, results.size());
        Map<String, String> fields = results.get(0).fields();
        assertEquals("UserController", fields.get("className"));
        assertEquals("/api", fields.get("fieldValue"));
        assertEquals("String", fields.get("parameterType"));
        assertEquals("/api/{id}", fields.get("returnValue"));
        assertEquals("fixed", fields.get("literalValue"));
        assertEquals("UserDto", fields.get("newType"));
    }

    @Test
    void resolvesFieldExternalValueWithTraceRuleAndDictionary() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
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
        StaticTraceRuleSet traceRules =
                new AntlrSerRuleParser()
                        .parseTrace(
                                """
                                trace "Spring External Values"

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
        TypeDeclaration type = typeNamed(cu, "Client");
        JdtTraceOptions options =
                JdtTraceOptions.of(
                        List.of(traceRules),
                        new MapExternalValueResolver(Map.of(
                                "config", Map.of("service.base-url", List.of("http://users")))));

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine(options).execute(rule, cu, type, "Client.java", null);

        assertEquals(1, results.size());
        assertEquals("http://users", results.get(0).fields().get("path"));
    }

    @Test
    void resolvesMethodCallExternalValueWithTraceRuleAndDictionary() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "Trace Call Value"
                                endpoint CUSTOM outbound
                                find method with annotation @Endpoint

                                let basePath =
                                  from return take value

                                build {
                                  path: basePath
                                }
                                """);
        StaticTraceRuleSet traceRules =
                new AntlrSerRuleParser()
                        .parseTrace(
                                """
                                trace "Spring External Values"

                                from call
when method Environment.getProperty

let configLookup =
  from argument[0] take value

build {
  namespace: "config"
  lookup: configLookup
}
                                """);
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @interface Endpoint {}
                        class Environment {
                            String getProperty(String key) { return null; }
                        }
                        class Client {
                            private Environment environment;

                            @Endpoint
                            String load() {
                                String key = "service.path";
                                return environment.getProperty(key);
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "Client");
        JdtTraceOptions options =
                JdtTraceOptions.of(
                        List.of(traceRules),
                        new MapExternalValueResolver(Map.of(
                                "config", Map.of("service.path", List.of("/api/users")))));

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine(options).execute(rule, cu, type, "Client.java", null);

        assertEquals(1, results.size());
        assertEquals("/api/users", results.get(0).fields().get("path"));
    }

    @Test
    void resolvesParameterExternalValueWithTraceRuleAndDictionary() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "Trace Parameter Value"
                                endpoint CUSTOM inbound
                                find method with annotation @Endpoint

                                let basePath =
                                  from return take value

                                build {
                                  path: basePath
                                }
                                """);
        StaticTraceRuleSet traceRules =
                new AntlrSerRuleParser()
                        .parseTrace(
                                """
                                trace "Parameter External Values"

                                from parameter
                                when annotation @Value on parameter

                                let rawValue =
                                  from annotation on parameter @Value take attr(value)

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
                            @Endpoint
                            String load(@Value("${service.path:/default}") String basePath) {
                                return basePath;
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "Client");
        JdtTraceOptions options =
                JdtTraceOptions.of(
                        List.of(traceRules),
                        new MapExternalValueResolver(Map.of(
                                "config", Map.of("service.path", List.of("/api/users")))));

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine(options).execute(rule, cu, type, "Client.java", null);

        assertEquals(1, results.size());
        assertEquals("/api/users", results.get(0).fields().get("path"));
    }

    @Test
    void tracesFieldAssignmentsBeforeExternalLookup() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "Trace Assigned Field"
                                endpoint CUSTOM inbound
                                find method with annotation @Endpoint

                                let basePath =
                                  from return take value

                                build {
                                  path: basePath
                                }
                                """);
        StaticTraceRuleSet traceRules =
                new AntlrSerRuleParser()
                        .parseTrace(
                                """
                                trace "Call External Values"

                                from call
                                when method Environment.getProperty

                                let configLookup =
                                  from argument[0] take value

                                build {
                                  namespace: "config"
                                  lookup: configLookup
                                }
                                """);
        CompilationUnit cu =
                parse(
                        """
                        package com.example;

                        @interface Endpoint {}
                        class Environment {
                            String getProperty(String name) { return null; }
                        }

                        class Client {
                            private String basePath;

                            Client(Environment environment) {
                                this.basePath = environment.getProperty("service.path");
                            }

                            @Endpoint
                            String load() {
                                return basePath;
                            }
                        }
                        """);
        TypeDeclaration type = typeNamed(cu, "Client");
        JdtTraceOptions options =
                JdtTraceOptions.of(
                        List.of(traceRules),
                        new MapExternalValueResolver(Map.of(
                                "config", Map.of("service.path", List.of("/api/users")))));

        List<StaticExtractResult> results =
                new DefaultJdtStaticExtractEngine(options).execute(rule, cu, type, "Client.java", null);

        assertEquals(1, results.size());
        assertEquals("/api/users", results.get(0).fields().get("path"));
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

    private StaticExtractRule loadRule(String name) {
        AntlrSerRuleParser parser = new AntlrSerRuleParser();
        return switch (name) {
            case "Spring MVC HTTP Inbound" -> parser.parse(
                    """
                    rule "Spring MVC HTTP Inbound"
                    endpoint HTTP inbound

                    find method with annotation @*Mapping

                    let basePath =
                      from annotation on class @RequestMapping take attr(value)
                      from annotation on class @RequestMapping take attr(path)
                      default ""

                    let methodPath =
                      from annotation on method @*Mapping take attr(value)
                      from annotation on method @*Mapping take attr(path)
                      default ""

                    let httpMethod =
                      from annotation on method @*Mapping take name
                      map {
                        GetMapping: GET
                        PostMapping: POST
                        PutMapping: PUT
                        DeleteMapping: DELETE
                        PatchMapping: PATCH
                        RequestMapping: GET
                      }

                    build {
                      httpMethod: httpMethod
                      path: concat(basePath, methodPath) | normalize slash | normalize pathVariable
                    }
                    """);
            case "RestTemplate HTTP Outbound" -> parser.parse(
                    """
                    rule "RestTemplate HTTP Outbound"
                    endpoint HTTP outbound

                    find method RestTemplate.[getForObject,getForEntity,postForObject,postForEntity,put,delete]

                    let rawUrl =
                      from argument[0] take value

                    let httpMethod =
                      from method take name
                      map {
                        getForObject: GET
                        getForEntity: GET
                        postForObject: POST
                        postForEntity: POST
                        put: PUT
                        delete: DELETE
                      }

                    build {
                      httpMethod: httpMethod
                      path: rawUrl | normalize extractPath | normalize pathVariable
                    }
                    """);
            default -> throw new IllegalArgumentException("Missing test rule: " + name);
        };
    }
}
