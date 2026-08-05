package com.poseidon.javastatic.extract.jdt.project;

import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.jdt.extractor.JavaStaticExtractRunner;
import com.poseidon.javastatic.extract.jdt.trace.external.ExternalValueResolver;
import com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceResolver;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.extractor.ExtractedFact;
import com.poseidon.javastatic.extract.extractor.StaticExtractExtractor;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Project-level entry point for static extraction.
 *
 * <p>The simplest usage is passing only {@link Builder#project(Path)}. The runner
 * will try common Maven and Gradle directories under that project:
 * {@code src/main/java}, {@code src/test/java}, {@code target/classes},
 * {@code target/test-classes}, {@code build/classes/java/main},
 * {@code build/classes/java/test}, {@code libs}, and {@code target/dependency}.
 *
 * <p>If no project is passed, callers must at least pass {@link Builder#source(Path)}.
 * {@code source} is the Java code to analyze. {@code classes} and
 * {@code dependency} are not always required, but rules that need to confirm
 * a method or field belongs to a specific Java type need them to be accurate.
 */
public final class JavaStaticExtractProjectRunner implements StaticExtractExtractor {

    private final Path project;
    private final List<Path> sources;
    private final List<Path> classes;
    private final List<Path> dependencies;
    private final Charset charset;
    private final JavaStaticExtractRunner runner;

    private JavaStaticExtractProjectRunner(
            Path project,
            List<Path> sources,
            List<Path> classes,
            List<Path> dependencies,
            Charset charset,
            JavaStaticExtractRunner runner) {
        this.project = normalize(project);
        this.sources = List.copyOf(sources);
        this.classes = List.copyOf(classes);
        this.dependencies = List.copyOf(dependencies);
        this.charset = charset;
        this.runner = runner;
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<StaticExtractResult> extract() {
        List<Path> javaFiles = javaFiles();
        List<String> classpath = classpathEntries();
        List<String> sourcepath = sourcepathEntries();
        List<StaticExtractResult> results = new ArrayList<>();
        for (Path javaFile : javaFiles) {
            CompilationUnit compilationUnit = parse(javaFile, classpath, sourcepath);
            results.addAll(runner.extract(
                    compilationUnit,
                    projectFilePath(javaFile),
                    javaFile.toAbsolutePath().normalize().toString()));
        }
        return results;
    }

    @Override
    public List<ExtractedFact> extractFacts() {
        return extract().stream().map(StaticExtractResult::toFact).toList();
    }

    public List<Path> sources() {
        return sources;
    }

    public List<Path> classes() {
        return classes;
    }

    public List<Path> dependencies() {
        return dependencies;
    }

    private CompilationUnit parse(Path javaFile, List<String> classpath, List<String> sourcepath) {
        try {
            String source = Files.readString(javaFile, charset);
            ASTParser parser = ASTParser.newParser(AST.getJLSLatest());
            parser.setKind(ASTParser.K_COMPILATION_UNIT);
            parser.setSource(source.toCharArray());
            parser.setUnitName(unitName(javaFile));
            parser.setResolveBindings(true);
            parser.setBindingsRecovery(true);
            parser.setEnvironment(
                    classpath.toArray(String[]::new),
                    sourcepath.toArray(String[]::new),
                    null,
                    true);
            parser.setCompilerOptions(JavaCore.getOptions());
            return (CompilationUnit) parser.createAST(null);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Java source file: " + javaFile, e);
        }
    }

    private List<Path> javaFiles() {
        Set<Path> files = new LinkedHashSet<>();
        for (Path source : sources) {
            if (Files.isRegularFile(source) && source.toString().endsWith(".java")) {
                files.add(source.toAbsolutePath().normalize());
                continue;
            }
            if (Files.isDirectory(source)) {
                files.addAll(walk(source, ".java"));
            }
        }
        return files.stream().sorted(Comparator.comparing(Path::toString)).toList();
    }

    private List<String> classpathEntries() {
        List<String> entries = new ArrayList<>();
        for (Path classesPath : classes) {
            if (Files.isDirectory(classesPath)) {
                entries.add(classesPath.toAbsolutePath().normalize().toString());
            }
        }
        for (Path dependency : dependencies) {
            if (Files.isRegularFile(dependency) && dependency.toString().endsWith(".jar")) {
                entries.add(dependency.toAbsolutePath().normalize().toString());
                continue;
            }
            if (Files.isDirectory(dependency)) {
                walk(dependency, ".jar").stream()
                        .map(path -> path.toAbsolutePath().normalize().toString())
                        .forEach(entries::add);
            }
        }
        return entries;
    }

    private List<String> sourcepathEntries() {
        return sources.stream()
                .map(path -> Files.isRegularFile(path) ? path.getParent() : path)
                .filter(path -> path != null && Files.isDirectory(path))
                .map(path -> path.toAbsolutePath().normalize().toString())
                .distinct()
                .toList();
    }

    private String projectFilePath(Path javaFile) {
        Path absolute = javaFile.toAbsolutePath().normalize();
        if (project != null && absolute.startsWith(project)) {
            return project.relativize(absolute).toString();
        }
        return absolute.toString();
    }

    private String unitName(Path javaFile) {
        Path absolute = javaFile.toAbsolutePath().normalize();
        for (Path source : sources) {
            Path root = Files.isRegularFile(source) ? source.getParent() : source;
            if (root != null) {
                Path absoluteRoot = root.toAbsolutePath().normalize();
                if (absolute.startsWith(absoluteRoot)) {
                    return absoluteRoot.relativize(absolute).toString();
                }
            }
        }
        return javaFile.getFileName().toString();
    }

    private static List<Path> walk(Path root, String suffix) {
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(suffix))
                    .map(path -> path.toAbsolutePath().normalize())
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to scan path: " + root, e);
        }
    }

    private static Path normalize(Path path) {
        return path == null ? null : path.toAbsolutePath().normalize();
    }

    public static final class Builder {
        private Path project;
        private final List<Path> sources = new ArrayList<>();
        private final List<Path> classes = new ArrayList<>();
        private final List<Path> dependencies = new ArrayList<>();
        private Charset charset = StandardCharsets.UTF_8;
        private final JavaStaticExtractRunner.Builder runnerBuilder = JavaStaticExtractRunner.builder();

        /**
         * Java project root, for example the directory containing {@code pom.xml}
         * or {@code build.gradle}. If only project is passed, the runner will
         * discover common source, class, and dependency directories under it.
         */
        public Builder project(Path project) {
            this.project = project;
            return this;
        }

        /**
         * Java source directory or a single {@code .java} file to analyze.
         * If a project is also passed, relative paths are resolved from project.
         */
        public Builder source(Path source) {
            if (source != null) {
                sources.add(source);
            }
            return this;
        }

        /**
         * Directory containing compiled classes for the current project, such as
         * {@code target/classes}, {@code target/test-classes}, or Gradle's class
         * output directories. These classes help JDT confirm exact Java types.
         */
        public Builder classes(Path classes) {
            if (classes != null) {
                this.classes.add(classes);
            }
            return this;
        }

        /**
         * A third-party dependency jar, or a directory containing dependency jars,
         * such as {@code libs} or {@code target/dependency}. These jars help JDT
         * confirm calls to framework/library types like Spring MVC or RestTemplate.
         */
        public Builder dependency(Path dependency) {
            if (dependency != null) {
                dependencies.add(dependency);
            }
            return this;
        }

        public Builder charset(Charset charset) {
            if (charset != null) {
                this.charset = charset;
            }
            return this;
        }

        public Builder classpathRules(boolean enabled) {
            runnerBuilder.classpathRules(enabled);
            return this;
        }

        public Builder classpathTraceRules(boolean enabled) {
            runnerBuilder.classpathTraceRules(enabled);
            return this;
        }

        public Builder addRule(StaticExtractRule rule) {
            runnerBuilder.addRule(rule);
            return this;
        }

        public Builder addRules(List<StaticExtractRule> rules) {
            runnerBuilder.addRules(rules);
            return this;
        }

        public Builder addTraceRuleSet(StaticTraceRuleSet traceRuleSet) {
            runnerBuilder.addTraceRuleSet(traceRuleSet);
            return this;
        }

        public Builder addTraceRuleSets(List<StaticTraceRuleSet> traceRuleSets) {
            runnerBuilder.addTraceRuleSets(traceRuleSets);
            return this;
        }

        public Builder addTraceResolver(JdtTraceResolver traceResolver) {
            runnerBuilder.addTraceResolver(traceResolver);
            return this;
        }

        public Builder externalValueResolver(ExternalValueResolver resolver) {
            runnerBuilder.externalValueResolver(resolver);
            return this;
        }

        public Builder externalValues(Map<String, Map<String, List<String>>> values) {
            runnerBuilder.externalValues(values);
            return this;
        }

        public Builder rulesFromDirectory(Path directory) {
            runnerBuilder.rulesFromDirectory(directory);
            return this;
        }

        public Builder traceRulesFromDirectory(Path directory) {
            runnerBuilder.traceRulesFromDirectory(directory);
            return this;
        }

        public JavaStaticExtractProjectRunner build() {
            Path normalizedProject = normalize(project);
            List<Path> effectiveSources = resolveSources(normalizedProject);
            if (effectiveSources.isEmpty()) {
                throw new IllegalStateException("Pass project(...) or at least one source(...).");
            }
            return new JavaStaticExtractProjectRunner(
                    normalizedProject,
                    effectiveSources,
                    resolveExisting(normalizedProject, classes, defaultClasses(normalizedProject)),
                    resolveExisting(normalizedProject, dependencies, defaultDependencies(normalizedProject)),
                    charset,
                    runnerBuilder.build());
        }

        private List<Path> resolveSources(Path normalizedProject) {
            return resolveExisting(normalizedProject, sources, defaultSources(normalizedProject));
        }

        private List<Path> resolveExisting(Path normalizedProject, List<Path> explicitPaths, List<Path> defaults) {
            List<Path> rawPaths = explicitPaths.isEmpty() ? defaults : explicitPaths;
            return rawPaths.stream()
                    .map(path -> resolve(normalizedProject, path))
                    .filter(Files::exists)
                    .distinct()
                    .toList();
        }

        private Path resolve(Path normalizedProject, Path path) {
            if (path == null) {
                return null;
            }
            if (path.isAbsolute() || normalizedProject == null) {
                return path.toAbsolutePath().normalize();
            }
            return normalizedProject.resolve(path).toAbsolutePath().normalize();
        }

        private List<Path> defaultSources(Path normalizedProject) {
            if (normalizedProject == null) {
                return List.of();
            }
            List<Path> sources = new ArrayList<>();
            sources.add(normalizedProject.resolve("src/main/java"));
            sources.add(normalizedProject.resolve("src/test/java"));
            sources.addAll(discoverSourceRoots(normalizedProject));
            return sources;
        }

        private List<Path> defaultClasses(Path normalizedProject) {
            if (normalizedProject == null) {
                return List.of();
            }
            return List.of(
                    normalizedProject.resolve("target/classes"),
                    normalizedProject.resolve("target/test-classes"),
                    normalizedProject.resolve("build/classes/java/main"),
                    normalizedProject.resolve("build/classes/java/test"));
        }

        private List<Path> defaultDependencies(Path normalizedProject) {
            if (normalizedProject == null) {
                return List.of();
            }
            return List.of(
                    normalizedProject.resolve("libs"),
                    normalizedProject.resolve("target/dependency"));
        }

        private List<Path> discoverSourceRoots(Path normalizedProject) {
            try (var stream = Files.walk(normalizedProject, 6)) {
                return stream.filter(Files::isDirectory)
                        .filter(path -> path.endsWith(Path.of("src/main/java"))
                                || path.endsWith(Path.of("src/test/java")))
                        .map(path -> path.toAbsolutePath().normalize())
                        .sorted(Comparator.comparing(Path::toString))
                        .toList();
            } catch (IOException e) {
                throw new UncheckedIOException("Failed to discover Java source roots: " + normalizedProject, e);
            }
        }
    }
}
