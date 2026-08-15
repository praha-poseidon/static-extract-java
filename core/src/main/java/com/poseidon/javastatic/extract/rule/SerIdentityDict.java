package com.poseidon.javastatic.extract.rule;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SER document identity dictionary block (part of the rule text, not a separate UI field).
 *
 * <pre>
 * rule "..."
 * ...
 * build { ... }
 *
 * dict {
 *   com.foo.Bar.send() = cooper_domain_event_test
 *   com.foo.Bar.other() = /v1/path
 * }
 * </pre>
 *
 * Lines: {@code key = value} or {@code "key" = "value"}. Comments {@code #} allowed.
 */
public final class SerIdentityDict {

    private static final Pattern DICT_BLOCK = Pattern.compile(
            "(?is)\\bdict\\s*\\{([^}]*)\\}\\s*$");

    private SerIdentityDict() {}

    public record Split(String serBody, Map<String, String> identity) {
        public Split {
            if (serBody == null) {
                serBody = "";
            }
            if (identity == null) {
                identity = Map.of();
            } else {
                identity = Map.copyOf(identity);
            }
        }

        public boolean hasIdentity() {
            return !identity.isEmpty();
        }
    }

    /** Strip trailing {@code dict { ... }} and parse flat identity map. */
    public static Split split(String source) {
        if (source == null || source.isBlank()) {
            return new Split("", Map.of());
        }
        Matcher m = DICT_BLOCK.matcher(source);
        if (!m.find()) {
            return new Split(source, Map.of());
        }
        String body = source.substring(0, m.start()).stripTrailing();
        Map<String, String> identity = parseBody(m.group(1));
        return new Split(body, identity);
    }

    static Map<String, String> parseBody(String body) {
        Map<String, String> out = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return out;
        }
        for (String rawLine : body.split("\\R")) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            int spaced = line.indexOf(" = ");
            int eq;
            int sepLen;
            if (spaced > 0) {
                eq = spaced;
                sepLen = 3;
            } else {
                eq = line.indexOf('=');
                sepLen = 1;
            }
            if (eq <= 0) {
                throw new IllegalArgumentException("dict line must be key = value: " + rawLine);
            }
            String key = unquote(line.substring(0, eq).trim());
            String value = unquote(line.substring(eq + sepLen).trim());
            if (key.isEmpty() || value.isEmpty()) {
                throw new IllegalArgumentException("dict key/value empty: " + rawLine);
            }
            out.put(key, value);
        }
        return out;
    }

    private static String unquote(String s) {
        if (s == null) {
            return "";
        }
        s = s.trim();
        if (s.length() >= 2) {
            char a = s.charAt(0);
            char b = s.charAt(s.length() - 1);
            if ((a == '"' && b == '"') || (a == '\'' && b == '\'')) {
                return s.substring(1, s.length() - 1).trim();
            }
        }
        return s;
    }
}
