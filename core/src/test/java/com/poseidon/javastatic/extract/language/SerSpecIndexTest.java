package com.poseidon.javastatic.extract.language;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SerSpecIndexTest {

    @Test
    void specReadmeIndexesSerSpecAndMachineContracts() throws Exception {
        Path root = specRoot();
        String readme = Files.readString(root.resolve("README.md"));

        assertTrue(readme.contains("ser/Ser.g4") || readme.contains("`ser/Ser.g4`") || readme.contains("Ser.g4"));
        assertTrue(Files.isRegularFile(root.resolve("ser/Ser.g4")));
        assertTrue(Files.isRegularFile(root.resolve("ser/SER_SPEC.md")));
        assertTrue(Files.isRegularFile(root.resolve("schema/extracted-fact.schema.json")));
        assertTrue(Files.isRegularFile(root.resolve("cli/extractor-cli.md")));
    }

    private Path specRoot() {
        String env = System.getenv("STATIC_EXTRACT_SPEC");
        if (env != null && !env.isBlank()) {
            Path fromEnv = Path.of(env).toAbsolutePath().normalize();
            if (Files.isRegularFile(fromEnv.resolve("ser/Ser.g4"))) {
                return fromEnv;
            }
        }

        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            Path sibling = current.resolve("../static-extract-spec").normalize();
            if (Files.isRegularFile(sibling.resolve("ser/Ser.g4"))) {
                return sibling;
            }
            if (Files.isRegularFile(current.resolve("ser/Ser.g4"))
                    && Files.isRegularFile(current.resolve("README.md"))) {
                return current;
            }
            if (Files.isRegularFile(current.resolve("spec/ser/Ser.g4"))) {
                return current.resolve("spec");
            }
            current = current.getParent();
        }
        throw new IllegalStateException("static-extract-spec root was not found.");
    }
}
