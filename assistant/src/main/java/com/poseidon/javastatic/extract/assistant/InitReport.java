package com.poseidon.javastatic.extract.assistant;

public record InitReport(
        String projectRoot,
        String generatedRulesDir,
        String reportDir,
        String resultDir) {}
