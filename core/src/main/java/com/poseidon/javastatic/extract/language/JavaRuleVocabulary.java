package com.poseidon.javastatic.extract.language;

import java.util.Set;

/**
 * Built-in Java vocabulary currently implemented by the JDT extractor.
 */
public final class JavaRuleVocabulary implements RuleVocabulary {

    public static final JavaRuleVocabulary INSTANCE = new JavaRuleVocabulary();

    private static final Set<String> FIND_KINDS = Set.of(
            "class",
            "field",
            "method");

    private static final Set<String> SOURCE_KINDS = Set.of(
            "annotation",
            "argument",
            "assignment",
            "call",
            "class",
            "field",
            "literal",
            "method",
            "new",
            "parameter",
            "return");

    private static final Set<String> TAKE_KINDS = Set.of(
            "attr",
            "name",
            "owner",
            "raw",
            "signature",
            "type",
            "value");

    private static final Set<String> NORMALIZE_KINDS = Set.of(
            "extractPath",
            "extract_path",
            "kebab",
            "kebabCase",
            "kebab_case",
            "pathVariable",
            "path_variable",
            "placeholderDefault",
            "placeholder_default",
            "placeholderLookup",
            "placeholder_lookup",
            "slash");

    private JavaRuleVocabulary() {}

    @Override
    public String language() {
        return "java";
    }

    @Override
    public Set<String> findKinds() {
        return FIND_KINDS;
    }

    @Override
    public Set<String> sourceKinds() {
        return SOURCE_KINDS;
    }

    @Override
    public Set<String> takeKinds() {
        return TAKE_KINDS;
    }

    @Override
    public Set<String> normalizeKinds() {
        return NORMALIZE_KINDS;
    }
}
