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

    /**
     * Apply a SER {@code map { k: v }} table.
     *
     * <p>Empty/null mapping: values unchanged (no map block).
     * Non-empty mapping: only keys present in the table are kept (mapped to values).
     * Unmapped inputs are dropped (not passed through as the original token).
     */
    public static List<String> applyMapping(List<String> values, Map<String, String> mapping) {
        if (mapping == null || mapping.isEmpty()) {
            return values;
        }
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String mapped = mapping.get(value);
            if (mapped != null) {
                out.add(mapped);
            }
            // miss → empty (omit); do not pass through method names / raw tokens
        }
        return out;
    }
}

