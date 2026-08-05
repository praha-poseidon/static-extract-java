package com.poseidon.javastatic.extract.cli;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.poseidon.javastatic.extract.assistant.DiagnosticRequest;
import com.poseidon.javastatic.extract.assistant.JavaStaticExtractAssistant;
import com.poseidon.javastatic.extract.assistant.RunRequest;
import com.poseidon.javastatic.extract.assistant.TryRequest;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

@Command(
        name = "static-extract-java",
        mixinStandardHelpOptions = true,
        version = "0.0.1",
        description = "Run Static Extract Java extractor SER rules.",
        subcommands = {
                JavaStaticExtractCli.InitCommand.class,
                JavaStaticExtractCli.TryCommand.class,
                JavaStaticExtractCli.DiagnoseCommand.class,
                JavaStaticExtractCli.RunCommand.class
        })
public final class JavaStaticExtractCli implements Callable<Integer> {

    private final JavaStaticExtractAssistant assistant;
    private final ObjectMapper objectMapper;

    public JavaStaticExtractCli() {
        this(new JavaStaticExtractAssistant(), new ObjectMapper());
    }

    JavaStaticExtractCli(JavaStaticExtractAssistant assistant, ObjectMapper objectMapper) {
        this.assistant = assistant;
        this.objectMapper = objectMapper;
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JavaStaticExtractCli()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    @Command(name = "init", description = "Create the local .ser workspace directories.")
    static final class InitCommand extends BaseCommand {
        @Option(names = "--project", required = true, description = "Java project root.")
        Path project;

        @Override
        protected Object execute() {
            return assistant().init(project);
        }
    }

    @Command(name = "try", description = "Run SER rules against selected Java files.")
    static final class TryCommand extends ExtractionCommand {
        @Option(names = {"--source", "--file"}, required = true, description = "Java file to test. Can be repeated.")
        List<Path> files = new ArrayList<>();

        @Override
        protected Object execute() {
            return assistant().tryRule(new TryRequest(
                    project,
                    files,
                    ruleFiles,
                    ruleDirectories,
                    traceRuleFiles,
                    traceRuleDirectories,
                    builtin,
                    externalValues()));
        }
    }

    @Command(name = "diagnose", description = "Run rules and return source facts when no result is emitted.")
    static final class DiagnoseCommand extends ExtractionCommand {
        @Option(names = {"--source", "--file"}, required = true, description = "Java file to diagnose. Can be repeated.")
        List<Path> files = new ArrayList<>();

        @Override
        protected Object execute() {
            return assistant().diagnose(new DiagnosticRequest(
                    project,
                    files,
                    ruleFiles,
                    ruleDirectories,
                    traceRuleFiles,
                    traceRuleDirectories,
                    builtin,
                    externalValues()));
        }
    }

    @Command(name = "run", description = "Run SER rules against a project or explicit source paths.")
    static final class RunCommand extends ExtractionCommand {
        @Option(names = "--source", description = "Java source directory or file. Can be repeated.")
        List<Path> sources = new ArrayList<>();

        @Option(names = "--classes", description = "Compiled project classes directory. Can be repeated.")
        List<Path> classes = new ArrayList<>();

        @Option(names = "--dependency", description = "Dependency jar or directory containing jars. Can be repeated.")
        List<Path> dependencies = new ArrayList<>();

        @Option(names = "--out", description = "Optional JSONL output file.")
        Path outputFile;

        @Override
        protected Object execute() {
            return assistant().run(new RunRequest(
                    project,
                    sources,
                    classes,
                    dependencies,
                    ruleFiles,
                    ruleDirectories,
                    traceRuleFiles,
                    traceRuleDirectories,
                    builtin,
                    outputFile,
                    externalValues()));
        }
    }

    abstract static class ExtractionCommand extends BaseCommand {
        @Option(names = "--project", description = "Java project root. If present, common source/classes/dependency paths are discovered.")
        Path project;

        @Option(names = "--rule", description = "SER rule file. Can be repeated.")
        List<Path> ruleFiles = new ArrayList<>();

        @Option(names = {"--rule-dir", "--rules"}, description = "Directory containing .ser rule files. Can be repeated.")
        List<Path> ruleDirectories = new ArrayList<>();

        @Option(names = "--trace-rule", description = "SER trace rule file. Can be repeated.")
        List<Path> traceRuleFiles = new ArrayList<>();

        @Option(names = "--trace-rules", description = "Directory containing .ser trace rule files. Can be repeated.")
        List<Path> traceRuleDirectories = new ArrayList<>();

        @Option(names = "--builtin", description = "Load rules and trace rules from the classpath.")
        boolean builtin;

        @Option(names = "--external-values", description = "JSON file with external trace values.")
        Path externalValuesFile;

        Map<String, Map<String, List<String>>> externalValues() {
            if (externalValuesFile == null) {
                return Map.of();
            }
            try {
                return objectMapper().readValue(
                        Files.readString(externalValuesFile),
                        new TypeReference<Map<String, Map<String, List<String>>>>() {});
            } catch (IOException e) {
                throw new IllegalArgumentException("Failed to read external values JSON: " + externalValuesFile, e);
            }
        }
    }

    abstract static class BaseCommand implements Callable<Integer> {
        @CommandLine.ParentCommand
        private JavaStaticExtractCli cli;

        @Override
        public Integer call() {
            try {
                cli.printJson(execute());
                return 0;
            } catch (RuntimeException e) {
                cli.printError(e);
                return 1;
            }
        }

        protected abstract Object execute();

        protected JavaStaticExtractAssistant assistant() {
            return cli.assistant;
        }

        protected ObjectMapper objectMapper() {
            return cli.objectMapper;
        }
    }

    private void printJson(Object value) {
        PrintWriter out = new PrintWriter(System.out, true);
        try {
            out.println(objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(value));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write JSON output.", e);
        }
    }

    private void printError(Exception error) {
        PrintWriter err = new PrintWriter(System.err, true);
        try {
            err.println(objectMapper.writeValueAsString(Map.of(
                    "status", "ERROR",
                    "message", rootMessage(error))));
        } catch (IOException ignored) {
            err.println("{\"status\":\"ERROR\",\"message\":\"" + rootMessage(error) + "\"}");
        }
    }

    private String rootMessage(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getName() : current.getMessage();
    }
}
