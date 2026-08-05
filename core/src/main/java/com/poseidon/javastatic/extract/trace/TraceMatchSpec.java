package com.poseidon.javastatic.extract.trace;

import com.poseidon.javastatic.extract.source.AnnotationSelector;
import com.poseidon.javastatic.extract.source.MethodSelector;

public record TraceMatchSpec(
        AnnotationSelector annotation,
        MethodSelector method,
        String fieldName,
        String fieldType,
        String parameterName,
        String parameterType,
        String methodName,
        String callName,
        String callOwner,
        String assignmentField) {}
