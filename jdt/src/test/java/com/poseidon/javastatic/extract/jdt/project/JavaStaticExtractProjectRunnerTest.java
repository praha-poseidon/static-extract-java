package com.poseidon.javastatic.extract.jdt.project;

import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.language.AntlrSerRuleParser;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JavaStaticExtractProjectRunnerTest {

    @TempDir
    Path tempDir;

    @Test
    void extractsFromSourceWithoutProjectClassesOrDependencies() throws IOException {
        Path sourceFile = tempDir.resolve("Client.java");
        Files.writeString(sourceFile, source());

        List<StaticExtractResult> results =
                JavaStaticExtractProjectRunner.builder()
                        .classpathRules(false)
                        .classpathTraceRules(false)
                        .source(sourceFile)
                        .addRule(rule())
                        .build()
                        .extract();

        assertEquals(1, results.size());
        assertEquals("/users", results.get(0).fields().get("path"));
    }

    @Test
    void discoversSourceFromProjectRoot() throws IOException {
        Path sourceDirectory = tempDir.resolve("src/main/java/com/example");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("Client.java"), source());

        JavaStaticExtractProjectRunner runner =
                JavaStaticExtractProjectRunner.builder()
                        .classpathRules(false)
                        .classpathTraceRules(false)
                        .project(tempDir)
                        .addRule(rule())
                        .build();

        List<StaticExtractResult> results = runner.extract();

        assertEquals(List.of(tempDir.resolve("src/main/java").toAbsolutePath().normalize()), runner.sources());
        assertEquals(1, results.size());
        assertEquals("/users", results.get(0).fields().get("path"));
    }

    @Test
    void discoversSourcesFromMultiModuleProjectRoot() throws IOException {
        Path sourceDirectory = tempDir.resolve("service-a/src/main/java/com/example");
        Files.createDirectories(sourceDirectory);
        Files.writeString(sourceDirectory.resolve("Client.java"), source());

        JavaStaticExtractProjectRunner runner =
                JavaStaticExtractProjectRunner.builder()
                        .classpathRules(false)
                        .classpathTraceRules(false)
                        .project(tempDir)
                        .addRule(rule())
                        .build();

        List<StaticExtractResult> results = runner.extract();

        assertEquals(List.of(tempDir.resolve("service-a/src/main/java").toAbsolutePath().normalize()), runner.sources());
        assertEquals(1, results.size());
        assertEquals("service-a/src/main/java/com/example/Client.java", results.get(0).projectFilePath());
    }

    @Test
    void resolvesRelativeInputsAndDefaultProjectArtifacts() throws IOException {
        Path sourceDirectory = tempDir.resolve("src/main/java/com/example");
        Path classesDirectory = tempDir.resolve("target/classes");
        Path dependencyDirectory = tempDir.resolve("target/dependency");
        Files.createDirectories(sourceDirectory);
        Files.createDirectories(classesDirectory);
        Files.createDirectories(dependencyDirectory);
        Files.writeString(sourceDirectory.resolve("Client.java"), source());
        writeEmptyJar(dependencyDirectory.resolve("dep.jar"));
        Files.writeString(dependencyDirectory.resolve("ignore.txt"), "");

        JavaStaticExtractProjectRunner runner =
                JavaStaticExtractProjectRunner.builder()
                        .classpathRules(false)
                        .classpathTraceRules(false)
                        .project(tempDir)
                        .source(Path.of("src/main/java"))
                        .classes(Path.of("target/classes"))
                        .dependency(Path.of("target/dependency"))
                        .dependency(null)
                        .charset(null)
                        .addRules(List.of(rule()))
                        .addTraceRuleSets(null)
                        .externalValues(Map.of())
                        .build();

        List<StaticExtractResult> results = runner.extract();

        assertEquals(List.of(tempDir.resolve("src/main/java").toAbsolutePath().normalize()), runner.sources());
        assertEquals(List.of(classesDirectory.toAbsolutePath().normalize()), runner.classes());
        assertEquals(List.of(dependencyDirectory.toAbsolutePath().normalize()), runner.dependencies());
        assertEquals(1, results.size());
    }

    @Test
    void requiresProjectOrSource() {
        assertThrows(
                IllegalStateException.class,
                () -> JavaStaticExtractProjectRunner.builder()
                        .classpathRules(false)
                        .classpathTraceRules(false)
                        .build());
    }

    private StaticExtractRule rule() {
        return new AntlrSerRuleParser()
                .parse(
                        """
                        rule "Spring Mapping"
                        endpoint HTTP inbound
                        find method with annotation @GetMapping

                        let path =
                          from annotation on method @GetMapping take attr(value)

                        build {
                          path: path
                        }
                        """);
    }

    private String source() {
        return """
                package com.example;

                @interface GetMapping {
                    String value();
                }

                class Client {
                    @GetMapping("/users")
                    String users() {
                        return "ok";
                    }
                }
                """;
    }

    private void writeEmptyJar(Path jarFile) throws IOException {
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jarFile))) {
            out.putNextEntry(new JarEntry("META-INF/"));
            out.closeEntry();
        }
    }
}
