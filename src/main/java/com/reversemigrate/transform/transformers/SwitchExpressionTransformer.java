package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.List;

/**
 * Transforms JDK 14+ switch expressions (arrow-form with yield)
 * into traditional switch statements compatible with older JDK versions.
 *
 * Example:
 * Input:
 * int result = switch (day) {
 * case MONDAY, FRIDAY -> 6;
 * case TUESDAY -> 7;
 * default -> 0;
 * };
 *
 * Output:
 * int result;
 * switch (day) {
 * case MONDAY:
 * case FRIDAY:
 * result = 6;
 * break;
 * case TUESDAY:
 * result = 7;
 * break;
 * default:
 * result = 0;
 * break;
 * }
 */
public class SwitchExpressionTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "SWITCH_EXPRESSION";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        // Find all switch expressions used in variable declarations
        // e.g., int x = switch(...) { ... };
        List<SwitchExpr> switchExprs = cu.findAll(SwitchExpr.class);

        for (SwitchExpr switchExpr : switchExprs) {
            if (!hasTypePatterns(switchExpr)) {
                transformSwitchExpr(switchExpr);
            }
        }

        return cu;
    }

    private boolean hasTypePatterns(SwitchExpr switchExpr) {
        return !switchExpr.findAll(com.github.javaparser.ast.expr.TypePatternExpr.class).isEmpty() ||
               switchExpr.getEntries().stream().anyMatch(e -> e.getGuard().isPresent());
    }

    private void transformSwitchExpr(SwitchExpr switchExpr) {
        // Check if the switch expression is used as a variable initializer
        if (switchExpr.getParentNode().isEmpty()) {
            return;
        }

        var parent = switchExpr.getParentNode().get();

        if (parent instanceof VariableDeclarator varDecl) {
            transformSwitchInVariableDecl(switchExpr, varDecl);
        } else if (parent instanceof AssignExpr assignExpr) {
            transformSwitchInAssignment(switchExpr, assignExpr);
        } else if (parent instanceof ReturnStmt returnStmt) {
            transformSwitchInReturn(switchExpr, returnStmt);
        }
    }

    private void transformSwitchInVariableDecl(SwitchExpr switchExpr, VariableDeclarator varDecl) {
        // Find the enclosing statement
        var exprStmt = varDecl.getParentNode()
                .flatMap(p -> p.getParentNode())
                .filter(p -> p instanceof ExpressionStmt)
                .map(p -> (ExpressionStmt) p);

        if (exprStmt.isEmpty()) {
            return;
        }

        // Find the block that contains this statement
        var block = exprStmt.get().getParentNode()
                .filter(p -> p instanceof BlockStmt)
                .map(p -> (BlockStmt) p);

        if (block.isEmpty()) {
            return;
        }

        String varName = varDecl.getNameAsString();
        SwitchStmt switchStmt = buildSwitchStatement(switchExpr, varName, AssignmentTarget.VARIABLE);

        // Remove the initializer from var decl (just declare the variable)
        varDecl.removeInitializer();

        // Insert the switch statement after the variable declaration
        BlockStmt blockStmt = block.get();
        int index = blockStmt.getStatements().indexOf(exprStmt.get());
        if (index >= 0) {
            blockStmt.getStatements().add(index + 1, switchStmt);
        }
    }

    private void transformSwitchInAssignment(SwitchExpr switchExpr, AssignExpr assignExpr) {
        var exprStmt = assignExpr.getParentNode()
                .filter(p -> p instanceof ExpressionStmt)
                .map(p -> (ExpressionStmt) p);

        if (exprStmt.isEmpty())
            return;

        var block = exprStmt.get().getParentNode()
                .filter(p -> p instanceof BlockStmt)
                .map(p -> (BlockStmt) p);

        if (block.isEmpty())
            return;

        String targetName = assignExpr.getTarget().toString();
        SwitchStmt switchStmt = buildSwitchStatement(switchExpr, targetName, AssignmentTarget.VARIABLE);

        // Replace the assignment statement with the switch statement
        exprStmt.get().replace(switchStmt);
    }

    private void transformSwitchInReturn(SwitchExpr switchExpr, ReturnStmt returnStmt) {
        var block = returnStmt.getParentNode()
                .filter(p -> p instanceof BlockStmt)
                .map(p -> (BlockStmt) p);

        if (block.isEmpty())
            return;

        SwitchStmt switchStmt = buildSwitchStatement(switchExpr, null, AssignmentTarget.RETURN);

        // Replace return with switch that has return statements in each case
        returnStmt.replace(switchStmt);
    }

    private SwitchStmt buildSwitchStatement(SwitchExpr switchExpr, String varName,
            AssignmentTarget target) {
        SwitchStmt switchStmt = new SwitchStmt();
        switchStmt.setSelector(switchExpr.getSelector());

        NodeList<SwitchEntry> newEntries = new NodeList<>();

        for (SwitchEntry entry : switchExpr.getEntries()) {
            // For arrow entries with multiple labels, create one entry per label
            // (traditional switch needs separate case labels)
            List<Expression> labels = entry.getLabels();

            if (labels.size() > 1) {
                // Multiple labels: case A, B -> val; becomes case A: case B: val; break;
                for (int i = 0; i < labels.size() - 1; i++) {
                    SwitchEntry fallthrough = new SwitchEntry();
                    fallthrough.setLabels(new NodeList<>(labels.get(i)));
                    fallthrough.setType(SwitchEntry.Type.STATEMENT_GROUP);
                    newEntries.add(fallthrough);
                }
                // Last label gets the actual body
                SwitchEntry lastEntry = createTraditionalEntry(
                        labels.get(labels.size() - 1), entry, varName, target);
                newEntries.add(lastEntry);
            } else {
                // Single label or default
                Expression label = labels.isEmpty() ? null : labels.get(0);
                SwitchEntry newEntry = createTraditionalEntry(label, entry, varName, target);
                newEntries.add(newEntry);
            }
        }

        switchStmt.setEntries(newEntries);
        return switchStmt;
    }

    private SwitchEntry createTraditionalEntry(Expression label, SwitchEntry originalEntry,
            String varName, AssignmentTarget target) {
        SwitchEntry newEntry = new SwitchEntry();
        if (label != null) {
            newEntry.setLabels(new NodeList<>(label));
        }
        newEntry.setType(SwitchEntry.Type.STATEMENT_GROUP);

        NodeList<Statement> statements = new NodeList<>();

        // Extract the value from the arrow entry
        if (!originalEntry.getStatements().isEmpty()) {
            Statement stmt = originalEntry.getStatements().get(0);

            if (stmt instanceof ExpressionStmt exprStmt) {
                Expression expr = exprStmt.getExpression();
                addAssignmentOrReturn(statements, expr, varName, target);
            } else if (stmt instanceof BlockStmt blockStmt) {
                // Block body: look for yield statements and replace with assignments
                for (Statement s : blockStmt.getStatements()) {
                    if (s instanceof YieldStmt yieldStmt) {
                        addAssignmentOrReturn(statements, yieldStmt.getExpression(), varName, target);
                    } else {
                        statements.add(s.clone());
                    }
                }
            } else if (stmt instanceof ThrowStmt) {
                statements.add(stmt.clone());
            } else {
                statements.add(stmt.clone());
                statements.add(new BreakStmt().removeLabel());
            }
        }

        newEntry.setStatements(statements);
        return newEntry;
    }

    private void addAssignmentOrReturn(NodeList<Statement> statements, Expression expr,
            String varName, AssignmentTarget target) {
        if (target == AssignmentTarget.RETURN) {
            statements.add(new ReturnStmt(expr.clone()));
        } else {
            statements.add(new ExpressionStmt(new AssignExpr(
                    new NameExpr(varName),
                    expr.clone(),
                    AssignExpr.Operator.ASSIGN)));
            statements.add(new BreakStmt().removeLabel());
        }
    }

    private enum AssignmentTarget {
        VARIABLE,
        RETURN
    }
}
