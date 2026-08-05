package com.poseidon.javastatic.extract.rule;

import com.poseidon.javastatic.extract.source.AnnotationSelector;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import com.poseidon.javastatic.extract.source.MethodSelector;

/**
 * Find target plus optional filters.
 *
 * <p>{@code annotation} applies to the find target (method/field/class).
 * {@code className} / {@code classAnnotation} narrow to an enclosing type
 * (e.g. only methods on class {@code UserController} or types with
 * {@code @RestController}).
 */
public record FindSpec(
        JavaElementKind target,
        String targetKind,
        String name,
        AnnotationSelector annotation,
        MethodSelector method,
        String className,
        AnnotationSelector classAnnotation) {

    public FindSpec(
            JavaElementKind target,
            String name,
            AnnotationSelector annotation,
            MethodSelector method) {
        this(
                target,
                target != null ? target.name().toLowerCase() : null,
                name,
                annotation,
                method,
                null,
                null);
    }

    public FindSpec(
            JavaElementKind target,
            String targetKind,
            String name,
            AnnotationSelector annotation,
            MethodSelector method) {
        this(target, targetKind, name, annotation, method, null, null);
    }

    public FindSpec withFilters(
            AnnotationSelector annotation,
            String className,
            AnnotationSelector classAnnotation) {
        return new FindSpec(target, targetKind, name, annotation, method, className, classAnnotation);
    }
}
