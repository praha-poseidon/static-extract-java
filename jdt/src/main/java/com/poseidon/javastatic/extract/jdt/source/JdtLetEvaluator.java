package com.poseidon.javastatic.extract.jdt.source;

import com.poseidon.javastatic.extract.jdt.support.ValueSupport;
import com.poseidon.javastatic.extract.source.LetSpec;
import com.poseidon.javastatic.extract.source.SourceSpec;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class JdtLetEvaluator {

    private final JdtSourceEvaluator sourceEvaluator;

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
            if (!resolved.isEmpty()) {
                values.put(let.name(), ValueSupport.dedupe(resolved));
            }
        }
        return values;
    }
}
