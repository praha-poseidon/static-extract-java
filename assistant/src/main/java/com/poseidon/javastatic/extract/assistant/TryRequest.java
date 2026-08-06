package com.poseidon.javastatic.extract.assistant;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record TryRequest(
        Path project,
        List<Path> files,
        List<Path> ruleFiles,
        List<Path> ruleDirectories,
        List<String> ruleSources,
        Map<String, Map<String, List<String>>> externalValues) {}
