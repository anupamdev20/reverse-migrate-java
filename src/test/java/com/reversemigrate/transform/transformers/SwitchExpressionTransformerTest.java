package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SwitchExpressionTransformer.
 * Covers assignment context, multiple labels, yield, return context, and throw.
 */
class SwitchExpressionTransformerTest {

    private SwitchExpressionTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new SwitchExpressionTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Test
    @DisplayName("Simple switch expression assigned to variable is converted")
    void testSimpleSwitchExpression() {
        String source = """
                class Test {
                    void test() {
                        String day = "MONDAY";
                        int result = switch (day) {
                            case "MONDAY" -> 1;
                            case "TUESDAY" -> 2;
                            default -> 0;
                        };
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        // Should not contain arrow syntax
        assertFalse(output.contains("->"), "Arrow syntax should be removed");

        // Should contain traditional case with colon
        assertTrue(output.contains("case"), "Should have case labels");
        assertTrue(output.contains("break"), "Should have break statements");
    }

    @Test
    @DisplayName("Switch expression with multiple labels per case")
    void testMultipleLabels() {
        String source = """
                class Test {
                    void test() {
                        String day = "MONDAY";
                        int type = switch (day) {
                            case "MONDAY", "FRIDAY" -> 1;
                            case "TUESDAY" -> 2;
                            default -> 0;
                        };
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("->"), "Arrow syntax should be removed");
    }

    @Test
    @DisplayName("Switch expression with yield in block body")
    void testSwitchWithYield() {
        String source = """
                class Test {
                    void test() {
                        int x = 5;
                        int result = switch (x) {
                            case 1 -> {
                                System.out.println("one");
                                yield 10;
                            }
                            default -> 0;
                        };
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("yield"), "yield should be replaced");
        assertTrue(output.contains("result ="), "Should have assignment");
    }

    @Test
    @DisplayName("Feature ID is SWITCH_EXPRESSION")
    void testFeatureId() {
        assertEquals("SWITCH_EXPRESSION", transformer.featureId());
    }

    @Test
    @DisplayName("Code without switch expressions is unchanged")
    void testNoSwitch() {
        String source = """
                class Test {
                    void test() {
                        int x = 5;
                        switch (x) {
                            case 1:
                                System.out.println("one");
                                break;
                            default:
                                break;
                        }
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        String before = cu.toString();
        CompilationUnit result = transformer.transform(cu);

        assertEquals(before, result.toString(), "Traditional switch should be unchanged");
    }

    @Test
    @DisplayName("Switch expression with throw in case arm")
    void testSwitchWithThrow() {
        String source = """
                class Test {
                    void test() {
                        String s = "TEST";
                        int result = switch (s) {
                            case "A" -> 1;
                            default -> throw new IllegalArgumentException("unknown");
                        };
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("IllegalArgumentException"), "Throw should be preserved");
    }
}
