package com.poseidon.javastatic.extract.jdt.source;

import com.poseidon.javastatic.extract.build.BuildAction;
import com.poseidon.javastatic.extract.jdt.build.JdtBuildEvaluator;
import com.poseidon.javastatic.extract.jdt.support.ValueSupport;
import com.poseidon.javastatic.extract.source.LetSpec;
import com.poseidon.javastatic.extract.source.SourceSpec;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdtLetEvaluator {

    private final JdtSourceEvaluator sourceEvaluator;
    private final JdtBuildEvaluator buildEvaluator = new JdtBuildEvaluator();

    public JdtLetEvaluator(JdtSourceEvaluator sourceEvaluator) {
        this.sourceEvaluator = sourceEvaluator;
    }

    public Map<String, List<String>> evaluate(List<LetSpec> lets, JdtEvalContext context) {
        Map<String, List<String>> values = new LinkedHashMap<>();
        if (lets == null) {
            return values;
        }
        for (LetSpec let : lets) {
            List<String> resolved = List.of();
            if (let.sources() != null) {
                for (SourceSpec source : let.sources()) {
                    resolved = sourceEvaluator.evaluate(source, context);
                    if (!resolved.isEmpty()) {
                        break;
                    }
                }
            }
            if (resolved.isEmpty() && let.defaultValue() != null) {
                resolved = List.of(let.defaultValue());
            }
            resolved = ValueSupport.applyMapping(resolved, let.mapping());
            resolved = applyPipeline(resolved, let.pipeline());
            if (!resolved.isEmpty()) {
                values.put(let.name(), ValueSupport.dedupe(resolved));
            }
        }
        return values;
    }

    private List<String> applyPipeline(List<String> values, List<BuildAction> pipeline) {
        if (values == null || values.isEmpty() || pipeline == null || pipeline.isEmpty()) {
            return values;
        }
        List<String> out = new ArrayList<>(values.size());
        for (String value : values) {
            out.add(buildEvaluator.applyActions(value, pipeline));
        }
        return out;
    }
}
