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

    private static final Pattern DICT_START = Pattern.compile("(?im)^\\s*dict\\s*\\{");

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
        Matcher matcher = DICT_START.matcher(source);
        int blockStart = -1;
        int openBrace = -1;
        int closeBrace = -1;
        while (matcher.find()) {
            int candidateOpen = source.indexOf('{', matcher.start());
            int candidateClose = matchingBrace(source, candidateOpen);
            if (candidateClose >= 0 && source.substring(candidateClose + 1).isBlank()) {
                blockStart = matcher.start();
                openBrace = candidateOpen;
                closeBrace = candidateClose;
            }
        }
        if (blockStart < 0) {
            return new Split(source, Map.of());
        }
        String body = source.substring(0, blockStart).stripTrailing();
        Map<String, String> identity = parseBody(source.substring(openBrace + 1, closeBrace));
        return new Split(body, identity);
    }

    private static int matchingBrace(String source, int openBrace) {
        if (openBrace < 0) {
            return -1;
        }
        int depth = 0;
        char quote = 0;
        boolean escaped = false;
        for (int i = openBrace; i < source.length(); i++) {
            char current = source.charAt(i);
            if (quote != 0) {
                if (escaped) {
                    escaped = false;
                } else if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                }
                continue;
            }
            if (current == '\'' || current == '"') {
                quote = current;
            } else if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return i;
            }
        }
        return -1;
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
