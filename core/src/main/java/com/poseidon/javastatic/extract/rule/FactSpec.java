package com.poseidon.javastatic.extract.rule;

/**
 * Declares the standard fact type emitted by a rule.
 *
 * <p>Facts are broader than endpoints. They can represent backend endpoints,
 * frontend UI actions, API calls, routes, permissions, configuration keys, or
 * other extracted code facts.
 */
public record FactSpec(String type) {}
