package com.poseidon.javastatic.extract.jdt.trace.spi;

import org.eclipse.jdt.core.dom.Expression;

import java.util.List;

public interface JdtTraceContext {

    List<String> trace(Expression expression);

    List<String> resolveExternalValue(String namespace, String key);
}
