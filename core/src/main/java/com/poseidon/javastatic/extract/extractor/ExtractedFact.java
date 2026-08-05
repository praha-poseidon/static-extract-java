package com.poseidon.javastatic.extract.extractor;

import java.util.Map;

public record ExtractedFact(
        String rule,
        String factType,
        Map<String, String> classifiers,
        Map<String, String> fields,
        String projectFilePath,
        String absoluteFilePath,
        int startLine,
        int endLine,
        String enclosingSymbol) {}
