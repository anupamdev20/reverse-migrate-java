package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.type.ArrayType;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.PrimitiveType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.VarType;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.List;

/**
 * Transforms JDK 10+ local variable type inference (var keyword)
 * into explicit type declarations by inspecting the initializer.
 *
 * Example:
 * Input: var list = new ArrayList<String>();
 * Output: ArrayList<String> list = new ArrayList<String>();
 *
 * Input: var x = 42;
 * Output: int x = 42;
 *
 * Input: var s = "hello";
 * Output: String s = "hello";
 */
public class VarTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "LOCAL_VAR_TYPE";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        List<VariableDeclarator> varDeclarators = cu.findAll(VariableDeclarator.class);

        for (VariableDeclarator varDecl : varDeclarators) {
            if (varDecl.getType() instanceof VarType) {
                Type resolvedType = inferType(varDecl);
                if (resolvedType != null) {
                    varDecl.setType(resolvedType);
                }
            }
        }

        return cu;
    }

    /**
     * Infers the explicit type from the initializer expression.
     */
    Type inferType(VariableDeclarator varDecl) {
        if (varDecl.getInitializer().isEmpty()) {
            return null; // var without initializer — shouldn't happen but guard
        }

        Expression init = varDecl.getInitializer().get();

        // new ClassName<...>() — use the class type
        if (init instanceof ObjectCreationExpr objectCreation) {
            return objectCreation.getType().clone();
        }

        // String literal
        if (init instanceof StringLiteralExpr) {
            return new ClassOrInterfaceType(null, "String");
        }

        // Character literal
        if (init instanceof CharLiteralExpr) {
            return PrimitiveType.charType();
        }

        // Boolean literal
        if (init instanceof BooleanLiteralExpr) {
            return PrimitiveType.booleanType();
        }

        // Integer literal
        if (init instanceof IntegerLiteralExpr intLit) {
            String value = intLit.getValue();
            if (value.endsWith("L") || value.endsWith("l")) {
                return PrimitiveType.longType();
            }
            return PrimitiveType.intType();
        }

        // Long literal
        if (init instanceof LongLiteralExpr) {
            return PrimitiveType.longType();
        }

        // Double literal
        if (init instanceof DoubleLiteralExpr doubleLit) {
            String value = doubleLit.getValue();
            if (value.endsWith("f") || value.endsWith("F")) {
                return PrimitiveType.floatType();
            }
            return PrimitiveType.doubleType();
        }

        // Null literal — can't infer type, leave as Object
        if (init instanceof NullLiteralExpr) {
            return new ClassOrInterfaceType(null, "Object");
        }

        // Cast expression — use the cast type
        if (init instanceof CastExpr castExpr) {
            return castExpr.getType().clone();
        }

        // Array creation
        if (init instanceof ArrayCreationExpr arrayCreation) {
            return new ArrayType(arrayCreation.getElementType().clone());
        }

        // Method call — without symbol solver, we annotate with a comment
        // and use Object as a fallback
        if (init instanceof MethodCallExpr) {
            ClassOrInterfaceType objType = new ClassOrInterfaceType(null, "Object");
            objType.setComment(new com.github.javaparser.ast.comments.LineComment(
                    " TODO: verify inferred type for var"));
            return objType;
        }

        // Conditional expression (ternary) — try to infer from then branch
        if (init instanceof ConditionalExpr conditional) {
            return inferTypeFromExpression(conditional.getThenExpr());
        }

        // Name expression (another variable) — can't resolve without symbol solver
        if (init instanceof NameExpr) {
            ClassOrInterfaceType objType = new ClassOrInterfaceType(null, "Object");
            objType.setComment(new com.github.javaparser.ast.comments.LineComment(
                    " TODO: verify inferred type for var"));
            return objType;
        }

        // Fallback — use Object with a TODO comment
        ClassOrInterfaceType fallback = new ClassOrInterfaceType(null, "Object");
        fallback.setComment(new com.github.javaparser.ast.comments.LineComment(
                " TODO: verify inferred type for var"));
        return fallback;
    }

    private Type inferTypeFromExpression(Expression expr) {
        if (expr instanceof StringLiteralExpr)
            return new ClassOrInterfaceType(null, "String");
        if (expr instanceof IntegerLiteralExpr)
            return PrimitiveType.intType();
        if (expr instanceof LongLiteralExpr)
            return PrimitiveType.longType();
        if (expr instanceof DoubleLiteralExpr)
            return PrimitiveType.doubleType();
        if (expr instanceof BooleanLiteralExpr)
            return PrimitiveType.booleanType();
        if (expr instanceof ObjectCreationExpr oce)
            return oce.getType().clone();
        return new ClassOrInterfaceType(null, "Object");
    }
}
