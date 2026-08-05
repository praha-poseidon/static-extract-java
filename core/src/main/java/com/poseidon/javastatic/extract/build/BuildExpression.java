package com.poseidon.javastatic.extract.build;

import java.util.List;

public record BuildExpression(
        String reference,
        String constValue,
        List<String> concat,
        List<BuildAction> actions) {}
