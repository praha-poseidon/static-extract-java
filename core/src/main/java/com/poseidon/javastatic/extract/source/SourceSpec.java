package com.poseidon.javastatic.extract.source;

public record SourceSpec(
        JavaElementKind element,
        String elementKind,
        JavaElementKind on,
        String onKind,
        String name,
        String literalValue,
        AnnotationSelector annotation,
        MethodSelector method,
        Integer argumentIndex,
        TakeSpec take) {

    public SourceSpec(
            JavaElementKind element,
            JavaElementKind on,
            String name,
            String literalValue,
            AnnotationSelector annotation,
            MethodSelector method,
            Integer argumentIndex,
            TakeSpec take) {
        this(
                element,
                element != null ? element.name().toLowerCase() : null,
                on,
                on != null ? on.name().toLowerCase() : null,
                name,
                literalValue,
                annotation,
                method,
                argumentIndex,
                take);
    }
}
