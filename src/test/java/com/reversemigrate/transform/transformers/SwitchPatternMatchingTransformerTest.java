package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class SwitchPatternMatchingTransformerTest {
    
    private SwitchPatternMatchingTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new SwitchPatternMatchingTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    @Test
    @DisplayName("Transforms switch expression with type patterns to if-else chain")
    void testSwitchExpressionWithTypePatterns() {
        String source = "class Test {\n" +
                "    void method(Object obj) {\n" +
                "        String res = switch (obj) {\n" +
                "            case Integer i -> \"int: \" + i;\n" +
                "            case String s -> \"str: \" + s;\n" +
                "            default -> \"unknown\";\n" +
                "        };\n" +
                "    }\n" +
                "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        cu = transformer.transform(cu);
        String transformed = cu.toString();

        assertFalse(transformed.contains("switch"), "Switch should be removed");
        assertTrue(transformed.contains("if (obj instanceof Integer)"), "Should have instanceof checks");
        assertTrue(transformed.contains("Integer i = (Integer) obj;"), "Should cast and bind variables");
        assertTrue(transformed.contains("else if (obj instanceof String)"), "Should chain else-if");
        assertTrue(transformed.contains("res = \"str: \" + s"), "Should assign results");
    }

    @Test
    @DisplayName("Transforms switch statement with type patterns to if-else chain")
    void testSwitchStatementWithTypePatterns() {
        String source = "class Test {\n" +
                "    void method(Object obj) {\n" +
                "        switch (obj) {\n" +
                "            case Integer i -> System.out.println(i);\n" +
                "            case String s -> System.out.println(s);\n" +
                "            default -> System.out.println(\"def\");\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        cu = transformer.transform(cu);
        String transformed = cu.toString();

        assertFalse(transformed.contains("switch"), "Switch statement should be removed");
        assertTrue(transformed.contains("if (obj instanceof Integer)"), "Should have instanceof checks");
        assertTrue(transformed.contains("System.out.println(i)"), "Should execute block");
    }

    @Test
    @DisplayName("Transforms switch with guards to guarded if-else chain")
    void testSwitchWithGuards() {
        String source = "class Test {\n" +
                "    void method(Object obj) {\n" +
                "        String res = switch (obj) {\n" +
                "            case Integer i when i > 0 -> \"Positive\";\n" +
                "            case Integer i -> \"Zero or negative\";\n" +
                "            default -> \"unknown\";\n" +
                "        };\n" +
                "    }\n" +
                "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        cu = transformer.transform(cu);
        String transformed = cu.toString();

        assertFalse(transformed.contains("switch"), "Switch should be removed");
        assertTrue(transformed.contains("if (obj instanceof Integer && ((Integer) obj) > 0)"), "Guard condition should be integrated into if");
        assertTrue(transformed.contains("else if (obj instanceof Integer)"), "Should fall back to non-guarded");
    }
}
