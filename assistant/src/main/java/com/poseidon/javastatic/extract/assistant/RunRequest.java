package com.poseidon.javastatic.extract.assistant;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record RunRequest(
        Path project,
        List<Path> sources,
        List<Path> classes,
        List<Path> dependencies,
        List<Path> ruleFiles,
        List<Path> ruleDirectories,
        List<Path> traceRuleFiles,
        List<Path> traceRuleDirectories,
        boolean builtinRules,
        Path outputFile,
        Map<String, Map<String, List<String>>> externalValues) {}
