package com.poseidon.javastatic.extract.jdt.build;

import com.poseidon.javastatic.extract.build.BuildAction;
import com.poseidon.javastatic.extract.build.BuildActionKind;
import com.poseidon.javastatic.extract.build.BuildExpression;
import com.poseidon.javastatic.extract.build.BuildSpec;
import com.poseidon.javastatic.extract.build.NormalizeKind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JdtBuildEvaluator {

    public List<Map<String, String>> evaluate(BuildSpec build, Map<String, List<String>> values) {
        if (build == null || build.fields() == null || build.fields().isEmpty()) {
            return List.of();
        }
        List<Map<String, String>> rows = new ArrayList<>();
        rows.add(new LinkedHashMap<>());
        for (Map.Entry<String, BuildExpression> field : build.fields().entrySet()) {
            List<String> fieldValues = evaluateExpression(field.getValue(), values);
            if (fieldValues.isEmpty()) {
                fieldValues = List.of("");
            }
            List<Map<String, String>> next = new ArrayList<>();
            for (Map<String, String> row : rows) {
                for (String value : fieldValues) {
                    Map<String, String> copy = new LinkedHashMap<>(row);
                    copy.put(field.getKey(), value);
                    next.add(copy);
                }
            }
            rows = next;
        }
        return rows;
    }

    private List<String> evaluateExpression(BuildExpression expression, Map<String, List<String>> values) {
        if (expression == null) {
            return List.of("");
        }
        List<String> rawValues;
        if (expression.concat() != null) {
            rawValues = evaluateConcat(expression.concat(), values);
        } else if (expression.reference() != null) {
            rawValues = values.getOrDefault(expression.reference(), List.of(""));
        } else {
            rawValues = List.of(expression.constValue() != null ? expression.constValue() : "");
        }
        List<String> result = new ArrayList<>();
        for (String value : rawValues) {
            result.add(applyActions(value, expression.actions()));
        }
        return result;
    }

    private List<String> evaluateConcat(List<String> parts, Map<String, List<String>> values) {
        List<String> result = new ArrayList<>();
        result.add("");
        for (String part : parts) {
            List<String> partValues = values.containsKey(part) ? values.get(part) : List.of(part);
            List<String> next = new ArrayList<>();
            for (String prefix : result) {
                for (String value : partValues) {
                    next.add(prefix + value);
                }
            }
            result = next;
        }
        return result;
    }

    private String applyActions(String input, List<BuildAction> actions) {
        String value = input != null ? input : "";
        if (actions == null) {
            return value;
        }
        for (BuildAction action : actions) {
            value = applyAction(value, action);
        }
        return value;
    }

    private String applyAction(String value, BuildAction action) {
        if (action == null || action.kind() == null) {
            return value;
        }
        return switch (action.kind()) {
            case NORMALIZE -> normalize(value, action.normalize());
            case REGEX -> regex(value, action.pattern(), action.group());
            case REPLACE -> replace(value, action.pattern(), action.replacement());
            case MAP -> map(value, action.mapping());
            case UPPER -> value.toUpperCase(Locale.ROOT);
            case LOWER -> value.toLowerCase(Locale.ROOT);
        };
    }

    private String normalize(String value, NormalizeKind kind) {
        if (kind == null) {
            return value;
        }
        return switch (kind) {
            case SLASH -> normalizeSlash(value);
            case PATH_VARIABLE -> normalizePathVariable(value);
            case EXTRACT_PATH -> extractPath(value);
            case PLACEHOLDER_LOOKUP -> placeholderLookup(value);
            case PLACEHOLDER_DEFAULT -> placeholderDefault(value);
            case KEBAB -> kebab(value);
        };
    }

    private String regex(String value, String pattern, Integer group) {
        if (pattern == null) {
            return "";
        }
        Matcher matcher = Pattern.compile(pattern).matcher(value);
        if (!matcher.find()) {
            return "";
        }
        int groupIndex = group != null ? group : 0;
        return groupIndex <= matcher.groupCount() ? matcher.group(groupIndex) : "";
    }

    private String replace(String value, String pattern, String replacement) {
        if (pattern == null) {
            return value;
        }
        return value.replaceAll(pattern, replacement != null ? replacement : "");
    }

    private String map(String value, Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return value;
        }
        return mapping.getOrDefault(value, value);
    }

    private String normalizeSlash(String value) {
        return value.trim().replace('\\', '/').replaceAll("/+", "/");
    }

    private String normalizePathVariable(String value) {
        return normalizeSlash(value)
                .replaceAll(":([A-Za-z_$][\\w$]*)", "{param}")
                .replaceAll("\\$\\{[^}]+}", "{param}")
                .replaceAll("\\{[^}/]+}", "{param}");
    }

    private String extractPath(String value) {
        String path = value.replaceFirst("(?i)^https?://[^/]+", "");
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        return normalizeSlash(path);
    }

    private String placeholderLookup(String value) {
        Matcher matcher = Pattern.compile("^\\$\\{([^}:]+)(?::.*)?}$").matcher(value);
        return matcher.matches() ? matcher.group(1) : value;
    }

    private String placeholderDefault(String value) {
        Matcher matcher = Pattern.compile("^\\$\\{[^}:]+:(.*)}$").matcher(value);
        return matcher.matches() ? matcher.group(1) : "";
    }

    private String kebab(String value) {
        return value
                .replaceAll("([a-z0-9])([A-Z])", "$1-$2")
                .replaceAll("[_\\s]+", "-")
                .toLowerCase(Locale.ROOT);
    }
}
