package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class RecordPatternTransformerTest {
    
    private RecordPatternTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new RecordPatternTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    @Test
    @DisplayName("Transforms record patterns in instanceof to explicit extraction")
    void testRecordPatternInstanceof() {
        String source = "class Test {\n" +
                "    void method(Object obj) {\n" +
                "        if (obj instanceof Point(int x, int y)) {\n" +
                "            System.out.println(x + y);\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        cu = transformer.transform(cu);
        String transformed = cu.toString();

        assertTrue(transformed.contains("if (obj instanceof Point)"), "Should check for record type");
        assertTrue(transformed.contains("Point _p = (Point) obj;"), "Should cast to temp variable");
        assertTrue(transformed.contains("int x = _p.x();"), "Should extract component x");
        assertTrue(transformed.contains("int y = _p.y();"), "Should extract component y");
        assertFalse(transformed.contains("Point(int x, int y)"), "Should not contain record pattern syntax");
    }
}
