package com.poseidon.javastatic.extract.source;

import java.util.List;

public record MethodSelector(
        String ownerType,
        String ownerTypePattern,
        List<String> names,
        String namePattern) {}
