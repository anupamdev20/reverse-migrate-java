package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for VarTransformer.
 * Covers literal initializers, object creation, and fallback cases.
 */
class VarTransformerTest {

    private VarTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new VarTransformer();
    }

    @Test
    @DisplayName("var with string literal becomes String")
    void testVarWithStringLiteral() {
        String source = """
                class Test {
                    void test() {
                        var s = "hello";
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains(" var "), "var should be replaced");
        assertTrue(output.contains("String s"), "Should be String type");
    }

    @Test
    @DisplayName("var with integer literal becomes int")
    void testVarWithIntLiteral() {
        String source = """
                class Test {
                    void test() {
                        var x = 42;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains(" var "), "var should be replaced");
        assertTrue(output.contains("int x"), "Should be int type");
    }

    @Test
    @DisplayName("var with boolean literal becomes boolean")
    void testVarWithBooleanLiteral() {
        String source = """
                class Test {
                    void test() {
                        var flag = true;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("boolean flag"), "Should be boolean type");
    }

    @Test
    @DisplayName("var with new object creation uses the class type")
    void testVarWithObjectCreation() {
        String source = """
                import java.util.ArrayList;
                class Test {
                    void test() {
                        var list = new ArrayList<String>();
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("ArrayList<String> list"),
                "Should use the constructed type");
    }

    @Test
    @DisplayName("var with double literal becomes double")
    void testVarWithDoubleLiteral() {
        String source = """
                class Test {
                    void test() {
                        var pi = 3.14;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("double pi"), "Should be double type");
    }

    @Test
    @DisplayName("var with long literal becomes long")
    void testVarWithLongLiteral() {
        String source = """
                class Test {
                    void test() {
                        var big = 100L;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("long big"), "Should be long type");
    }

    @Test
    @DisplayName("var with char literal becomes char")
    void testVarWithCharLiteral() {
        String source = """
                class Test {
                    void test() {
                        var c = 'a';
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("char c"), "Should be char type");
    }

    @Test
    @DisplayName("var with method call falls back to Object with TODO comment")
    void testVarWithMethodCall() {
        String source = """
                class Test {
                    void test() {
                        var result = someMethod();
                    }
                    Object someMethod() { return null; }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("Object result"), "Should fall back to Object");
        assertTrue(output.contains("TODO"), "Should have TODO comment for review");
    }

    @Test
    @DisplayName("Multiple var declarations in same method")
    void testMultipleVarDeclarations() {
        String source = """
                class Test {
                    void test() {
                        var x = 10;
                        var s = "hello";
                        var b = true;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("int x"), "First should be int");
        assertTrue(output.contains("String s"), "Second should be String");
        assertTrue(output.contains("boolean b"), "Third should be boolean");
    }

    @Test
    @DisplayName("Feature ID is LOCAL_VAR_TYPE")
    void testFeatureId() {
        assertEquals("LOCAL_VAR_TYPE", transformer.featureId());
    }

    @Test
    @DisplayName("Code without var is unchanged")
    void testNoVar() {
        String source = """
                class Test {
                    void test() {
                        int x = 42;
                        String s = "hello";
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        String before = cu.toString();
        CompilationUnit result = transformer.transform(cu);

        assertEquals(before, result.toString(), "Code without var should be unchanged");
    }

    @Test
    @DisplayName("var with cast expression uses cast type")
    void testVarWithCast() {
        String source = """
                class Test {
                    void test(Object obj) {
                        var s = (String) obj;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("String s"), "Should use cast type");
    }
}
