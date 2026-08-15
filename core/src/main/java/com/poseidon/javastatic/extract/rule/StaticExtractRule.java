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
        StaticTraceRuleSet embeddedTrace,
        /**
         * Optional identity dict from the same .ser file ({@code dict { key = value }}).
         * Keys: language-specific fully-qualified method keys; values: topic/path strings.
         */
        Map<String, String> identityDict) {

    public StaticExtractRule {
        if (identityDict == null) {
            identityDict = Map.of();
        } else if (!identityDict.isEmpty()) {
            identityDict = Map.copyOf(identityDict);
        }
    }

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
        this(name, description, enabled, priority, fact, classifiers, endpoint, find, lets, build, null, Map.of());
    }

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
            BuildSpec build,
            StaticTraceRuleSet embeddedTrace) {
        this(name, description, enabled, priority, fact, classifiers, endpoint, find, lets, build, embeddedTrace, Map.of());
    }

    public StaticExtractRule withIdentityDict(Map<String, String> dict) {
        return new StaticExtractRule(
                name,
                description,
                enabled,
                priority,
                fact,
                classifiers,
                endpoint,
                find,
                lets,
                build,
                embeddedTrace,
                dict == null ? Map.of() : dict);
    }
}
