package com.poseidon.javastatic.extract.language;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites Java dialect SER sugar into preferred core-oriented forms before ANTLR parse.
 *
 * <p>See sibling {@code static-extract-spec/docs/CORE-VS-JAVA-DIALECT.md} and
 * {@code METHOD-VS-CALL.md}.
 */
public final class JavaSerDesugarer {

    /**
     * F1/F2/F3: find method|class|field with annotation @Name or @*Suffix
     */
    private static final Pattern FIND_WITH_ANNOTATION =
            Pattern.compile(
                    "(?m)^([ \\t]*)find[ \\t]+(method|class|field)[ \\t]+with[ \\t]+annotation[ \\t]+(@\\*?[A-Za-z_][A-Za-z0-9_]*)[ \\t]*$");

    /**
     * S1–S3 legacy: from annotation on element @Ref → from annotation @Ref on element
     */
    private static final Pattern FROM_ANNOTATION_ON_LEGACY =
            Pattern.compile(
                    "(?m)^([ \\t]*from[ \\t]+annotation[ \\t]+)on[ \\t]+(method|class|field|parameter)[ \\t]+(@\\*?[A-Za-z_][A-Za-z0-9_]*)\\b");

    /**
     * F4/F5: find method Owner.name / Owner.[a,b] → find call … (call sites, not declarations)
     */
    private static final Pattern FIND_METHOD_CALL_PATTERN =
            Pattern.compile("(?m)^([ \\t]*)find[ \\t]+method[ \\t]+(\\S*\\.\\S+)[ \\t]*$");

    /**
     * Legacy decorator order: from decorator on class Name → from decorator Name on class
     */
    private static final Pattern FROM_DECORATOR_ON_LEGACY =
            Pattern.compile(
                    "(?m)^([ \\t]*from[ \\t]+decorator[ \\t]+)on[ \\t]+(method|class|field|parameter)[ \\t]+(@?[A-Za-z_][\\w$]*)\\b");

    public String apply(String source) {
        if (source == null) {
            return null;
        }
        String step = desugarFindWithAnnotation(source);
        step = desugarFromAnnotationOnLegacy(step);
        step = desugarFromDecoratorOnLegacy(step);
        return desugarFindMethodCallPattern(step);
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
            String prefix = matcher.group(1);
            String element = matcher.group(2);
            String annotation = matcher.group(3);
            String replacement = prefix + annotation + " on " + element;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String desugarFindMethodCallPattern(String source) {
        Matcher matcher = FIND_METHOD_CALL_PATTERN.matcher(source);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String indent = matcher.group(1);
            String pattern = matcher.group(2);
            String replacement = indent + "find call " + pattern;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static String desugarFromDecoratorOnLegacy(String source) {
        Matcher matcher = FROM_DECORATOR_ON_LEGACY.matcher(source);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            String prefix = matcher.group(1);
            String element = matcher.group(2);
            String name = matcher.group(3);
            String replacement = prefix + name + " on " + element;
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }
}
