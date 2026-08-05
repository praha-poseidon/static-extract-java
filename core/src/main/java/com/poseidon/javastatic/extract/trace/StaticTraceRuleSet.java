package com.poseidon.javastatic.extract.trace;

import java.util.List;

public record StaticTraceRuleSet(
        String name,
        List<ExternalValueEntryRule> externalEntries) {}
