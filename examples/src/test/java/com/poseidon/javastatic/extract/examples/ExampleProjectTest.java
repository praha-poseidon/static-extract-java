package com.poseidon.javastatic.extract.examples;

import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.jdt.project.JavaStaticExtractProjectRunner;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleProjectTest {

    @Test
    void extractsEndpointsFromExampleProject() throws IOException {
        Path project = Path.of("src/test/resources/example-project");
        List<StaticExtractResult> results =
                JavaStaticExtractProjectRunner.builder()
                        .project(project)
                        .externalValues(Map.of(
                                "config", Map.of(
                                        "users.base-url", List.of("http://users.internal"))))
                        .build()
                        .extract();

        writeOutput(results);

        assertHasResult(results, "HTTP", "inbound", "GET", "/api/users/{param}");
        assertHasResult(results, "HTTP", "inbound", "POST", "/api/users");
        assertHasResult(results, "HTTP", "outbound", "GET", "/api/users/{param}");
        assertHasResult(results, "HTTP", "outbound", "POST", "/api/users");
    }

    private void assertHasResult(
            List<StaticExtractResult> results,
            String type,
            String direction,
            String httpMethod,
            String path) {
        assertTrue(
                results.stream().anyMatch(result ->
                        type.equals(result.rule().endpoint().type())
                                && direction.equals(result.rule().endpoint().direction())
                                && httpMethod.equals(result.fields().get("httpMethod"))
                                && path.equals(result.fields().get("path"))),
                () -> "Missing result: " + type + " " + direction + " " + httpMethod + " " + path
                        + "\nActual:\n" + format(results));
    }

    private void writeOutput(List<StaticExtractResult> results) throws IOException {
        Path output = Path.of("target/example-output.txt");
        Files.createDirectories(output.getParent());
        Files.writeString(output, format(results));
    }

    private String format(List<StaticExtractResult> results) {
        StringBuilder out = new StringBuilder();
        out.append("Static Extract Java example output\n");
        out.append("==================================\n\n");
        results.stream()
                .sorted(Comparator
                        .comparing((StaticExtractResult result) -> result.rule().endpoint().direction())
                        .thenComparing(result -> result.fields().getOrDefault("httpMethod", ""))
                        .thenComparing(result -> result.fields().getOrDefault("path", "")))
                .forEach(result -> {
                    out.append("rule: ").append(result.rule().name()).append('\n');
                    out.append("endpoint: ")
                            .append(result.rule().endpoint().type())
                            .append(' ')
                            .append(result.rule().endpoint().direction())
                            .append('\n');
                    out.append("fields: ").append(result.fields()).append('\n');
                    out.append("lines: ").append(result.startLine()).append('-').append(result.endLine()).append('\n');
                    out.append("method: ").append(result.enclosingMethodSignatureHint()).append("\n\n");
                });
        return out.toString();
    }
}
