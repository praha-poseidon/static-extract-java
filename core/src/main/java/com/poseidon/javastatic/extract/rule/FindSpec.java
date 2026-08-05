package com.poseidon.javastatic.extract.rule;

import com.poseidon.javastatic.extract.source.AnnotationSelector;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import com.poseidon.javastatic.extract.source.MethodSelector;

public record FindSpec(
        JavaElementKind target,
        String targetKind,
        String name,
        AnnotationSelector annotation,
        MethodSelector method) {

    public FindSpec(
            JavaElementKind target,
            String name,
            AnnotationSelector annotation,
            MethodSelector method) {
        this(target, target != null ? target.name().toLowerCase() : null, name, annotation, method);
    }
}
