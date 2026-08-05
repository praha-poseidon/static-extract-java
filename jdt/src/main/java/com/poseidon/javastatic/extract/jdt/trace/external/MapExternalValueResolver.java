package com.poseidon.javastatic.extract.jdt.trace.external;

import java.util.List;
import java.util.Map;

public class MapExternalValueResolver implements ExternalValueResolver {

    private final Map<String, Map<String, List<String>>> values;

    public MapExternalValueResolver(Map<String, Map<String, List<String>>> values) {
        this.values = values != null ? values : Map.of();
    }

    @Override
    public List<String> resolve(String namespace, String key) {
        Map<String, List<String>> namespaceValues = values.get(namespace);
        if (namespaceValues == null) {
            return List.of();
        }
        return namespaceValues.getOrDefault(key, List.of());
    }
}
