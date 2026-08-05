package com.poseidon.javastatic.extract.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.poseidon.javastatic.extract.jdt.load.SerRuleLoader;
import com.poseidon.javastatic.extract.jdt.project.JavaStaticExtractProjectRunner;
import com.poseidon.javastatic.extract.extractor.ExtractedFact;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JavaStaticExtractAssistant {

    private final SerRuleLoader loader;
    private final ObjectMapper objectMapper;
    private final SourceFactExtractor sourceFactExtractor;

    public JavaStaticExtractAssistant() {
        this(new SerRuleLoader(), new ObjectMapper(), new SourceFactExtractor());
    }

    JavaStaticExtractAssistant(SerRuleLoader loader, ObjectMapper objectMapper, SourceFactExtractor sourceFactExtractor) {
        this.loader = loader;
        this.objectMapper = objectMapper;
        this.sourceFactExtractor = sourceFactExtractor;
    }

    public InitReport init(Path project) {
        Path root = normalize(project);
        Path generatedRules = root.resolve(".ser/generated");
        Path report = root.resolve(".ser/report");
        Path result = root.resolve(".ser/result");
        try {
            Files.createDirectories(generatedRules);
            Files.createDirectories(report);
            Files.createDirectories(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to initialize SER workspace: " + root, e);
        }
        return new InitReport(root.toString(), generatedRules.toString(), report.toString(), result.toString());
    }

    public TryReport tryRule(TryRequest request) {
        validateTryRequest(request);
        JavaStaticExtractProjectRunner.Builder builder = baseBuilder(
                request.project(),
                List.of(),
                List.of(),
                List.of(),
                request.ruleFiles(),
                request.ruleDirectories(),
                request.traceRuleFiles(),
                request.traceRuleDirectories(),
                request.builtinRules(),
                request.externalValues());
        List<Path> files = resolveInputPaths(request.project(), request.files());
        files.forEach(builder::source);
        List<ExtractedFact> results = builder.build().extractFacts();
        return new TryReport(
                "OK",
                normalizeOrNull(request.project()),
                strings(files),
                ruleInputs(request.ruleFiles(), request.ruleDirectories(), request.builtinRules()),
                results.size(),
                results);
    }

    public DiagnosticReport diagnose(DiagnosticRequest request) {
        TryReport tryReport = tryRule(new TryRequest(
                request.project(),
                request.files(),
                request.ruleFiles(),
                request.ruleDirectories(),
                request.traceRuleFiles(),
                request.traceRuleDirectories(),
                request.builtinRules(),
                request.externalValues()));
        List<SourceFacts> facts = safeList(request.files()).stream()
                .map(file -> resolveInputPath(request.project(), file))
                .map(sourceFactExtractor::extract)
                .toList();
        List<String> hints = tryReport.resultCount() > 0 ? List.of() : List.of(
                "No result was emitted. Compare the rule find clause with annotations and method calls in facts.",
                "If the rule uses framework type matching, pass project classes and dependency jars when running the full project.");
        return new DiagnosticReport(tryReport.resultCount() > 0 ? "SUCCESS" : "NO_MATCH", tryReport, facts, hints);
    }

    public RunReport run(RunRequest request) {
        validateRunRequest(request);
        JavaStaticExtractProjectRunner.Builder builder = baseBuilder(
                request.project(),
                request.sources(),
                request.classes(),
                request.dependencies(),
                request.ruleFiles(),
                request.ruleDirectories(),
                request.traceRuleFiles(),
                request.traceRuleDirectories(),
                request.builtinRules(),
                request.externalValues());
        List<ExtractedFact> results = builder.build().extractFacts();
        if (request.outputFile() != null) {
            writeJsonLines(request.outputFile(), results);
        }
        return new RunReport(
                "OK",
                normalizeOrNull(request.project()),
                ruleInputs(request.ruleFiles(), request.ruleDirectories(), request.builtinRules()),
                results.size(),
                results,
                request.outputFile() == null ? null : normalize(request.outputFile()).toString());
    }

    private JavaStaticExtractProjectRunner.Builder baseBuilder(
            Path project,
            List<Path> sources,
            List<Path> classes,
            List<Path> dependencies,
            List<Path> ruleFiles,
            List<Path> ruleDirectories,
            List<Path> traceRuleFiles,
            List<Path> traceRuleDirectories,
            boolean builtinRules,
            Map<String, Map<String, List<String>>> externalValues) {
        JavaStaticExtractProjectRunner.Builder builder = JavaStaticExtractProjectRunner.builder()
                .project(project)
                .classpathRules(builtinRules)
                .classpathTraceRules(builtinRules)
                .addRules(loader.loadRulesFromFiles(safeList(ruleFiles)))
                .addTraceRuleSets(loader.loadTraceRulesFromFiles(safeList(ruleFiles)))
                .addTraceRuleSets(loader.loadTraceRulesFromFiles(safeList(traceRuleFiles)))
                .externalValues(externalValues == null ? Map.of() : externalValues);
        safeList(sources).forEach(builder::source);
        safeList(classes).forEach(builder::classes);
        safeList(dependencies).forEach(builder::dependency);
        safeList(ruleDirectories).forEach(directory -> {
            builder.rulesFromDirectory(directory);
            builder.traceRulesFromDirectory(directory);
        });
        safeList(traceRuleDirectories).forEach(builder::traceRulesFromDirectory);
        return builder;
    }

    private void validateTryRequest(TryRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required.");
        }
        if (safeList(request.files()).isEmpty()) {
            throw new IllegalArgumentException("At least one Java source file is required.");
        }
        validateRules(request.ruleFiles(), request.ruleDirectories(), request.builtinRules());
    }

    private void validateRunRequest(RunRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Request is required.");
        }
        if (request.project() == null && safeList(request.sources()).isEmpty()) {
            throw new IllegalArgumentException("Pass a project directory or at least one source path.");
        }
        validateRules(request.ruleFiles(), request.ruleDirectories(), request.builtinRules());
    }

    private void validateRules(List<Path> ruleFiles, List<Path> ruleDirectories, boolean builtinRules) {
        if (!builtinRules && safeList(ruleFiles).isEmpty() && safeList(ruleDirectories).isEmpty()) {
            throw new IllegalArgumentException("Pass at least one SER rule file, rule directory, or enable builtin rules.");
        }
    }

    private void writeJsonLines(Path outputFile, List<ExtractedFact> results) {
        Path target = normalize(outputFile);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            List<String> lines = new ArrayList<>();
            for (ExtractedFact result : results) {
                lines.add(objectMapper.writeValueAsString(result));
            }
            Files.write(target, lines, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to write extraction results: " + target, e);
        }
    }

    private List<String> ruleInputs(List<Path> ruleFiles, List<Path> ruleDirectories, boolean builtinRules) {
        Set<String> inputs = new LinkedHashSet<>();
        if (builtinRules) {
            inputs.add("classpath:builtin");
        }
        strings(ruleFiles).forEach(inputs::add);
        strings(ruleDirectories).forEach(inputs::add);
        return List.copyOf(inputs);
    }

    private static List<String> strings(List<Path> paths) {
        return safeList(paths).stream().map(JavaStaticExtractAssistant::normalize).map(Path::toString).toList();
    }

    private static List<Path> resolveInputPaths(Path project, List<Path> paths) {
        return safeList(paths).stream().map(path -> resolveInputPath(project, path)).toList();
    }

    private static Path resolveInputPath(Path project, Path path) {
        if (path == null) {
            return null;
        }
        if (path.isAbsolute()) {
            return path.toAbsolutePath().normalize();
        }
        Path cwdRelative = path.toAbsolutePath().normalize();
        if (Files.exists(cwdRelative)) {
            return cwdRelative;
        }
        if (project != null) {
            Path projectRelative = normalize(project).resolve(path).normalize();
            if (Files.exists(projectRelative)) {
                return projectRelative;
            }
        }
        return cwdRelative;
    }

    private static String normalizeOrNull(Path path) {
        return path == null ? null : normalize(path).toString();
    }

    private static Path normalize(Path path) {
        return path.toAbsolutePath().normalize();
    }

    private static <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }
}
