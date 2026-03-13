package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.IfStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Transforms JDK 16+ pattern matching for instanceof into
 * explicit instanceof check + cast compatible with older JDK versions.
 *
 * Example:
 * Input: if (obj instanceof String s) { System.out.println(s.length()); }
 * Output: if (obj instanceof String) { String s = (String) obj;
 * System.out.println(s.length()); }
 */
public class PatternMatchingTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "PATTERN_MATCHING_INSTANCEOF";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        // Find all TypePatternExpr nodes (the concrete pattern type in JavaParser
        // 3.26+)
        List<TypePatternExpr> patternExprs = cu.findAll(TypePatternExpr.class);

        // Process in reverse order to avoid index shifting issues
        List<TypePatternExpr> reversed = new ArrayList<>(patternExprs);
        java.util.Collections.reverse(reversed);

        for (TypePatternExpr patternExpr : reversed) {
            transformPatternInstanceof(patternExpr);
        }

        return cu;
    }

    private void transformPatternInstanceof(TypePatternExpr patternExpr) {
        // The TypePatternExpr should be inside an InstanceOfExpr
        Optional<Node> parent = patternExpr.getParentNode();
        if (parent.isEmpty() || !(parent.get() instanceof InstanceOfExpr instanceOfExpr)) {
            return;
        }

        String varName = patternExpr.getNameAsString();
        Type patternType = patternExpr.getType();
        Expression checkedExpr = instanceOfExpr.getExpression();

        // Replace the pattern instanceof with a plain instanceof
        InstanceOfExpr plainInstanceOf = new InstanceOfExpr();
        plainInstanceOf.setExpression(checkedExpr.clone());
        plainInstanceOf.setType((ReferenceType) patternType.clone());
        instanceOfExpr.replace(plainInstanceOf);

        // Now find the enclosing if-statement to insert the cast variable
        Optional<IfStmt> enclosingIf = findEnclosingIf(plainInstanceOf);
        if (enclosingIf.isPresent()) {
            IfStmt ifStmt = enclosingIf.get();
            Statement thenStmt = ifStmt.getThenStmt();

            // Create the cast variable declaration:
            // Type varName = (Type) checkedExpr;
            CastExpr cast = new CastExpr(patternType.clone(), checkedExpr.clone());
            VariableDeclarator varDecl = new VariableDeclarator(
                    patternType.clone(), varName, cast);
            VariableDeclarationExpr varDeclExpr = new VariableDeclarationExpr(varDecl);
            ExpressionStmt declStmt = new ExpressionStmt(varDeclExpr);

            if (thenStmt instanceof BlockStmt blockStmt) {
                // Insert at the beginning of the block
                blockStmt.getStatements().addFirst(declStmt);
            } else {
                // Single statement — wrap in a block
                BlockStmt newBlock = new BlockStmt();
                newBlock.addStatement(declStmt);
                newBlock.addStatement(thenStmt.clone());
                ifStmt.setThenStmt(newBlock);
            }
        }
    }

    private Optional<IfStmt> findEnclosingIf(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof IfStmt ifStmt) {
                return Optional.of(ifStmt);
            }
            current = current.getParentNode().orElse(null);
        }
        return Optional.empty();
    }
}
