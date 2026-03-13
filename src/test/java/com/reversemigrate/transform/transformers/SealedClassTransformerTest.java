package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for SealedClassTransformer.
 * Covers sealed, non-sealed, and permits clause removal.
 */
class SealedClassTransformerTest {

    private SealedClassTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new SealedClassTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Test
    @DisplayName("sealed modifier is removed from class")
    void testSealedModifierRemoved() {
        String source = """
                public sealed class Shape permits Circle, Rectangle {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("sealed"), "sealed modifier should be removed");
        assertFalse(output.contains("permits"), "permits clause should be removed");
        assertTrue(output.contains("public class Shape"), "Class declaration should remain");
    }

    @Test
    @DisplayName("non-sealed modifier is removed from subclass")
    void testNonSealedModifierRemoved() {
        String source = """
                public non-sealed class Circle extends Shape {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("non-sealed"), "non-sealed modifier should be removed");
        assertTrue(output.contains("extends Shape"), "Extends clause should remain");
    }

    @Test
    @DisplayName("sealed interface modifier is removed")
    void testSealedInterface() {
        String source = """
                public sealed interface Shape permits Circle, Rectangle {
                    double area();
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("sealed"), "sealed should be removed");
        assertFalse(output.contains("permits"), "permits should be removed");
        assertTrue(output.contains("double area()"), "Method should remain");
    }

    @Test
    @DisplayName("Code without sealed classes is unchanged")
    void testNoSealedClasses() {
        String source = """
                public class Regular {
                    private int x;
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        String before = cu.toString();
        CompilationUnit result = transformer.transform(cu);

        assertEquals(before, result.toString(), "Regular classes should be unchanged");
    }

    @Test
    @DisplayName("Feature ID is SEALED_CLASS")
    void testFeatureId() {
        assertEquals("SEALED_CLASS", transformer.featureId());
    }

    @Test
    @DisplayName("Multiple sealed and non-sealed classes in same file")
    void testMultipleSealedClasses() {
        String source = """
                public sealed class Shape permits Circle {
                }

                class Circle extends Shape {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("sealed"), "All sealed modifiers should be removed");
        assertFalse(output.contains("permits"), "All permits clauses should be removed");
    }
}
