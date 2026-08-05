package com.poseidon.javastatic.extract.rule;

import com.poseidon.javastatic.extract.build.BuildSpec;
import com.poseidon.javastatic.extract.source.LetSpec;

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
        BuildSpec build) {}
