package com.poseidon.javastatic.extract.assistant;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Full project extract request.
 *
 * <p>Rules come from files, directories, and/or in-memory {@code ruleSources}
 * (one SER document per string). {@code externalValues} is the trace dictionary, per call.
 */
public record RunRequest(
        Path project,
        List<Path> sources,
        List<Path> classes,
        List<Path> dependencies,
        List<Path> ruleFiles,
        List<Path> ruleDirectories,
        List<String> ruleSources,
        Path outputFile,
        Map<String, Map<String, List<String>>> externalValues) {}
