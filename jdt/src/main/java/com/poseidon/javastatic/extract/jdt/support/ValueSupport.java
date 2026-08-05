package com.poseidon.javastatic.extract.jdt.support;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

public final class ValueSupport {

    private ValueSupport() {}

    public static List<String> dedupe(List<String> in) {
        return new ArrayList<>(new LinkedHashSet<>(in));
    }

    public static List<String> applyMapping(List<String> values, Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return values;
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            out.add(mapping.getOrDefault(value, value));
        }
        return out;
    }
}
