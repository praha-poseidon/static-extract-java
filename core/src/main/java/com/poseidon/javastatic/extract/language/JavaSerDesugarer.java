package com.poseidon.javastatic.extract.language;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites Java dialect SER sugar into forms closer to core SER before ANTLR parse.
 *
 * <p>See sibling {@code static-extract-spec/docs/CORE-VS-JAVA-DIALECT.md}.
 *
 * <ul>
 *   <li>B1: identity shell
 *   <li>B2: {@code find (method|class|field) with annotation @X} → {@code find …} + {@code when
 *       annotation @X on …}
 *   <li>B3: {@code from annotation on …} kept (still required by g4 / value model); normalized
 *       whitespace only if needed later
 *   <li>B4: trace {@code when annotation}/{@code when method} kept (trace path already uses them)
 * </ul>
 */
public final class JavaSerDesugarer {

    /**
     * F1/F2/F3: find method|class|field with annotation @Name or @*Suffix
     *
     * <p>Example:
     *
     * <pre>
     * find method with annotation @GetMapping
     * →
     * find method
     * when annotation @GetMapping on method
     * </pre>
     */
    private static final Pattern FIND_WITH_ANNOTATION =
            Pattern.compile(
                    "(?m)^([ \\t]*)find[ \\t]+(method|class|field)[ \\t]+with[ \\t]+annotation[ \\t]+(@\\*?[A-Za-z_][A-Za-z0-9_]*)[ \\t]*$");

    /**
     * @param source raw SER or trace-SER text
     * @return text to feed the ANTLR grammar (never null if source is non-null)
     */
    public String apply(String source) {
        if (source == null) {
            return null;
        }
        // B3/B4: from annotation on / when annotation / when method — pass through until core g4
        // gains equivalent free-form source/when without exclusive productions.
        return desugarFindWithAnnotation(source);
    }

    private static String desugarFindWithAnnotation(String source) {
        Matcher matcher = FIND_WITH_ANNOTATION.matcher(source);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String indent = matcher.group(1);
            String element = matcher.group(2);
            String annotation = matcher.group(3);
            String replacement =
                    indent
                            + "find "
                            + element
                            + "\n"
                            + indent
                            + "when annotation "
                            + annotation
                            + " on "
                            + element;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
