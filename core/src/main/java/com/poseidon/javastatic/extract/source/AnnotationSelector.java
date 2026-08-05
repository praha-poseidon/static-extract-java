package com.poseidon.javastatic.extract.source;

import java.util.List;

public record AnnotationSelector(
        JavaElementKind on,
        List<String> names,
        String namePattern) {}
