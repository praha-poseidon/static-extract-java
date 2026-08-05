package com.poseidon.javastatic.extract.assistant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class SourceFactExtractor {

    private static final Pattern ANNOTATION = Pattern.compile("@([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)?)(?:\\s*\\(([^)]*)\\))?");
    private static final Pattern METHOD_CALL = Pattern.compile("([A-Za-z_$][\\w$]*(?:\\.[A-Za-z_$][\\w$]*)*)\\s*\\.\\s*([A-Za-z_$][\\w$]*)\\s*\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:\\\\.|[^\"\\\\])*)\"");

    SourceFacts extract(Path file) {
        try {
            String source = Files.readString(file, StandardCharsets.UTF_8);
            return new SourceFacts(
                    file.toAbsolutePath().normalize().toString(),
                    annotations(source),
                    annotationAttributes(source),
                    methodCalls(source),
                    pathLikeStrings(source));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Java source file: " + file, e);
        }
    }

    private List<String> annotations(String source) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = ANNOTATION.matcher(source);
        while (matcher.find()) {
            values.add(matcher.group(1));
        }
        return List.copyOf(values);
    }

    private Map<String, List<String>> annotationAttributes(String source) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        Matcher matcher = ANNOTATION.matcher(source);
        while (matcher.find()) {
            String body = matcher.group(2);
            if (body != null && !body.isBlank()) {
                values.computeIfAbsent(matcher.group(1), ignored -> new ArrayList<>()).add(body.trim());
            }
        }
        values.replaceAll((ignored, list) -> List.copyOf(list));
        return Map.copyOf(values);
    }

    private List<String> methodCalls(String source) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = METHOD_CALL.matcher(source);
        while (matcher.find()) {
            values.add(matcher.group(1) + "." + matcher.group(2));
        }
        return List.copyOf(values);
    }

    private List<String> pathLikeStrings(String source) {
        Set<String> values = new LinkedHashSet<>();
        Matcher matcher = STRING_LITERAL.matcher(source);
        while (matcher.find()) {
            String value = matcher.group(1);
            if (value.startsWith("/") || value.startsWith("http://") || value.startsWith("https://")) {
                values.add(value);
            }
        }
        return List.copyOf(values);
    }
}
