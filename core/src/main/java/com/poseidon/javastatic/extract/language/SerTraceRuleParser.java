package com.poseidon.javastatic.extract.language;

import com.poseidon.javastatic.extract.trace.StaticTraceRuleSet;

public interface SerTraceRuleParser {

    StaticTraceRuleSet parseTrace(String source);
}
