package com.poseidon.javastatic.extract.language;

import com.poseidon.javastatic.extract.rule.StaticExtractRule;

/**
 * Parses the user-facing SER language into the static extract rule model.
 *
 * <p>The first implementation should support the constrained operator set in
 * this module's README before growing into a general grammar.
 */
public interface SerRuleParser {

    StaticExtractRule parse(String source);
}
