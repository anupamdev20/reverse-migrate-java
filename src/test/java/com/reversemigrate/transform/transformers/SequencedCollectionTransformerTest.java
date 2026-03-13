package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class SequencedCollectionTransformerTest {
    
    private SequencedCollectionTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new SequencedCollectionTransformer();
    }

    @Test
    @DisplayName("Transforms sequenced collection API methods to generic equivalents")
    void testSequencedCollectionMethods() {
        String source = "class Test {\n" +
                "    void method(java.util.List<String> list) {\n" +
                "        String f = list.getFirst();\n" +
                "        String l = list.getLast();\n" +
                "        list.addFirst(\"a\");\n" +
                "        list.addLast(\"z\");\n" +
                "        list.removeFirst();\n" +
                "        list.removeLast();\n" +
                "    }\n" +
                "}\n";

        CompilationUnit cu = StaticJavaParser.parse(source);
        cu = transformer.transform(cu);
        String transformed = cu.toString();

        assertFalse(transformed.contains("getFirst"), "getFirst should be replaced");
        assertTrue(transformed.contains("list.get(0)"), "list.get(0) should be present");
        
        assertFalse(transformed.contains("getLast"), "getLast should be replaced");
        assertTrue(transformed.contains("list.get(list.size() - 1)"), "list.get(list.size() - 1) should be present");
        
        assertFalse(transformed.contains("addFirst"), "addFirst should be replaced");
        assertTrue(transformed.contains("list.add(0, \"a\")"), "list.add(0, ...) should be present");
        
        assertFalse(transformed.contains("addLast"), "addLast should be replaced");
        assertTrue(transformed.contains("list.add(\"z\")"), "list.add(e) should be present");
        
        assertFalse(transformed.contains("removeFirst"), "removeFirst should be replaced");
        assertTrue(transformed.contains("list.remove(0)"), "list.remove(0) should be present");
        
        assertFalse(transformed.contains("removeLast"), "removeLast should be replaced");
        assertTrue(transformed.contains("list.remove(list.size() - 1)"), "list.remove(list.size() - 1) should be present");
    }
}
