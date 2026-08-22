package com.poseidon.javastatic.extract.jdt.trace.external;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * External values for static-extract.
 *
 * <p>Identity dict (redesign): flat JSON only:
 * <pre>
 * {
 *   "com.foo.Bar.send()": "cooper_domain_event_test"
 * }
 * </pre>
 *
 * <p>The flat identity JSON format is only used by {@link #loadJson(String)}. The engine wire
 * constructor also carries ordinary trace namespaces such as {@code config}; those values must
 * remain available to SER {@code trace { }} rules.
 */
public class MapExternalValueResolver implements ExternalValueResolver, IdentityDictResolver {

    public static final String IDENTITY_NS = "identity";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Map<String, Map<String, List<String>>> values;
    private final Map<String, String> identity;

    /** Engine wire: preserve all namespaces used by trace rules and expose identity separately. */
    public MapExternalValueResolver(Map<String, Map<String, List<String>>> multiNamespace) {
        this.values = copyWire(multiNamespace);
        this.identity = fromWire(values);
    }

    private MapExternalValueResolver(Map<String, String> identity, boolean ignored) {
        this.identity =
                identity == null || identity.isEmpty()
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(identity));
        this.values = toWire(this.identity);
    }

    public static MapExternalValueResolver ofIdentity(Map<String, String> identity) {
        return new MapExternalValueResolver(identity, true);
    }

    public static MapExternalValueResolver loadJson(String pathOrJson) {
        if (pathOrJson == null || pathOrJson.isBlank()) {
            return ofIdentity(Map.of());
        }
        try {
            String raw = pathOrJson.trim();
            if (!raw.startsWith("{")) {
                raw = Files.readString(Path.of(raw));
            }
            return ofIdentity(parseFlatIdentityJson(raw));
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse identity dict (flat {\"fqcn.method()\": \"value\"}): " + pathOrJson, e);
        }
    }

    public static Map<String, String> parseFlatIdentityJson(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        Map<String, Object> raw = MAPPER.readValue(json.trim(), new TypeReference<>() {});
        if (raw.isEmpty()) {
            return Map.of();
        }
        if (raw.containsKey("endpointPathOverrides") || raw.containsKey("config")) {
            throw new IllegalArgumentException(
                    "Identity dict must be flat {\"fqcn.method()\": \"value\"}; "
                            + "endpointPathOverrides/config are not supported");
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : raw.entrySet()) {
            if (e.getKey() == null || e.getKey().isBlank() || e.getValue() == null) {
                continue;
            }
            if (e.getValue() instanceof Map<?, ?> || e.getValue() instanceof List<?>) {
                throw new IllegalArgumentException(
                        "Identity values must be strings: key=" + e.getKey());
            }
            String s = e.getValue().toString().trim();
            if (!s.isEmpty()) {
                out.put(e.getKey().trim(), s);
            }
        }
        return out;
    }

    private static Map<String, String> fromWire(Map<String, Map<String, List<String>>> multi) {
        if (multi == null || multi.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> table = multi.get(IDENTITY_NS);
        if (table == null || table.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : table.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isEmpty()) {
                continue;
            }
            String v = e.getValue().get(0);
            if (v != null && !v.isBlank()) {
                out.put(e.getKey().trim(), v.trim());
            }
        }
        return out;
    }

    /** Flat identity → engine wire (identity namespace, one-element lists). */
    public static Map<String, Map<String, List<String>>> toWire(Map<String, String> flat) {
        if (flat == null || flat.isEmpty()) {
            return Map.of();
        }
        Map<String, List<String>> table = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : flat.entrySet()) {
            if (e.getKey() == null || e.getValue() == null || e.getValue().isBlank()) {
                continue;
            }
            table.put(e.getKey().trim(), List.of(e.getValue().trim()));
        }
        return table.isEmpty() ? Map.of() : Map.of(IDENTITY_NS, table);
    }

    @Override
    public List<String> resolve(String namespace, String key) {
        if (key == null || key.isBlank()) {
            return List.of();
        }
        String resolvedNamespace = namespace == null || namespace.isBlank() ? IDENTITY_NS : namespace.trim();
        Map<String, List<String>> table = values.get(resolvedNamespace);
        if (table == null) {
            return List.of();
        }
        List<String> resolved = table.get(key.trim());
        return resolved == null ? List.of() : resolved;
    }

    @Override
    public String resolveIdentity(String methodKey) {
        if (methodKey == null || methodKey.isBlank()) {
            return null;
        }
        String v = identity.get(methodKey.trim());
        return v == null || v.isBlank() ? null : v;
    }

    public Map<String, String> identityMap() {
        return identity;
    }

    private static Map<String, Map<String, List<String>>> copyWire(
            Map<String, Map<String, List<String>>> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, List<String>>> copied = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, List<String>>> namespace : source.entrySet()) {
            if (namespace.getKey() == null || namespace.getKey().isBlank()
                    || namespace.getValue() == null || namespace.getValue().isEmpty()) {
                continue;
            }
            Map<String, List<String>> table = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : namespace.getValue().entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                List<String> nonBlank = entry.getValue().stream()
                        .filter(value -> value != null && !value.isBlank())
                        .map(String::trim)
                        .toList();
                if (!nonBlank.isEmpty()) {
                    table.put(entry.getKey().trim(), nonBlank);
                }
            }
            if (!table.isEmpty()) {
                copied.put(namespace.getKey().trim(), Collections.unmodifiableMap(table));
            }
        }
        return copied.isEmpty() ? Map.of() : Collections.unmodifiableMap(copied);
    }
}
