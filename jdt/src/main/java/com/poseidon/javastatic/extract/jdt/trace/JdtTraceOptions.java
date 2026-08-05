package com.poseidon.javastatic.extract.jdt.trace;

import com.poseidon.javastatic.extract.jdt.trace.external.ExternalValueResolver;
import com.poseidon.javastatic.extract.jdt.trace.spi.JdtTraceResolver;
import com.poseidon.javastatic.extract.trace.ExternalValueEntryRule;
import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;

import java.util.ArrayList;
import java.util.List;

public record JdtTraceOptions(
        List<ExternalValueEntryRule> externalEntries,
        ExternalValueResolver externalValueResolver,
        List<JdtTraceResolver> traceResolvers) {

    public static JdtTraceOptions empty() {
        return new JdtTraceOptions(List.of(), (namespace, key) -> List.of(), List.of());
    }

    public static JdtTraceOptions of(List<StaticTraceRuleSet> ruleSets, ExternalValueResolver resolver) {
        return of(ruleSets, resolver, List.of());
    }

    public static JdtTraceOptions of(
            List<StaticTraceRuleSet> ruleSets,
            ExternalValueResolver resolver,
            List<JdtTraceResolver> traceResolvers) {
        List<ExternalValueEntryRule> entries = new ArrayList<>();
        if (ruleSets != null) {
            for (StaticTraceRuleSet ruleSet : ruleSets) {
                entries.addAll(ruleSet.externalEntries());
            }
        }
        return new JdtTraceOptions(
                entries,
                resolver != null ? resolver : (namespace, key) -> List.of(),
                traceResolvers);
    }

    public JdtTraceOptions {
        externalEntries = externalEntries != null ? List.copyOf(externalEntries) : List.of();
        externalValueResolver = externalValueResolver != null ? externalValueResolver : (namespace, key) -> List.of();
        traceResolvers = traceResolvers != null ? List.copyOf(traceResolvers) : List.of();
    }
}
