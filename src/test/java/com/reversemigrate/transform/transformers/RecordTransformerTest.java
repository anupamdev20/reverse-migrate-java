package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for RecordTransformer.
 * Covers simple records, records with multiple components, custom methods, and
 * empty records.
 */
class RecordTransformerTest {

    private RecordTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new RecordTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Test
    @DisplayName("Simple record with two components is converted to class")
    void testSimpleRecord() {
        String source = """
                public record Point(int x, int y) {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        // Should not contain 'record' keyword
        assertFalse(output.contains("record Point"), "record keyword should be removed");

        // Should have class declaration
        assertTrue(output.contains("class Point"), "Should be converted to a class");
        assertTrue(output.contains("final"), "Class should be final");

        // Should have private final fields
        assertTrue(output.contains("private final int x"), "Should have field x");
        assertTrue(output.contains("private final int y"), "Should have field y");

        // Should have constructor
        assertTrue(output.contains("public Point(int x, int y)"), "Should have constructor");
        assertTrue(output.contains("this.x = x"), "Constructor should assign x");
        assertTrue(output.contains("this.y = y"), "Constructor should assign y");

        // Should have accessor methods
        assertTrue(output.contains("public int x()"), "Should have accessor x()");
        assertTrue(output.contains("public int y()"), "Should have accessor y()");

        // Should have equals, hashCode, toString
        assertTrue(output.contains("public boolean equals(Object o)"), "Should have equals");
        assertTrue(output.contains("public int hashCode()"), "Should have hashCode");
        assertTrue(output.contains("public String toString()"), "Should have toString");
    }

    @Test
    @DisplayName("Record with Object type components generates correct equals")
    void testRecordWithObjectComponents() {
        String source = """
                public record Person(String name, int age) {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        // String comparison should use Objects.equals
        assertTrue(output.contains("Objects.equals(name, that.name)"),
                "String fields should use Objects.equals");
        // int comparison should use ==
        assertTrue(output.contains("age == that.age"),
                "Primitive fields should use ==");
    }

    @Test
    @DisplayName("Record with custom method preserves it")
    void testRecordWithCustomMethod() {
        String source = """
                public record Rectangle(double width, double height) {
                    public double area() {
                        return width * height;
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("public double area()"), "Custom method should be preserved");
        assertTrue(output.contains("width * height"), "Custom method body should be preserved");
    }

    @Test
    @DisplayName("Record with single component is converted correctly")
    void testSingleComponentRecord() {
        String source = """
                public record Wrapper(String value) {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("private final String value"), "Should have field");
        assertTrue(output.contains("public Wrapper(String value)"), "Should have constructor");
        assertTrue(output.contains("public String value()"), "Should have accessor");
    }

    @Test
    @DisplayName("Record implementing interface preserves it")
    void testRecordImplementingInterface() {
        String source = """
                public record NamedPoint(String name, int x, int y) implements Comparable<NamedPoint> {
                    @Override
                    public int compareTo(NamedPoint other) {
                        return this.name.compareTo(other.name);
                    }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("implements Comparable<NamedPoint>"),
                "Interface implementation should be preserved");
        assertTrue(output.contains("compareTo"), "Custom method should be preserved");
    }

    @Test
    @DisplayName("Empty record (no components) is converted correctly")
    void testEmptyRecord() {
        String source = """
                public record Empty() {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("class Empty"), "Should be converted to class");
        assertTrue(output.contains("public Empty()"), "Should have empty constructor");
        assertTrue(output.contains("return 0"), "hashCode for empty record should return 0");
    }

    @Test
    @DisplayName("Feature ID is RECORD")
    void testFeatureId() {
        assertEquals("RECORD", transformer.featureId());
    }

    @Test
    @DisplayName("Class without records is unchanged")
    void testNoRecords() {
        String source = """
                public class Regular {
                    private int x;
                    public Regular(int x) { this.x = x; }
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        String before = cu.toString();
        CompilationUnit result = transformer.transform(cu);

        assertEquals(before, result.toString(), "Code without records should be unchanged");
    }

    @Test
    @DisplayName("Record toString contains record name and field names")
    void testToStringFormat() {
        String source = """
                public record Color(int r, int g, int b) {
                }
                """;

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertTrue(output.contains("\"Color[\""), "toString should contain record name");
        assertTrue(output.contains("\"r=\""), "toString should contain field name r");
        assertTrue(output.contains("\"g=\""), "toString should contain field name g");
        assertTrue(output.contains("\"b=\""), "toString should contain field name b");
    }
}
