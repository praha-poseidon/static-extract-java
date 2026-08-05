package com.poseidon.javastatic.extract.language;

import java.util.Set;

/**
 * Declares the rule words supported by an extractor.
 *
 * <p>The SER parser owns the common syntax. Extractor vocabularies define which
 * find kinds, source kinds, take kinds, and normalization names are meaningful
 * for a specific source language or framework family.
 */
public interface RuleVocabulary {

    String language();

    Set<String> findKinds();

    Set<String> sourceKinds();

    Set<String> takeKinds();

    Set<String> normalizeKinds();
}
