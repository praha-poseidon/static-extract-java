package com.poseidon.javastatic.extract.assistant;

import com.poseidon.javastatic.extract.extractor.ExtractedFact;

import java.util.List;

public record RunReport(
        String status,
        String projectRoot,
        List<String> ruleInputs,
        int resultCount,
        List<ExtractedFact> results,
        String outputFile) {}
