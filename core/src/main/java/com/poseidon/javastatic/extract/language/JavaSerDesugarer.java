package com.poseidon.javastatic.extract.language;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites Java dialect SER sugar into preferred core-oriented forms before ANTLR parse.
 *
 * <p>See sibling {@code static-extract-spec/docs/CORE-VS-JAVA-DIALECT.md}.
 */
public final class JavaSerDesugarer {

    /**
     * F1/F2/F3: find method|class|field with annotation @Name or @*Suffix
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
     * S1–S3 legacy order: from annotation on element @Ref
     *
     * <pre>
     * from annotation on method @GetMapping take …
     * →
     * from annotation @GetMapping on method take …
     * </pre>
     */
    private static final Pattern FROM_ANNOTATION_ON_LEGACY =
            Pattern.compile(
                    "(?m)^([ \\t]*from[ \\t]+annotation[ \\t]+)on[ \\t]+(method|class|field|parameter)[ \\t]+(@\\*?[A-Za-z_][A-Za-z0-9_]*)\\b");

    /**
     * @param source raw SER or trace-SER text
     * @return text to feed the ANTLR grammar (never null if source is non-null)
     */
    public String apply(String source) {
        if (source == null) {
            return null;
        }
        String step = desugarFindWithAnnotation(source);
        return desugarFromAnnotationOnLegacy(step);
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

    private static String desugarFromAnnotationOnLegacy(String source) {
        Matcher matcher = FROM_ANNOTATION_ON_LEGACY.matcher(source);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1); // "  from annotation "
            String element = matcher.group(2);
            String annotation = matcher.group(3);
            String replacement = prefix + annotation + " on " + element;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
