package com.reversemigrate.transform.transformers;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class FlexibleConstructorTransformerTest {
    
    private FlexibleConstructorTransformer transformer;

    @BeforeEach
    void setUp() {
        transformer = new FlexibleConstructorTransformer();
        StaticJavaParser.getParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
    }

    @Test
    @DisplayName("Moves statements before super to after super")
    void testStatementsBeforeSuper() {
        // JavaParser 3.26 parser rejects statements before super() since it enforces older JLS grammar
        // So we build an AST programmatically to test our transformer
        CompilationUnit cu = new CompilationUnit();
        var cls = cu.addClass("Child").addExtendedType("Parent");
        var constructor = cls.addConstructor();
        constructor.addParameter("int", "x");
        
        var block = new com.github.javaparser.ast.stmt.BlockStmt();
        
        // 1. System.out.println("Before super");
        block.addStatement(new com.github.javaparser.ast.stmt.ExpressionStmt(
            new com.github.javaparser.ast.expr.MethodCallExpr(
                new com.github.javaparser.ast.expr.FieldAccessExpr(
                    new com.github.javaparser.ast.expr.NameExpr("System"), "out"),
                "println",
                new com.github.javaparser.ast.NodeList<>(new com.github.javaparser.ast.expr.StringLiteralExpr("Before super"))
            )
        ));
        
        // 2. validate(x);
        block.addStatement(new com.github.javaparser.ast.stmt.ExpressionStmt(
            new com.github.javaparser.ast.expr.MethodCallExpr(
                "validate",
                new com.github.javaparser.ast.expr.NameExpr("x")
            )
        ));
        
        // 3. super(x);
        var superCall = new com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt(
            false,
            null,
            new com.github.javaparser.ast.NodeList<>(new com.github.javaparser.ast.expr.NameExpr("x"))
        );
        block.addStatement(superCall);
        
        // 4. this.x = x;
        block.addStatement(new com.github.javaparser.ast.stmt.ExpressionStmt(
            new com.github.javaparser.ast.expr.AssignExpr(
                new com.github.javaparser.ast.expr.FieldAccessExpr(
                    new com.github.javaparser.ast.expr.ThisExpr(), "x"),
                new com.github.javaparser.ast.expr.NameExpr("x"),
                com.github.javaparser.ast.expr.AssignExpr.Operator.ASSIGN
            )
        ));
        
        constructor.setBody(block);

        cu = transformer.transform(cu);
        String transformed = cu.toString();

        int superCallIndex = transformed.indexOf("super(x);");
        int printlnIndex = transformed.indexOf("System.out.println(\"Before super\");");
        int validateIndex = transformed.indexOf("validate(x);");
        int assignmentIndex = transformed.indexOf("this.x = x;");

        assertTrue(superCallIndex > 0, "super() should still exist");
        assertTrue(superCallIndex < printlnIndex, "super() should come before println");
        assertTrue(superCallIndex < validateIndex, "super() should come before validate");
        assertTrue(printlnIndex < assignmentIndex, "println should still come before assignment");
    }
}
