package com.poseidon.javastatic.extract.rule;

/**
 * Declares the endpoint product label emitted by a rule.
 *
 * <p>The static extraction layer intentionally does not validate endpoint types
 * or directions. Downstream graph engines decide how these labels and build
 * fields map to concrete graph endpoint classes.
 */
public record EndpointSpec(String type, String direction) {}
