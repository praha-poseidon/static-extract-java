package com.poseidon.javastatic.extract.assistant;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public record DiagnosticRequest(
        Path project,
        List<Path> files,
        List<Path> ruleFiles,
        List<Path> ruleDirectories,
        List<Path> traceRuleFiles,
        List<Path> traceRuleDirectories,
        boolean builtinRules,
        Map<String, Map<String, List<String>>> externalValues) {}
