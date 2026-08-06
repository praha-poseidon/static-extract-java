package com.poseidon.javastatic.extract.jdt;

import com.poseidon.javastatic.extract.jdt.build.JdtBuildEvaluator;
import com.poseidon.javastatic.extract.jdt.find.JdtFindExecutor;
import com.poseidon.javastatic.extract.jdt.source.JdtEvalContext;
import com.poseidon.javastatic.extract.jdt.source.JdtLetEvaluator;
import com.poseidon.javastatic.extract.jdt.source.JdtSourceEvaluator;
import com.poseidon.javastatic.extract.jdt.support.JdtNodeSupport;
import com.poseidon.javastatic.extract.jdt.trace.JdtTraceOptions;
import com.poseidon.javastatic.extract.jdt.trace.JdtValueTracer;
import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.TypeDeclaration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DefaultJdtStaticExtractEngine implements JdtStaticExtractEngine {

    private final JdtFindExecutor findExecutor;
    private final JdtBuildEvaluator buildEvaluator;
    private final JdtTraceOptions baseTraceOptions;
    private final JdtLetEvaluator sharedLetEvaluator;
    private final List<StaticExtractRule> extractRules;

    public DefaultJdtStaticExtractEngine() {
        this(JdtTraceOptions.empty(), List.of());
    }

    public DefaultJdtStaticExtractEngine(JdtTraceOptions traceOptions) {
        this(traceOptions, List.of());
    }

    public DefaultJdtStaticExtractEngine(JdtTraceOptions traceOptions, List<StaticExtractRule> extractRules) {
        this.baseTraceOptions = traceOptions != null ? traceOptions : JdtTraceOptions.empty();
        this.findExecutor = new JdtFindExecutor();
        this.buildEvaluator = new JdtBuildEvaluator();
        this.sharedLetEvaluator = null;
        this.extractRules = extractRules != null ? List.copyOf(extractRules) : List.of();
    }

    public DefaultJdtStaticExtractEngine(
            JdtFindExecutor findExecutor,
            JdtLetEvaluator letEvaluator,
            JdtBuildEvaluator buildEvaluator) {
        this.findExecutor = findExecutor;
        this.sharedLetEvaluator = letEvaluator;
        this.buildEvaluator = buildEvaluator;
        this.baseTraceOptions = JdtTraceOptions.empty();
        this.extractRules = List.of();
    }

    @Override
    public List<StaticExtractResult> execute(
            StaticExtractRule rule,
            CompilationUnit compilationUnit,
            TypeDeclaration typeDeclaration,
            String projectFilePath,
            String absoluteFilePath) {
        if (rule == null || rule.find() == null || rule.build() == null) {
            return List.of();
        }

        JdtLetEvaluator letEvaluator = letEvaluatorFor(rule);
        List<StaticExtractResult> results = new ArrayList<>();
        for (ASTNode anchor : findExecutor.find(rule.find(), typeDeclaration)) {
            JdtEvalContext context = new JdtEvalContext(compilationUnit, typeDeclaration, anchor);
            Map<String, List<String>> values = letEvaluator.evaluate(rule.lets(), context);
            for (Map<String, String> fields : buildEvaluator.evaluate(rule.build(), values)) {
                results.add(
                        new StaticExtractResult(
                                rule,
                                fields,
                                JdtNodeSupport.lineStart(compilationUnit, anchor),
                                JdtNodeSupport.lineEnd(compilationUnit, anchor),
                                projectFilePath,
                                absoluteFilePath,
                                JdtNodeSupport.enclosingMethodHint(compilationUnit, typeDeclaration, anchor),
                                anchor));
            }
        }
        return results;
    }

    private JdtLetEvaluator letEvaluatorFor(StaticExtractRule rule) {
        if (sharedLetEvaluator != null) {
            return sharedLetEvaluator;
        }
        JdtTraceOptions options = baseTraceOptions
                .withEmbedded(rule.embeddedTrace())
                .withExtractRules(extractRules.isEmpty() ? List.of(rule) : extractRules);
        JdtValueTracer valueTracer = new JdtValueTracer(options);
        return new JdtLetEvaluator(new JdtSourceEvaluator(valueTracer));
    }
}
