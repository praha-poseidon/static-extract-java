package com.poseidon.javastatic.extract.jdt.load;

import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SerRuleLoaderTest {

    @Test
    void loadAllShipsNoBuiltinRules() {
        assertEquals(List.of(), new SerRuleLoader().loadAll());
    }

    @Test
    void loadsApplicationRulesFromFixedResourceDirectory(@TempDir Path tempDir) throws Exception {
        write(
                tempDir.resolve("static-extract/rules/index.txt"),
                """
                custom/http.ser
                """);
        write(tempDir.resolve("static-extract/rules/custom/http.ser"), minimalRule("Custom HTTP Rule"));

        try (URLClassLoader classLoader =
                new URLClassLoader(new java.net.URL[] {tempDir.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            List<StaticExtractRule> rules = new SerRuleLoader(classLoader, new com.poseidon.javastatic.extract.language.AntlrSerRuleParser())
                    .loadApplicationRules();

            assertEquals(List.of("Custom HTTP Rule"), rules.stream().map(StaticExtractRule::name).toList());
        }
    }

    @Test
    void scansRuleDirectoryWhenRulesAreProvidedAsFiles(@TempDir Path tempDir) throws Exception {
        write(tempDir.resolve("a.ser"), minimalRule("A Rule"));
        write(tempDir.resolve("nested/b.ser"), minimalRule("B Rule"));
        write(tempDir.resolve("ignore.txt"), "not a rule");

        List<StaticExtractRule> rules = new SerRuleLoader().loadRulesFromDirectory(tempDir);

        assertEquals(List.of("A Rule", "B Rule"), rules.stream().map(StaticExtractRule::name).toList());
    }

    @Test
    void loadsExplicitFilesAndHandlesEmptyInputs(@TempDir Path tempDir) throws Exception {
        Path ruleFile = tempDir.resolve("rule.ser");
        write(ruleFile, minimalRule("File Rule"));
        SerRuleLoader loader = new SerRuleLoader();

        assertEquals(List.of(), loader.loadRulesFromFiles(null));
        assertEquals(List.of(), loader.loadRulesFromDirectory(tempDir.resolve("missing")));
        assertEquals("File Rule", loader.loadRulesFromFiles(List.of(ruleFile)).get(0).name());
    }

    @Test
    void loadsRulesFromInMemorySources() {
        SerRuleLoader loader = new SerRuleLoader();

        assertEquals(List.of(), loader.loadRulesFromSources(null));
        assertEquals(List.of(), loader.loadRulesFromSources(List.of("  ", "")));
        assertEquals(
                "Inline Rule",
                loader.loadRulesFromSources(List.of(minimalRule("Inline Rule"))).get(0).name());
    }

    @Test
    void loadsRuleWithEmbeddedTraceBlock(@TempDir Path tempDir) throws Exception {
        Path file = tempDir.resolve("combined.ser");
        write(
                file,
                """
                rule "Combined Rule"
                endpoint CUSTOM inbound
                find class

                build {
                  kind: "CUSTOM"
                }

                trace {
                  from field
                  when annotation @Value on field

                  let rawValue =
                    from annotation @Value on field take attr(value)

                  build {
                    namespace: "config"
                    lookup: rawValue | normalize placeholderLookup
                  }
                }
                """);

        StaticExtractRule rule = new SerRuleLoader().loadRulesFromFiles(List.of(file)).get(0);
        assertEquals("Combined Rule", rule.name());
        assertNotNull(rule.embeddedTrace());
        assertEquals(1, rule.embeddedTrace().externalEntries().size());
    }

    @Test
    void rejectsInvalidDirectoriesAndMissingIndexedResources(@TempDir Path tempDir) throws Exception {
        Path notDirectory = tempDir.resolve("rules.ser");
        Files.writeString(notDirectory, minimalRule("Not Directory"));
        assertThrows(IllegalArgumentException.class, () -> new SerRuleLoader().loadRulesFromDirectory(notDirectory));

        write(tempDir.resolve("static-extract/rules/index.txt"), "missing.ser");
        try (URLClassLoader classLoader =
                new URLClassLoader(new java.net.URL[] {tempDir.toUri().toURL()}, ClassLoader.getPlatformClassLoader())) {
            SerRuleLoader loader = new SerRuleLoader(classLoader, new com.poseidon.javastatic.extract.language.AntlrSerRuleParser());
            assertThrows(IllegalStateException.class, loader::loadApplicationRules);
        }
    }

    private String minimalRule(String name) {
        return """
                rule "%s"
                endpoint CUSTOM inbound
                find class

                build {
                  kind: "CUSTOM"
                }
                """
                .formatted(name);
    }

    private void write(Path file, String content) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
