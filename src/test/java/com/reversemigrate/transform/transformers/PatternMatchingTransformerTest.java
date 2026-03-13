package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PatternMatchingTransformer.
 * Covers simple instanceof pattern, else-if chains, and nested patterns.
 */
class PatternMatchingTransformerTest {

    private PatternMatchingTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new PatternMatchingTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Test
    @DisplayName("Simple pattern matching instanceof is converted to cast")
    void testSimplePatternMatching() {
        String source = """
                class Test {
                    void test(Object obj) {
                        if (obj instanceof String s) {
                            System.out.println(s.length());
                        }
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        // Should have plain instanceof
        assertTrue(output.contains("instanceof String"),
                "Should retain instanceof check");

        // Should have explicit cast
        assertTrue(output.contains("(String) obj"),
                "Should have explicit cast");

        // Should declare variable
        assertTrue(output.contains("String s"),
                "Should have variable declaration");
    }

    @Test
    @DisplayName("Pattern matching in else-if chain")
    void testElseIfChain() {
        String source = """
                class Test {
                    String format(Object obj) {
                        if (obj instanceof Integer i) {
                            return i.toString();
                        } else if (obj instanceof String s) {
                            return s;
                        } else {
                            return obj.toString();
                        }
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("(Integer) obj"), "Should cast to Integer");
        assertTrue(output.contains("(String) obj"), "Should cast to String");
    }

    @Test
    @DisplayName("Code without pattern matching is unchanged")
    void testNoPatternMatching() {
        String source = """
                class Test {
                    void test(Object obj) {
                        if (obj instanceof String) {
                            String s = (String) obj;
                            System.out.println(s);
                        }
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        String before = cu.toString();
        CompilationUnit result = transformer.transform(cu);

        assertEquals(before, result.toString(), "Traditional instanceof should be unchanged");
    }

    @Test
    @DisplayName("Pattern matching with single-statement body wraps in block")
    void testSingleStatementBody() {
        String source = """
                class Test {
                    void test(Object obj) {
                        if (obj instanceof String s)
                            System.out.println(s);
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("String s = (String) obj"),
                "Should add cast variable at start of block");
    }

    @Test
    @DisplayName("Feature ID is PATTERN_MATCHING_INSTANCEOF")
    void testFeatureId() {
        assertEquals("PATTERN_MATCHING_INSTANCEOF", transformer.featureId());
    }

    @Test
    @DisplayName("Multiple pattern matches in same method")
    void testMultiplePatterns() {
        String source = """
                class Test {
                    void test(Object a, Object b) {
                        if (a instanceof String s) {
                            System.out.println(s);
                        }
                        if (b instanceof Integer i) {
                            System.out.println(i);
                        }
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("(String) a"), "Should cast a to String");
        assertTrue(output.contains("(Integer) b"), "Should cast b to Integer");
    }
}
