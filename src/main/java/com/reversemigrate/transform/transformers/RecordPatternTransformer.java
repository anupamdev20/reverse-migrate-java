package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.Type;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms JDK 21+ record patterns into explicit deconstruction.
 *
 * Example:
 *   Input:  if (obj instanceof Point(int x, int y)) { use(x, y); }
 *   Output: if (obj instanceof Point) { Point _p = (Point) obj; int x = _p.x(); int y = _p.y(); use(x, y); }
 *
 * Also handles record patterns in switch statements.
 */
public class RecordPatternTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "RECORD_PATTERN";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        // Find all RecordPatternExpr nodes
        List<RecordPatternExpr> recordPatterns = new ArrayList<>(cu.findAll(RecordPatternExpr.class));

        // Process in reverse to avoid index issues
        java.util.Collections.reverse(recordPatterns);

        for (RecordPatternExpr recordPattern : recordPatterns) {
            transformRecordPattern(recordPattern);
        }

        return cu;
    }

    private void transformRecordPattern(RecordPatternExpr recordPattern) {
        Node parent = recordPattern.getParentNode().orElse(null);
        if (parent == null) return;

        if (parent instanceof InstanceOfExpr instanceOfExpr) {
            transformInInstanceOf(recordPattern, instanceOfExpr);
        }
        // Record patterns in switch are handled by SwitchPatternMatchingTransformer
    }

    private void transformInInstanceOf(RecordPatternExpr recordPattern,
                                        InstanceOfExpr instanceOfExpr) {
        Type recordType = recordPattern.getType();
        Expression checkedExpr = instanceOfExpr.getExpression();
        List<PatternExpr> componentPatterns = recordPattern.getPatternList();

        // Replace the record pattern instanceof with plain instanceof
        InstanceOfExpr plainInstanceOf = new InstanceOfExpr();
        plainInstanceOf.setExpression(checkedExpr.clone());
        plainInstanceOf.setType(
                (com.github.javaparser.ast.type.ReferenceType) recordType.clone());
        instanceOfExpr.replace(plainInstanceOf);

        // Find enclosing if-statement
        IfStmt enclosingIf = findEnclosingIf(plainInstanceOf);
        if (enclosingIf == null) return;

        Statement thenStmt = enclosingIf.getThenStmt();
        BlockStmt thenBlock;
        if (thenStmt instanceof BlockStmt block) {
            thenBlock = block;
        } else {
            thenBlock = new BlockStmt();
            thenBlock.addStatement(thenStmt.clone());
            enclosingIf.setThenStmt(thenBlock);
        }

        // Generate a temp variable: RecordType _temp = (RecordType) checkedExpr;
        String tempVar = "_" + recordType.asString().toLowerCase().charAt(0);
        CastExpr cast = new CastExpr(recordType.clone(), checkedExpr.clone());
        VariableDeclarator tempDecl = new VariableDeclarator(recordType.clone(), tempVar, cast);
        thenBlock.getStatements().addFirst(
                new ExpressionStmt(new VariableDeclarationExpr(tempDecl)));

        // For each component pattern, add: ComponentType name = _temp.name();
        int insertIdx = 1;
        for (int i = 0; i < componentPatterns.size(); i++) {
            PatternExpr componentPattern = componentPatterns.get(i);
            if (componentPattern instanceof TypePatternExpr typePattern) {
                String componentName = typePattern.getNameAsString();
                Type componentType = typePattern.getType();

                // Call the accessor method (record component name)
                MethodCallExpr accessor = new MethodCallExpr(
                        new NameExpr(tempVar), componentName);
                VariableDeclarator compDecl = new VariableDeclarator(
                        componentType.clone(), componentName, accessor);
                thenBlock.getStatements().add(insertIdx++,
                        new ExpressionStmt(new VariableDeclarationExpr(compDecl)));
            }
        }
    }

    private IfStmt findEnclosingIf(Node node) {
        Node current = node;
        while (current != null) {
            if (current instanceof IfStmt ifStmt) {
                return ifStmt;
            }
            current = current.getParentNode().orElse(null);
        }
        return null;
    }
}
