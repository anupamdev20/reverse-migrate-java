package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class UnnamedVariableTransformerTest {
    
    private UnnamedVariableTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new UnnamedVariableTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    @Test
    @DisplayName("Transforms unnamed variables to named variables")
    void testUnnamedVariables() {
        // JavaParser 3.26 parser still correctly rejects '_' as a keyword even on JAVA_21
        // So we build an AST programmatically with '_' to test our transformer
        String source = "class Test {\n" +
                "    void method() {\n" +
                "        var dummy1 = getSomething();\n" +
                "        try {\n" +
                "            doThrow();\n" +
                "        } catch (Exception dummy2) {\n" +
                "        }\n" +
                "    }\n" +
                "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        
        // Inject '_' variable names
        cu.findAll(com.github.javaparser.ast.body.VariableDeclarator.class).forEach(vd -> {
            if (vd.getNameAsString().equals("dummy1")) vd.setName("_");
        });
        cu.findAll(com.github.javaparser.ast.body.Parameter.class).forEach(p -> {
            if (p.getNameAsString().equals("dummy2")) p.setName("_");
        });

        cu = transformer.transform(cu);
        String transformed = cu.toString();

        assertFalse(transformed.contains("var _ ="), "Unnamed var declaration should be replaced");
        assertTrue(transformed.contains("var _unused1 = getSomething()"), "Should use sequential unused name");
        
        assertFalse(transformed.contains("catch (Exception _)"), "Unnamed catch param should be replaced");
        assertTrue(transformed.contains("catch (Exception _unused2)"), "Should use sequential unused name");
    }
}
