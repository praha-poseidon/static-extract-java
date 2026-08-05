package com.poseidon.javastatic.extract.build;

import java.util.Map;

public record BuildAction(
        BuildActionKind kind,
        String pattern,
        Integer group,
        String replacement,
        NormalizeKind normalize,
        Map<String, String> mapping) {}
