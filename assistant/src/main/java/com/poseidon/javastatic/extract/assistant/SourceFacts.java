package com.poseidon.javastatic.extract.assistant;

import java.util.List;
import java.util.Map;

public record SourceFacts(
        String file,
        List<String> annotations,
        Map<String, List<String>> annotationAttributes,
        List<String> methodCalls,
        List<String> pathLikeStrings) {}
