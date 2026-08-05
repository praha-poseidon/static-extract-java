package com.poseidon.javastatic.extract.assistant;

import java.util.List;

public record DiagnosticReport(
        String status,
        TryReport tryReport,
        List<SourceFacts> facts,
        List<String> hints) {}
