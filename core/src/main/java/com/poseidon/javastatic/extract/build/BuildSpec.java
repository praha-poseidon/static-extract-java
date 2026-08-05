package com.poseidon.javastatic.extract.build;

import java.util.Map;

public record BuildSpec(Map<String, BuildExpression> fields) {}
