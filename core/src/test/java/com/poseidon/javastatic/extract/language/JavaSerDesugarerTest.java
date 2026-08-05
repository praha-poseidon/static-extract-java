package com.poseidon.javastatic.extract.language;

import com.poseidon.javastatic.extract.rule.StaticExtractRule;
import com.poseidon.javastatic.extract.source.JavaElementKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class JavaSerDesugarerTest {

    private final JavaSerDesugarer desugarer = new JavaSerDesugarer();

    @Test
    void desugarsFindMethodWithAnnotation() {
        String input = "find method with annotation @GetMapping\n";
        String expected =
                """
                find method
                when annotation @GetMapping on method
                """;
        assertEquals(expected, desugarer.apply(input));
    }

    @Test
    void desugarsFindFieldWithAnnotationStar() {
        String input = "find field with annotation @*Mapping\n";
        String expected =
                """
                find field
                when annotation @*Mapping on field
                """;
        assertEquals(expected, desugarer.apply(input));
    }

    @Test
    void desugarsFindClassWithAnnotation() {
        String input = "  find class with annotation @Controller\n";
        String expected =
                """
                  find class
                  when annotation @Controller on class
                """;
        assertEquals(expected, desugarer.apply(input));
    }

    @Test
    void leavesNonSugarFindUnchanged() {
        String input = "find method RestTemplate.getForObject\n";
        assertEquals(input, desugarer.apply(input));
    }

    @Test
    void desugarsFromAnnotationOnLegacyOrder() {
        String input = "  from annotation on method @RouteGet take attr(value)\n";
        String expected = "  from annotation @RouteGet on method take attr(value)\n";
        assertEquals(expected, desugarer.apply(input));
    }

    @Test
    void leavesPreferredFromAnnotationOrderUnchanged() {
        String input = "  from annotation @RouteGet on method take attr(value)\n";
        assertEquals(input, desugarer.apply(input));
    }

    @Test
    void leavesTraceWhenAnnotationUnchanged() {
        String input =
                """
                from field
                when annotation @Value on field
                """;
        assertEquals(input, desugarer.apply(input));
    }

    @Test
    void nullStaysNull() {
        assertNull(desugarer.apply(null));
    }

    @Test
    void parserStillAcceptsSugarViaDesugar() {
        String ser =
                """
                rule "x"
                fact y

                find method with annotation @RouteGet

                let path =
                  from annotation on method @RouteGet take attr(value)

                build {
                  path: path
                }
                """;
        StaticExtractRule rule = new AntlrSerRuleParser().parse(ser);
        assertNotNull(rule.find());
        assertEquals(JavaElementKind.METHOD, rule.find().target());
        assertNotNull(rule.find().annotation());
    }
}
