package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CompactSourceFileTransformerTest {
    
    private CompactSourceFileTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new CompactSourceFileTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21); // JavaParser 3.26 uses Java 21+ for unnamed/compact parsing
    }

    @Test
    @DisplayName("Transforms instance main method to public static void main(String[] args)")
    void testInstanceMainMethod() {
        // JavaParser 3.26 parser accepts this inside a class as a normal method
        String source = "class Main {\n" +
                "    void main() {\n" +
                "        System.out.println(\"Hello\");\n" +
                "    }\n" +
                "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        cu = transformer.transform(cu);
        String transformed = cu.toString();

        assertTrue(transformed.contains("public static void main(String[] args)"), "main should be public static void main(String[] args)");
        assertFalse(transformed.contains("public static public"), "Should not add duplicate modifiers");
    }
}
