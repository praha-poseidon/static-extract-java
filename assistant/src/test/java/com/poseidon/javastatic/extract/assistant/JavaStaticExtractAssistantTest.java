package com.poseidon.javastatic.extract.assistant;

import com.poseidon.javastatic.extract.extractor.ExtractedFact;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaStaticExtractAssistantTest {

    @TempDir
    Path tempDir;

    private final JavaStaticExtractAssistant assistant = new JavaStaticExtractAssistant();

    @Test
    void initCreatesSerWorkspace() throws Exception {
        InitReport report = assistant.init(tempDir);

        assertTrue(Files.isDirectory(Path.of(report.generatedRulesDir())));
        assertTrue(Files.isDirectory(Path.of(report.reportDir())));
        assertTrue(Files.isDirectory(Path.of(report.resultDir())));
    }

    @Test
    void tryRuleRunsUserSerAgainstSelectedFile() throws Exception {
        Fixture fixture = fixture();

        TryReport report = assistant.tryRule(new TryRequest(
                fixture.project(),
                List.of(fixture.javaFile()),
                List.of(fixture.ruleFile()),
                List.of(),
                List.of(),
                List.of(),
                false,
                null));

        assertEquals("OK", report.status());
        assertEquals(1, report.resultCount());
        ExtractedFact result = report.results().getFirst();
        assertEquals("GET", result.fields().get("httpMethod"));
        assertEquals("/api/users/{param}", result.fields().get("path"));
        assertEquals("http_inbound", result.factType());
        assertEquals("HTTP", result.classifiers().get("category"));
        assertEquals("inbound", result.classifiers().get("direction"));
    }

    @Test
    void diagnoseReturnsSourceFactsWhenRuleDoesNotMatch() throws Exception {
        Fixture fixture = fixture();
        Path missingRule = tempDir.resolve("missing.ser");
        Files.writeString(missingRule, """
                rule "Missing"
                endpoint HTTP inbound

                find method with annotation @Missing

                let path =
                  from annotation on method @Missing take attr(value)

                build {
                  path: path
                }
                """);

        DiagnosticReport report = assistant.diagnose(new DiagnosticRequest(
                fixture.project(),
                List.of(fixture.javaFile()),
                List.of(missingRule),
                List.of(),
                List.of(),
                List.of(),
                false,
                null));

        assertEquals("NO_MATCH", report.status());
        assertEquals(0, report.tryReport().resultCount());
        assertTrue(report.facts().getFirst().annotations().contains("RouteGet"));
        assertFalse(report.hints().isEmpty());
    }

    @Test
    void runWritesJsonLinesWhenOutputFileIsSet() throws Exception {
        Fixture fixture = fixture();
        Path output = tempDir.resolve("results/out.jsonl");

        RunReport report = assistant.run(new RunRequest(
                fixture.project(),
                List.of(),
                List.of(),
                List.of(),
                List.of(fixture.ruleFile()),
                List.of(),
                List.of(),
                List.of(),
                false,
                output,
                null));

        assertEquals(1, report.resultCount());
        assertTrue(Files.exists(output));
        assertEquals(1, Files.readAllLines(output).size());
    }

    @Test
    void ruleFileCanContainTraceBlocks() throws Exception {
        Fixture fixture = fixture();
        Files.writeString(fixture.ruleFile(), Files.readString(fixture.ruleFile()) + """

                trace "Unused Trace"

                from field
                when annotation @Value on field

                let rawValue =
                  from annotation on field @Value take attr(value)

                build {
                  namespace: "config"
                  lookup: rawValue | normalize placeholderLookup
                }
                """);

        TryReport report = assistant.tryRule(new TryRequest(
                fixture.project(),
                List.of(fixture.javaFile()),
                List.of(fixture.ruleFile()),
                List.of(),
                List.of(),
                List.of(),
                false,
                null));

        assertEquals("OK", report.status());
        assertEquals(1, report.resultCount());
    }

    private Fixture fixture() throws Exception {
        Path project = tempDir.resolve("project");
        Path sourceDir = project.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Path javaFile = sourceDir.resolve("Api.java");
        Files.writeString(javaFile, """
                package demo;

                @interface RouteGet {
                    String value();
                }

                class Api {
                    @RouteGet("/api/users/{id}")
                    String getUser() {
                        return "ok";
                    }
                }
                """);
        Path ruleFile = tempDir.resolve("route.ser");
        Files.writeString(ruleFile, """
                rule "Custom HTTP Inbound"
                endpoint HTTP inbound

                find method with annotation @RouteGet

                let httpMethod =
                  from literal GET take value

                let path =
                  from annotation on method @RouteGet take attr(value)

                build {
                  httpMethod: httpMethod
                  path: path | normalize slash | normalize pathVariable
                }
                """);
        return new Fixture(project, javaFile, ruleFile);
    }

    private record Fixture(Path project, Path javaFile, Path ruleFile) {}
}
