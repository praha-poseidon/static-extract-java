package com.poseidon.javastatic.extract.trace;

import com.poseidon.javastatic.extract.build.BuildSpec;
import com.poseidon.javastatic.extract.source.LetSpec;

import java.util.List;

public record ExternalValueEntryRule(
        TraceTargetKind target,
        TraceMatchSpec match,
        List<LetSpec> lets,
        BuildSpec build) {}
