package com.poseidon.javastatic.extract.language;

/**
 * Built-in expression functions. These are not structural keywords; they are
 * optional helpers used inside let/build expressions.
 */
public final class SerBuiltins {

    private SerBuiltins() {}

    public static final String CONCAT = "concat";
    public static final String REGEX = "regex";
    public static final String REPLACE = "replace";
    public static final String NORMALIZE = "normalize";
    public static final String MAP = "map";
}
