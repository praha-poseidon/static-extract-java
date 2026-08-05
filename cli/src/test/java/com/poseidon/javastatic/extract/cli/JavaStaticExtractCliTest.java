package com.poseidon.javastatic.extract.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaStaticExtractCliTest {

    @TempDir
    Path tempDir;

    @Test
    void initCommandPrintsJson() throws Exception {
        CliOutput output = execute("init", "--project", tempDir.toString());

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("\"generatedRulesDir\""));
        assertTrue(Files.isDirectory(tempDir.resolve(".ser/generated")));
    }

    @Test
    void tryCommandRunsRuleAndPrintsResultJson() throws Exception {
        Fixture fixture = fixture();

        CliOutput output = execute(
                "try",
                "--project", fixture.project().toString(),
                "--source", fixture.javaFile().toString(),
                "--rule", fixture.ruleFile().toString());

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("\"resultCount\" : 1"));
        assertTrue(output.stdout().contains("/api/users"));
    }

    @Test
    void diagnoseCommandPrintsFactsWhenRuleDoesNotMatch() throws Exception {
        Fixture fixture = fixture();
        Path missingRule = tempDir.resolve("missing.ser");
        Files.writeString(missingRule, """
                rule "Missing"
                fact backend_endpoint

                find method with annotation @Missing

                let path =
                  from annotation on method @Missing take attr(value)

                build {
                  path: path
                }
                """);

        CliOutput output = execute(
                "diagnose",
                "--project", fixture.project().toString(),
                "--source", fixture.javaFile().toString(),
                "--rule", missingRule.toString());

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("\"status\" : \"NO_MATCH\""));
        assertTrue(output.stdout().contains("RouteGet"));
    }

    @Test
    void runCommandWritesJsonLinesAndReadsExternalValues() throws Exception {
        Fixture fixture = fixture();
        Path externalValues = tempDir.resolve("external-values.json");
        Files.writeString(externalValues, """
                {
                  "config": {
                    "unused": ["value"]
                  }
                }
                """);
        Path outputFile = tempDir.resolve("out/results.jsonl");

        CliOutput output = execute(
                "run",
                "--project", fixture.project().toString(),
                "--rule", fixture.ruleFile().toString(),
                "--external-values", externalValues.toString(),
                "--out", outputFile.toString());

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("\"resultCount\" : 1"));
        assertEquals(1, Files.readAllLines(outputFile).size());
        SpecAssertions.assertJsonLinesMatchExtractedFactSpec(outputFile);
    }

    @Test
    void commandPrintsStructuredError() throws Exception {
        Fixture fixture = fixture();

        CliOutput output = execute(
                "try",
                "--project", fixture.project().toString(),
                "--source", fixture.javaFile().toString());

        assertEquals(1, output.exitCode());
        assertTrue(output.stderr().contains("\"status\":\"ERROR\""));
        assertTrue(output.stderr().contains("Pass at least one SER rule"));
    }

    @Test
    void rootCommandPrintsUsage() throws Exception {
        CliOutput output = execute();

        assertEquals(0, output.exitCode());
        assertTrue(output.stdout().contains("Usage: static-extract-java"));
    }

    private CliOutput execute(String... args) throws Exception {
        PrintStream originalOut = System.out;
        PrintStream originalErr = System.err;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
            int exitCode = new CommandLine(new JavaStaticExtractCli()).execute(args);
            return new CliOutput(
                    exitCode,
                    out.toString(StandardCharsets.UTF_8),
                    err.toString(StandardCharsets.UTF_8));
        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private record CliOutput(int exitCode, String stdout, String stderr) {}

    private Fixture fixture() throws Exception {
        Path project = tempDir.resolve("project-" + System.nanoTime());
        Path sourceDir = project.resolve("src/main/java/demo");
        Files.createDirectories(sourceDir);
        Path javaFile = sourceDir.resolve("Api.java");
        Files.writeString(javaFile, """
                package demo;

                @interface RouteGet {
                    String value();
                }

                class Api {
                    @RouteGet("/api/users")
                    String getUser() {
                        return "ok";
                    }
                }
                """);
        Path ruleFile = tempDir.resolve("route-" + System.nanoTime() + ".ser");
        Files.writeString(ruleFile, """
                rule "Custom HTTP Inbound"
                fact backend_endpoint

                find method with annotation @RouteGet

                let path =
                  from annotation on method @RouteGet take attr(value)

                build {
                  path: path
                }
                """);
        return new Fixture(project, javaFile, ruleFile);
    }

    private record Fixture(Path project, Path javaFile, Path ruleFile) {}
}
