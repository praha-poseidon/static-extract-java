package com.poseidon.javastatic.extract.language;

/**
 * User-facing SER language tokens.
 *
 * <p>These names are the small public vocabulary a rule author sees. Extractor
 * concepts such as AST nodes or JDT binding names should not appear here.
 */
public final class SerOperators {

    private SerOperators() {}

    public static final String RULE = "rule";
    public static final String ENDPOINT = "endpoint";
    public static final String FACT = "fact";
    public static final String FIND = "find";
    public static final String WITH = "with";
    public static final String LET = "let";
    public static final String FROM = "from";
    public static final String ON = "on";
    public static final String TAKE = "take";
    public static final String DEFAULT = "default";
    public static final String BUILD = "build";
}
