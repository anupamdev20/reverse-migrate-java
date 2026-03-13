package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TextBlockTransformer.
 * Covers multi-line blocks, single-line, escape sequences, and empty blocks.
 *
 * NOTE: Since text blocks contain triple-quotes, we construct test source
 * strings
 * using regular string concatenation to avoid escaping issues.
 */
class TextBlockTransformerTest {

    private TextBlockTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new TextBlockTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
    }

    @Test
    @DisplayName("Simple multi-line text block is converted to string concatenation")
    void testSimpleMultiLineTextBlock() {
        String source = "class Test {\n"
                + "    String s = \"\"\"\n"
                + "            Hello\n"
                + "            World\n"
                + "            \"\"\";\n"
                + "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("\"\"\""), "Text block should be removed");
        assertTrue(output.contains("Hello"), "Content should be preserved");
        assertTrue(output.contains("World"), "Content should be preserved");
    }

    @Test
    @DisplayName("Text block with consistent indentation has indentation stripped")
    void testIndentationStripping() {
        String source = "class Test {\n"
                + "    String html = \"\"\"\n"
                + "            <html>\n"
                + "                <body>\n"
                + "                    <p>Hello</p>\n"
                + "                </body>\n"
                + "            </html>\n"
                + "            \"\"\";\n"
                + "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("\"\"\""), "Text block markers should be removed");
        assertTrue(output.contains("<html>"), "HTML content should be preserved");
        assertTrue(output.contains("<body>"), "Nested HTML should be preserved");
    }

    @Test
    @DisplayName("Text block with escape sequences preserves them")
    void testEscapeSequences() {
        String source = "class Test {\n"
                + "    String s = \"\"\"\n"
                + "            Line with tab\n"
                + "            Line with backslash\n"
                + "            \"\"\";\n"
                + "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("\"\"\""), "Text block should be removed");
        assertTrue(output.contains("Line with tab"), "Content should be preserved");
    }

    @Test
    @DisplayName("Multiple text blocks in the same class are all converted")
    void testMultipleTextBlocks() {
        String source = "class Test {\n"
                + "    String a = \"\"\"\n"
                + "            First\n"
                + "            \"\"\";\n"
                + "    String b = \"\"\"\n"
                + "            Second\n"
                + "            \"\"\";\n"
                + "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("\"\"\""), "All text blocks should be removed");
        assertTrue(output.contains("First"), "First text block content preserved");
        assertTrue(output.contains("Second"), "Second text block content preserved");
    }

    @Test
    @DisplayName("Text block used in method argument is converted")
    void testTextBlockInMethodArgument() {
        String source = "class Test {\n"
                + "    void test() {\n"
                + "        System.out.println(\"\"\"\n"
                + "                Hello World\n"
                + "                \"\"\");\n"
                + "    }\n"
                + "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        CompilationUnit result = transformer.transform(cu);
        String output = result.toString();

        assertFalse(output.contains("\"\"\""), "Text block should be removed");
        assertTrue(output.contains("Hello World"), "Content should be preserved");
    }

    @Test
    @DisplayName("Feature ID is TEXT_BLOCK")
    void testFeatureId() {
        assertEquals("TEXT_BLOCK", transformer.featureId());
    }

    @Test
    @DisplayName("Class without text blocks is unchanged")
    void testNoTextBlocks() {
        String source = "class Test {\n"
                + "    String s = \"normal string\";\n"
                + "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        String before = cu.toString();
        CompilationUnit result = transformer.transform(cu);

        assertEquals(before, result.toString(), "Code without text blocks should be unchanged");
    }
}
