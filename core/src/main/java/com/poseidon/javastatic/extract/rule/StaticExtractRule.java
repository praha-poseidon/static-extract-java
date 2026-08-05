package com.poseidon.javastatic.extract.rule;

import com.poseidon.javastatic.extract.build.BuildSpec;
import com.poseidon.javastatic.extract.source.LetSpec;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;

import java.util.List;
import java.util.Map;

public record StaticExtractRule(
        String name,
        String description,
        Boolean enabled,
        Integer priority,
        FactSpec fact,
        Map<String, String> classifiers,
        EndpointSpec endpoint,
        FindSpec find,
        List<LetSpec> lets,
        BuildSpec build,
        /** Optional value-trace block from the same .ser file (trace { ... }). */
        StaticTraceRuleSet embeddedTrace) {

    public StaticExtractRule(
            String name,
            String description,
            Boolean enabled,
            Integer priority,
            FactSpec fact,
            Map<String, String> classifiers,
            EndpointSpec endpoint,
            FindSpec find,
            List<LetSpec> lets,
            BuildSpec build) {
        this(name, description, enabled, priority, fact, classifiers, endpoint, find, lets, build, null);
    }
}
