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
    private final JdtLetEvaluator letEvaluator;
    private final JdtBuildEvaluator buildEvaluator;

    public DefaultJdtStaticExtractEngine() {
        this(JdtTraceOptions.empty());
    }

    public DefaultJdtStaticExtractEngine(JdtTraceOptions traceOptions) {
        JdtValueTracer valueTracer = new JdtValueTracer(traceOptions);
        JdtSourceEvaluator sourceEvaluator = new JdtSourceEvaluator(valueTracer);
        this.findExecutor = new JdtFindExecutor();
        this.letEvaluator = new JdtLetEvaluator(sourceEvaluator);
        this.buildEvaluator = new JdtBuildEvaluator();
    }

    public DefaultJdtStaticExtractEngine(
            JdtFindExecutor findExecutor,
            JdtLetEvaluator letEvaluator,
            JdtBuildEvaluator buildEvaluator) {
        this.findExecutor = findExecutor;
        this.letEvaluator = letEvaluator;
        this.buildEvaluator = buildEvaluator;
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
}
