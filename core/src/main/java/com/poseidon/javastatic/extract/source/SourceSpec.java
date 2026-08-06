package com.poseidon.javastatic.extract.source;

/**
 * Where to take a value from, relative to the find anchor.
 *
 * <p>Optional fluent-chain navigation (Java method call chains):
 * {@code chainOffset} steps prev (negative) or next (positive) along
 * {@code a.b().c()} style {@code MethodInvocation} links;
 * {@code chainCallName} jumps to the nearest call with that method name.
 */
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
        Integer chainOffset,
        String chainCallName,
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
                null,
                null,
                take);
    }

    public SourceSpec(
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
        this(
                element,
                elementKind,
                on,
                onKind,
                name,
                literalValue,
                annotation,
                method,
                argumentIndex,
                null,
                null,
                take);
    }

    public SourceSpec withChain(Integer chainOffset, String chainCallName) {
        return new SourceSpec(
                element,
                elementKind,
                on,
                onKind,
                name,
                literalValue,
                annotation,
                method,
                argumentIndex,
                chainOffset,
                chainCallName,
                take);
    }
}
