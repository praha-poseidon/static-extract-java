package com.poseidon.javastatic.extract.jdt.extractor;

import com.poseidon.javastatic.extract.jdt.DefaultJdtStaticExtractEngine;
import com.poseidon.javastatic.extract.jdt.StaticExtractResult;
import com.poseidon.javastatic.extract.jdt.load.SerRuleLoader;
import com.poseidon.javastatic.extract.jdt.trace.JdtTraceOptions;
import com.poseidon.javastatic.extract.jdt.trace.external.ExternalValueResolver;
import com.poseidon.javastatic.extract.jdt.trace.external.MapExternalValueResolver;
import com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceResolver;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class JavaStaticExtractRunner {

    private final List<StaticExtractRule> rules;
    private final DefaultJdtStaticExtractEngine engine;

    private JavaStaticExtractRunner(List<StaticExtractRule> rules, JdtTraceOptions traceOptions) {
        this.rules = List.copyOf(rules);
        this.engine = new DefaultJdtStaticExtractEngine(traceOptions);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(SerRuleLoader loader) {
        return new Builder(loader);
    }

    public List<StaticExtractRule> rules() {
        return rules;
    }

    public List<StaticExtractResult> extract(
            CompilationUnit compilationUnit,
            String projectFilePath,
            String absoluteFilePath) {
        if (compilationUnit == null) {
            return List.of();
        }
        List<StaticExtractResult> results = new ArrayList<>();
        for (Object type : compilationUnit.types()) {
            if (type instanceof TypeDeclaration typeDeclaration) {
                results.addAll(extract(compilationUnit, typeDeclaration, projectFilePath, absoluteFilePath));
            }
        }
        return results;
    }

    public List<StaticExtractResult> extract(
            CompilationUnit compilationUnit,
            TypeDeclaration typeDeclaration,
            String projectFilePath,
            String absoluteFilePath) {
        if (compilationUnit == null || typeDeclaration == null) {
            return List.of();
        }
        List<StaticExtractResult> results = new ArrayList<>();
        for (StaticExtractRule rule : rules) {
            results.addAll(engine.execute(rule, compilationUnit, typeDeclaration, projectFilePath, absoluteFilePath));
        }
        return results;
    }

    public static final class Builder {
        private final SerRuleLoader loader;
        private final List<StaticExtractRule> rules = new ArrayList<>();
        private final List<StaticTraceRuleSet> traceRuleSets = new ArrayList<>();
        private final List<JdtTraceResolver> traceResolvers = new ArrayList<>();
        private ExternalValueResolver externalValueResolver = new MapExternalValueResolver(Map.of());
        private boolean loadClasspathRules = true;
        private boolean loadClasspathTraceRules = true;

        private Builder() {
            this(new SerRuleLoader());
        }

        public Builder(SerRuleLoader loader) {
            this.loader = loader != null ? loader : new SerRuleLoader();
        }

        public Builder classpathRules(boolean enabled) {
            this.loadClasspathRules = enabled;
            return this;
        }

        public Builder classpathTraceRules(boolean enabled) {
            this.loadClasspathTraceRules = enabled;
            return this;
        }

        public Builder addRule(StaticExtractRule rule) {
            if (rule != null) {
                rules.add(rule);
            }
            return this;
        }

        public Builder addRules(List<StaticExtractRule> rules) {
            if (rules != null) {
                rules.forEach(this::addRule);
            }
            return this;
        }

        public Builder addTraceRuleSet(StaticTraceRuleSet traceRuleSet) {
            if (traceRuleSet != null) {
                traceRuleSets.add(traceRuleSet);
            }
            return this;
        }

        public Builder addTraceRuleSets(List<StaticTraceRuleSet> traceRuleSets) {
            if (traceRuleSets != null) {
                traceRuleSets.forEach(this::addTraceRuleSet);
            }
            return this;
        }

        public Builder addTraceResolver(JdtTraceResolver traceResolver) {
            if (traceResolver != null) {
                traceResolvers.add(traceResolver);
            }
            return this;
        }

        public Builder addTraceResolvers(List<JdtTraceResolver> traceResolvers) {
            if (traceResolvers != null) {
                traceResolvers.forEach(this::addTraceResolver);
            }
            return this;
        }

        public Builder rulesFromDirectory(Path directory) {
            return addRules(loader.loadRulesFromDirectory(directory));
        }

        public Builder traceRulesFromDirectory(Path directory) {
            return addTraceRuleSets(loader.loadTraceRulesFromDirectory(directory));
        }

        public Builder rulesFromFiles(List<Path> files) {
            return addRules(loader.loadRulesFromFiles(files));
        }

        public Builder traceRulesFromFiles(List<Path> files) {
            return addTraceRuleSets(loader.loadTraceRulesFromFiles(files));
        }

        public Builder externalValueResolver(ExternalValueResolver resolver) {
            this.externalValueResolver = resolver != null ? resolver : new MapExternalValueResolver(Map.of());
            return this;
        }

        public Builder externalValues(Map<String, Map<String, List<String>>> values) {
            return externalValueResolver(new MapExternalValueResolver(values));
        }

        public JavaStaticExtractRunner build() {
            List<StaticExtractRule> effectiveRules = new ArrayList<>();
            if (loadClasspathRules) {
                effectiveRules.addAll(loader.loadAll());
            }
            effectiveRules.addAll(rules);

            List<StaticTraceRuleSet> effectiveTraceRuleSets = new ArrayList<>();
            if (loadClasspathTraceRules) {
                effectiveTraceRuleSets.addAll(loader.loadApplicationTraceRules());
            }
            effectiveTraceRuleSets.addAll(traceRuleSets);

            JdtTraceOptions traceOptions =
                    JdtTraceOptions.of(effectiveTraceRuleSets, externalValueResolver, traceResolvers);
            return new JavaStaticExtractRunner(effectiveRules, traceOptions);
        }
    }
}
