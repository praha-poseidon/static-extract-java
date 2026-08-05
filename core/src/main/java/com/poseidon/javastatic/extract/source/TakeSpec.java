package com.poseidon.javastatic.extract.source;

import java.util.List;

public record TakeSpec(
        TakeKind kind,
        String kindName,
        List<String> attributes) {

    public TakeSpec(TakeKind kind, List<String> attributes) {
        this(kind, kind != null ? kind.name().toLowerCase() : null, attributes);
    }
}
