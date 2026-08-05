package com.poseidon.javastatic.extract.source;

import java.util.List;
import java.util.Map;

/**
 * A named value extracted by ordered sources.
 *
 * <p>Sources are tried in declaration order. The first source that yields a
 * value wins. If no source yields a value, {@code defaultValue} is used.
 */
public record LetSpec(
        String name,
        List<SourceSpec> sources,
        String defaultValue,
        Map<String, String> mapping) {}
