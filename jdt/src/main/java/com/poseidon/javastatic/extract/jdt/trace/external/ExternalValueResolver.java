package com.poseidon.javastatic.extract.jdt.trace.external;

import java.util.List;

public interface ExternalValueResolver {

    List<String> resolve(String namespace, String key);
}
