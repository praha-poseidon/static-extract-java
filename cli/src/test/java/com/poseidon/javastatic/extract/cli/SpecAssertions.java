package com.poseidon.javastatic.extract.cli;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class SpecAssertions {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private SpecAssertions() {}

    static void assertJsonLinesMatchExtractedFactSpec(Path outputFile) throws Exception {
        JsonNode schema = OBJECT_MAPPER.readTree(
                Files.readString(findSpecRoot().resolve("schema/extracted-fact.schema.json")));
        Set<String> required = OBJECT_MAPPER.convertValue(
                schema.get("required"),
                OBJECT_MAPPER.getTypeFactory().constructCollectionType(Set.class, String.class));
        Map<String, JsonNode> properties = OBJECT_MAPPER.convertValue(
                schema.get("properties"),
                OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, JsonNode.class));

        for (String line : Files.readAllLines(outputFile)) {
            JsonNode record = OBJECT_MAPPER.readTree(line);
            assertEquals("object", record.isObject() ? "object" : record.getNodeType().name().toLowerCase());
            for (String name : required) {
                assertTrue(record.has(name), "Missing required spec field: " + name);
            }
            Iterator<String> fieldNames = record.fieldNames();
            while (fieldNames.hasNext()) {
                String name = fieldNames.next();
                assertTrue(properties.containsKey(name), "Unexpected field not declared by spec: " + name);
                assertMatchesSpecType(name, record.get(name), properties.get(name).get("type"));
            }
        }
    }

    /** Root of the static-extract-java project (contains pom.xml + core/). */
    static Path findProjectRoot() {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("core"))
                    && Files.isDirectory(current.resolve("cli"))) {
                return current;
            }
            // legacy monorepo layout: .../static-extract with java/core
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("java/core"))
                    && Files.isDirectory(current.resolve("spec"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("static-extract-java project root not found.");
    }

    /**
     * Root of static-extract-spec (contains ser/Ser.g4).
     * Resolution order: STATIC_EXTRACT_SPEC env → sibling ../static-extract-spec →
     * legacy monorepo ./spec under project root.
     */
    static Path findSpecRoot() {
        String env = System.getenv("STATIC_EXTRACT_SPEC");
        if (env != null && !env.isBlank()) {
            Path fromEnv = Path.of(env).toAbsolutePath().normalize();
            if (Files.isRegularFile(fromEnv.resolve("ser/Ser.g4"))) {
                return fromEnv;
            }
            throw new IllegalStateException("STATIC_EXTRACT_SPEC does not look like a spec root: " + fromEnv);
        }

        Path projectRoot = findProjectRoot();
        Path sibling = projectRoot.resolve("../static-extract-spec").normalize();
        if (Files.isRegularFile(sibling.resolve("ser/Ser.g4"))) {
            return sibling;
        }

        Path nested = projectRoot.resolve("spec");
        if (Files.isRegularFile(nested.resolve("ser/Ser.g4"))) {
            return nested;
        }

        throw new IllegalStateException(
                "static-extract-spec not found. Expected sibling ../static-extract-spec or set STATIC_EXTRACT_SPEC.");
    }

    private static void assertMatchesSpecType(String name, JsonNode value, JsonNode typeSpec) {
        assertNotNull(typeSpec, "Missing schema type for field: " + name);
        if (typeSpec.isTextual()) {
            assertMatchesType(name, value, typeSpec.asText());
            return;
        }
        if (typeSpec.isArray()) {
            for (JsonNode allowedType : typeSpec) {
                if (matchesType(value, allowedType.asText())) {
                    return;
                }
            }
            fail("Field does not match any allowed spec type: " + name);
        }
    }

    private static void assertMatchesType(String name, JsonNode value, String type) {
        assertTrue(matchesType(value, type), "Field does not match spec type " + type + ": " + name);
    }

    private static boolean matchesType(JsonNode value, String type) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "object" -> value.isObject();
            case "null" -> value.isNull();
            default -> false;
        };
    }
}
