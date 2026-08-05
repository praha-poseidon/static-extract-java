package com.poseidon.javastatic.extract.cli;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class JavaExtractorSpecExampleTest {

    @TempDir
    Path tempDir;

    @Test
    void javaCliRunsSpecExamples() throws Exception {
        Path examplesRoot = SpecAssertions.findProjectRoot().resolve("examples/conformance");
        List<Path> examples;
        try (var stream = Files.list(examplesRoot)) {
            examples = stream
                    .filter(Files::isDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }

        assertFalse(examples.isEmpty(), "No Java spec examples found.");
        for (Path example : examples) {
            assertExample(example);
        }
    }

    private void assertExample(Path example) throws Exception {
        Path outputFile = tempDir.resolve(example.getFileName() + ".jsonl");
        CliOutput output = execute(
                "run",
                "--project", example.toString(),
                "--source", example.resolve("input").toString(),
                "--rule", example.resolve("rule.ser").toString(),
                "--out", outputFile.toString());

        assertEquals(0, output.exitCode(), output.stderr());
        SpecAssertions.assertJsonLinesMatchExtractedFactSpec(outputFile);
        assertJsonLinesEqual(example.resolve("expected.jsonl"), outputFile, example);
    }

    private void assertJsonLinesEqual(Path expectedFile, Path actualFile, Path example) throws Exception {
        List<String> expectedLines = Files.readAllLines(expectedFile);
        List<String> actualLines = Files.readAllLines(actualFile);

        assertEquals(expectedLines.size(), actualLines.size(), "Line count mismatch for " + example.getFileName());
        for (int i = 0; i < expectedLines.size(); i++) {
            String expectedLine = expectedLines.get(i).replace(
                    "${EXAMPLE_DIR}",
                    jsonStringValue(example.toAbsolutePath().normalize()));
            JsonNode expected = SpecAssertions.OBJECT_MAPPER.readTree(expectedLine);
            JsonNode actual = SpecAssertions.OBJECT_MAPPER.readTree(actualLines.get(i));
            assertEquals(expected, actual, "JSONL mismatch for " + example.getFileName() + " line " + (i + 1));
        }
    }

    private String jsonStringValue(Path path) {
        return path.toString().replace("\\", "\\\\");
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
}

