package com.poseidon.javastatic.extract.language;

import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import com.poseidon.javastatic.extract.source.TakeKind;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;
import com.poseidon.javastatic.extract.trace.TraceTargetKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AntlrSerRuleParserTest {

    @Test
    void parsesSpringMvcInboundRuleShape() {
        String ser =
                """
                rule "Spring MVC HTTP Inbound"
                endpoint HTTP inbound

                find method
when annotation @*Mapping on method

                let basePath =
                  from annotation @RequestMapping on class take attr(value)
                  from annotation @RequestMapping on class take attr(path)
                  fallback ""

                let methodPath =
                  from annotation @*Mapping on method take attr(value)
                  from annotation @*Mapping on method take attr(path)
                  fallback ""

                let httpMethod =
                  from annotation @*Mapping on method take name
                  map {
                    GetMapping: GET
                    PostMapping: POST
                    RequestMapping: GET
                  }

                build {
                  httpMethod: httpMethod
                  path: concat(basePath, "/", methodPath) | normalize slash
                }
                """;

        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);

        assertEquals("Spring MVC HTTP Inbound", rule.name());
        assertEquals("http_inbound", rule.fact().type());
        assertEquals("HTTP", rule.classifiers().get("category"));
        assertEquals("inbound", rule.classifiers().get("direction"));
        assertEquals("HTTP", rule.endpoint().type());
        assertEquals("inbound", rule.endpoint().direction());
        assertNotNull(rule.find().annotation());
        assertEquals(3, rule.lets().size());
        assertEquals(2, rule.build().fields().size());
    }

    @Test
    void parsesFactRuleShapeAndKeepsEndpointCompatibility() {
        String ser =
                """
                rule "React Button Action"
                fact ui_action

                find method
when annotation @Action on method

                let label =
                  from annotation @Action on method take attr(label)

                build {
                  label: label
                }
                """;

        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);

        assertEquals("React Button Action", rule.name());
        assertEquals("ui_action", rule.fact().type());
        assertEquals(0, rule.classifiers().size());
        assertEquals("ui_action", rule.endpoint().type());
        assertEquals("fact", rule.endpoint().direction());
        assertEquals(1, rule.lets().size());
        assertEquals(1, rule.build().fields().size());
    }

    @Test
    void parsesHashSlashAndBlockComments() {
        String ser =
                """
                # rule comment
                rule "Commented Rule"
                fact ui_text

                // find comment
                find jsx button

                /*
                 * let comment
                 */
                let label =
                  from children take text

                build {
                  text: label
                }
                """;

        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);

        assertEquals("Commented Rule", rule.name());
        assertEquals("ui_text", rule.fact().type());
        assertEquals("jsx", rule.find().targetKind());
        assertEquals("children", rule.lets().get(0).sources().get(0).elementKind());
    }

    @Test
    void preservesExtractorVocabularyKindsForFutureExtractors() {
        String ser =
                """
                rule "React Button Action"
                fact ui_action

                find jsx Button

                let label =
                  from children take text

                let handler =
                  from prop onClick take reference

                build {
                  label: label
                  handler: handler
                }
                """;

        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);

        assertEquals("ui_action", rule.fact().type());
        assertNull(rule.find().target());
        assertEquals("jsx", rule.find().targetKind());
        assertEquals("Button", rule.find().name());
        assertNull(rule.lets().get(0).sources().get(0).element());
        assertEquals("children", rule.lets().get(0).sources().get(0).elementKind());
        assertNull(rule.lets().get(0).sources().get(0).take().kind());
        assertEquals("text", rule.lets().get(0).sources().get(0).take().kindName());
        assertEquals("prop", rule.lets().get(1).sources().get(0).elementKind());
        assertEquals("onClick", rule.lets().get(1).sources().get(0).name());
        assertEquals("reference", rule.lets().get(1).sources().get(0).take().kindName());
    }

    @Test
    void parsesEmbeddedTraceBlockInRuleFile() {
        String ser =
                """
                rule "Spring External Values"
                fact config

                find method

                build {
                  ok: "true"
                }

                trace {
                  from field
                  when annotation @Value on field

                  let rawValue =
                    from annotation @Value on field take attr(value)

                  build {
                    namespace: "config"
                    lookup: rawValue | normalize placeholderLookup
                    default: rawValue | normalize placeholderDefault
                  }

                  from call
                  when method Environment.getProperty

                  let configLookup =
                    from argument[0] take value

                  build {
                    namespace: "config"
                    lookup: configLookup
                  }
                }
                """;

        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);
        StaticTraceRuleSet rules = rule.embeddedTrace();

        assertNotNull(rules);
        assertEquals("Spring External Values#trace", rules.name());
        assertEquals(2, rules.externalEntries().size());
        assertEquals(TraceTargetKind.FIELD, rules.externalEntries().get(0).target());
        assertEquals("namespace", rules.externalEntries().get(0).build().fields().keySet().iterator().next());
        assertEquals(TraceTargetKind.METHOD_CALL, rules.externalEntries().get(1).target());
        assertEquals("Environment", rules.externalEntries().get(1).match().method().ownerType());
    }

    @Test
    void parsesGeneralExtractRuleElementsAndBuildActions() {
        String ser =
                """
                rule "General Elements"
                endpoint CUSTOM outbound
                find call Client.[load,save]

                let argRaw =
                  from argument[0] take raw

                let argType =
                  from argument[0] take type

                let callOwner =
                  from call take owner

                let methodSignature =
                  from method take signature

                let classType =
                  from class take type

                let fieldName =
                  from field take name

                let parameterRaw =
                  from parameter input take raw

                let returnType =
                  from return take type

                let assignmentValue =
                  from assignment take value

                let newRaw =
                  from new com.example.UserDto take raw

                let literalValue =
                  from literal FIXED take value
                  fallback FIXED
                  map {
                    FIXED: mapped
                  }

                build {
                  route: argRaw | regex "(.+)" group 1 | replace "old" "new" | normalize extractPath
                  ownerField: callOwner
                  methodField: methodSignature
                  className: classType | normalize kebab
                  literalField: literalValue | map {
                    mapped: MAPPED
                  }
                }
                """;

        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);

        assertEquals("Client", rule.find().method().ownerType());
        assertEquals(JavaElementKind.ARGUMENT, rule.lets().get(0).sources().get(0).element());
        assertEquals(TakeKind.RAW, rule.lets().get(0).sources().get(0).take().kind());
        assertEquals(JavaElementKind.CALL, rule.lets().get(2).sources().get(0).element());
        assertEquals(TakeKind.OWNER, rule.lets().get(2).sources().get(0).take().kind());
        assertEquals(JavaElementKind.ASSIGNMENT, rule.lets().get(8).sources().get(0).element());
        assertEquals(5, rule.build().fields().size());
    }

    @Test
    void parsesGeneralTraceStuckPointConditions() {
        String ser =
                """
                rule "General Trace"
                fact general

                find method

                build {
                  ok: "true"
                }

                trace {
                  from parameter
                  when parameter name input
                  when parameter type String
                  when annotation @Config on parameter

                  let lookupValue =
                    from annotation @Config on parameter take attr(value)

                  build {
                    namespace: "config"
                    lookup: lookupValue
                  }

                  from assignment
                  when assignment field baseUrl

                  let assignedValue =
                    from assignment take value

                  build {
                    namespace: "config"
                    lookup: assignedValue
                  }

                  from method
                  when method name baseUrl
                  when annotation @Config on method

                  let methodLookup =
                    from annotation @Config on method take attr(value)

                  build {
                    namespace: "config"
                    lookup: methodLookup
                  }

                  from call
                  when call name get
                  when call owner ConfigService

                  let callLookup =
                    from argument[0] take value

                  build {
                    namespace: "config"
                    lookup: callLookup
                  }
                }
                """;

        StaticTraceRuleSet rules = new AntlrSerRuleParser().parse(ser).embeddedTrace();

        assertNotNull(rules);
        assertEquals(4, rules.externalEntries().size());
        assertEquals(TraceTargetKind.PARAMETER, rules.externalEntries().get(0).target());
        assertEquals("input", rules.externalEntries().get(0).match().parameterName());
        assertEquals("baseUrl", rules.externalEntries().get(1).match().assignmentField());
        assertEquals("baseUrl", rules.externalEntries().get(2).match().methodName());
        assertEquals("ConfigService", rules.externalEntries().get(3).match().callOwner());
    }

    @Test
    void parsesExpressionConditionsForFutureExtractors() {
        String ser =
                """
                rule "Condition Filter"
                fact route

                find call [get,post,delete]
                when if call.owner == router and call.name in [get,post] and not argument[0].value matches "^/internal"

                let path =
                  from argument[0] take value

                build {
                  path: path
                }
                """;

        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);

        assertEquals("Condition Filter", rule.name());
        assertEquals("route", rule.fact().type());
        assertEquals("call", rule.find().targetKind());
        assertEquals("[get,post,delete]", rule.find().name());
    }

    @Test
    void parsesFieldAnnotationFindRule() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "Config Fields"
                                endpoint CONFIG inbound
                                find field
when annotation @ConfigProperty on field

                                let fieldName =
                                  from field take name

                                build {
                                  field: fieldName
                                }
                                """);

        assertEquals(JavaElementKind.FIELD, rule.find().target());
        assertEquals("ConfigProperty", rule.find().annotation().names().get(0));
    }

    @Test
    void parsesNamedFieldFindRule() {
        StaticExtractRule rule =
                new AntlrSerRuleParser()
                        .parse(
                                """
                                rule "Named Field"
                                endpoint CONFIG inbound
                                find field baseUrl

                                let value =
                                  from field take value

                                build {
                                  value: value
                                }
                                """);

        assertEquals(JavaElementKind.FIELD, rule.find().target());
        assertEquals("baseUrl", rule.find().name());
        assertEquals("value", rule.lets().get(0).name());
    }

    @Test
    void rejectsInvalidSerSyntaxWithLocation() {
        IllegalArgumentException error =
                assertThrows(
                        IllegalArgumentException.class,
                        () -> new AntlrSerRuleParser().parse(
                                """
                                rule "Broken"
                                endpoint CUSTOM inbound
                                find
                                build {
                                  value: "x"
                                }
                                """));

        assertNotNull(error.getMessage());
    }
}
