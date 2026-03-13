package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.type.Type;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms JDK 21+ pattern matching for switch into
 * if-else chains compatible with older JDK versions.
 *
 * Example:
 *   Input:
 *     String result = switch (obj) {
 *         case Integer i -> "int: " + i;
 *         case String s  -> "str: " + s;
 *         default        -> "other";
 *     };
 *
 *   Output:
 *     String result;
 *     if (obj instanceof Integer) {
 *         Integer i = (Integer) obj;
 *         result = "int: " + i;
 *     } else if (obj instanceof String) {
 *         String s = (String) obj;
 *         result = "str: " + s;
 *     } else {
 *         result = "other";
 *     }
 */
public class SwitchPatternMatchingTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "SWITCH_PATTERN_MATCHING";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        // Find switch expressions that use type patterns
        List<SwitchExpr> switchExprs = new ArrayList<>(cu.findAll(SwitchExpr.class));

        for (SwitchExpr switchExpr : switchExprs) {
            if (hasTypePatterns(switchExpr)) {
                transformPatternSwitch(switchExpr);
            }
        }

        // Also handle switch statements with patterns
        List<SwitchStmt> switchStmts = new ArrayList<>(cu.findAll(SwitchStmt.class));
        for (SwitchStmt switchStmt : switchStmts) {
            if (hasTypePatterns(switchStmt)) {
                transformPatternSwitchStmt(switchStmt);
            }
        }

        return cu;
    }

    private boolean hasTypePatterns(Node switchNode) {
        return !switchNode.findAll(TypePatternExpr.class).isEmpty() ||
               !switchNode.findAll(NullLiteralExpr.class).isEmpty();
    }

    private void transformPatternSwitch(SwitchExpr switchExpr) {
        if (switchExpr.getParentNode().isEmpty()) return;

        Node parent = switchExpr.getParentNode().get();

        if (parent instanceof VariableDeclarator varDecl) {
            transformPatternSwitchInVarDecl(switchExpr, varDecl);
        } else if (parent instanceof ReturnStmt returnStmt) {
            transformPatternSwitchInReturn(switchExpr, returnStmt);
        } else if (parent instanceof AssignExpr assignExpr) {
            transformPatternSwitchInAssignment(switchExpr, assignExpr);
        }
    }

    private void transformPatternSwitchInVarDecl(SwitchExpr switchExpr, VariableDeclarator varDecl) {
        var enclosingStmt = varDecl.getParentNode()
                .flatMap(Node::getParentNode)
                .filter(p -> p instanceof ExpressionStmt)
                .map(p -> (ExpressionStmt) p);

        if (enclosingStmt.isEmpty()) return;

        var block = enclosingStmt.get().getParentNode()
                .filter(p -> p instanceof BlockStmt)
                .map(p -> (BlockStmt) p);

        if (block.isEmpty()) return;

        String varName = varDecl.getNameAsString();
        Expression selector = switchExpr.getSelector();

        // Build if-else chain from switch entries
        IfStmt ifChain = buildIfElseChain(switchExpr.getEntries(), selector, varName, AssignmentTarget.VARIABLE);

        if (ifChain != null) {
            varDecl.removeInitializer();
            BlockStmt blockStmt = block.get();
            int index = blockStmt.getStatements().indexOf(enclosingStmt.get());
            if (index >= 0) {
                blockStmt.getStatements().add(index + 1, ifChain);
            }
        }
    }

    private void transformPatternSwitchInReturn(SwitchExpr switchExpr, ReturnStmt returnStmt) {
        var block = returnStmt.getParentNode()
                .filter(p -> p instanceof BlockStmt)
                .map(p -> (BlockStmt) p);

        if (block.isEmpty()) return;

        Expression selector = switchExpr.getSelector();
        IfStmt ifChain = buildIfElseChain(switchExpr.getEntries(), selector, null, AssignmentTarget.RETURN);

        if (ifChain != null) {
            returnStmt.replace(ifChain);
        }
    }

    private void transformPatternSwitchInAssignment(SwitchExpr switchExpr, AssignExpr assignExpr) {
        var exprStmt = assignExpr.getParentNode()
                .filter(p -> p instanceof ExpressionStmt)
                .map(p -> (ExpressionStmt) p);

        if (exprStmt.isEmpty()) return;

        var block = exprStmt.get().getParentNode()
                .filter(p -> p instanceof BlockStmt)
                .map(p -> (BlockStmt) p);

        if (block.isEmpty()) return;

        String targetName = assignExpr.getTarget().toString();
        Expression selector = switchExpr.getSelector();
        IfStmt ifChain = buildIfElseChain(switchExpr.getEntries(), selector, targetName, AssignmentTarget.VARIABLE);

        if (ifChain != null) {
            exprStmt.get().replace(ifChain);
        }
    }

    private void transformPatternSwitchStmt(SwitchStmt switchStmt) {
        var block = switchStmt.getParentNode()
                .filter(p -> p instanceof BlockStmt)
                .map(p -> (BlockStmt) p);

        if (block.isEmpty()) return;

        Expression selector = switchStmt.getSelector();
        IfStmt ifChain = buildIfElseChainForStmt(switchStmt.getEntries(), selector);

        if (ifChain != null) {
            switchStmt.replace(ifChain);
        }
    }

    private IfStmt buildIfElseChain(List<SwitchEntry> entries, Expression selector, String targetName, AssignmentTarget target) {
        IfStmt firstIf = null;
        IfStmt currentIf = null;

        for (SwitchEntry entry : entries) {
            if (entry.getLabels().isEmpty()) {
                // Default branch
                BlockStmt defaultBlock = new BlockStmt();
                for (Statement stmt : entry.getStatements()) {
                    if (stmt instanceof ExpressionStmt exprStmt) {
                        addAssignmentOrReturn(defaultBlock, exprStmt.getExpression(), targetName, target);
                    } else if (stmt instanceof BlockStmt blk) {
                        processBlockForAssignment(blk, targetName, target, defaultBlock);
                    } else {
                        defaultBlock.addStatement(stmt.clone());
                    }
                }
                if (currentIf != null) {
                    currentIf.setElseStmt(defaultBlock);
                }
                continue;
            }

            for (Expression label : entry.getLabels()) {
                if (label instanceof TypePatternExpr typePattern) {
                    String patternVar = typePattern.getNameAsString();
                    Type patternType = typePattern.getType();

                    InstanceOfExpr instanceCheck = new InstanceOfExpr();
                    instanceCheck.setExpression(selector.clone());
                    instanceCheck.setType((ReferenceType) patternType.clone());

                    Expression condition = instanceCheck;
                    if (entry.getGuard().isPresent()) {
                        Expression guard = entry.getGuard().get().clone();
                        // Inline variable cast for the guard condition
                        guard.findAll(NameExpr.class).forEach(n -> {
                            if (n.getNameAsString().equals(patternVar)) {
                                n.replace(new EnclosedExpr(new CastExpr(patternType.clone(), selector.clone())));
                            }
                        });
                        condition = new BinaryExpr(instanceCheck, guard, BinaryExpr.Operator.AND);
                    }

                    BlockStmt thenBlock = new BlockStmt();
                    // Add cast: Type var = (Type) selector;
                    CastExpr cast = new CastExpr(patternType.clone(), selector.clone());
                    VariableDeclarator castVar = new VariableDeclarator(patternType.clone(), patternVar, cast);
                    thenBlock.addStatement(new ExpressionStmt(new VariableDeclarationExpr(castVar)));

                    // Add the body
                    for (Statement stmt : entry.getStatements()) {
                        if (stmt instanceof ExpressionStmt exprStmt) {
                            addAssignmentOrReturn(thenBlock, exprStmt.getExpression(), targetName, target);
                        } else if (stmt instanceof BlockStmt blk) {
                            processBlockForAssignment(blk, targetName, target, thenBlock);
                        } else {
                            thenBlock.addStatement(stmt.clone());
                        }
                    }

                    IfStmt newIf = new IfStmt(condition, thenBlock, null);
                    if (firstIf == null) {
                        firstIf = newIf;
                    } else if (currentIf != null) {
                        currentIf.setElseStmt(newIf);
                    }
                    currentIf = newIf;
                } else if (label instanceof NullLiteralExpr) {
                    BinaryExpr nullCheck = new BinaryExpr(selector.clone(), new NullLiteralExpr(), BinaryExpr.Operator.EQUALS);
                    
                    BlockStmt thenBlock = new BlockStmt();
                    
                    // Add the body
                    for (Statement stmt : entry.getStatements()) {
                        if (stmt instanceof ExpressionStmt exprStmt) {
                            addAssignmentOrReturn(thenBlock, exprStmt.getExpression(), targetName, target);
                        } else if (stmt instanceof BlockStmt blk) {
                            processBlockForAssignment(blk, targetName, target, thenBlock);
                        } else {
                            thenBlock.addStatement(stmt.clone());
                        }
                    }

                    IfStmt newIf = new IfStmt(nullCheck, thenBlock, null);
                    if (firstIf == null) {
                        firstIf = newIf;
                    } else if (currentIf != null) {
                        currentIf.setElseStmt(newIf);
                    }
                    currentIf = newIf;
                }
            }
        }

        return firstIf;
    }

    private IfStmt buildIfElseChainForStmt(List<SwitchEntry> entries, Expression selector) {
        IfStmt firstIf = null;
        IfStmt currentIf = null;

        for (SwitchEntry entry : entries) {
            if (entry.getLabels().isEmpty()) {
                BlockStmt defaultBlock = new BlockStmt();
                for (Statement stmt : entry.getStatements()) {
                    if (!(stmt instanceof BreakStmt)) {
                        defaultBlock.addStatement(stmt.clone());
                    }
                }
                if (currentIf != null) {
                    currentIf.setElseStmt(defaultBlock);
                }
                continue;
            }

            for (Expression label : entry.getLabels()) {
                if (label instanceof TypePatternExpr typePattern) {
                    String patternVar = typePattern.getNameAsString();
                    Type patternType = typePattern.getType();

                    InstanceOfExpr instanceCheck = new InstanceOfExpr();
                    instanceCheck.setExpression(selector.clone());
                    instanceCheck.setType((ReferenceType) patternType.clone());

                    Expression condition = instanceCheck;
                    if (entry.getGuard().isPresent()) {
                        Expression guard = entry.getGuard().get().clone();
                        guard.findAll(NameExpr.class).forEach(n -> {
                            if (n.getNameAsString().equals(patternVar)) {
                                n.replace(new EnclosedExpr(new CastExpr(patternType.clone(), selector.clone())));
                            }
                        });
                        condition = new BinaryExpr(instanceCheck, guard, BinaryExpr.Operator.AND);
                    }

                    BlockStmt thenBlock = new BlockStmt();
                    CastExpr cast = new CastExpr(patternType.clone(), selector.clone());
                    VariableDeclarator castVar = new VariableDeclarator(patternType.clone(), patternVar, cast);
                    thenBlock.addStatement(new ExpressionStmt(new VariableDeclarationExpr(castVar)));

                    for (Statement stmt : entry.getStatements()) {
                        if (!(stmt instanceof BreakStmt)) {
                            thenBlock.addStatement(stmt.clone());
                        }
                    }

                    IfStmt newIf = new IfStmt(condition, thenBlock, null);
                    if (firstIf == null) {
                        firstIf = newIf;
                    } else if (currentIf != null) {
                        currentIf.setElseStmt(newIf);
                    }
                    currentIf = newIf;
                } else if (label instanceof NullLiteralExpr) {
                    BinaryExpr nullCheck = new BinaryExpr(selector.clone(), new NullLiteralExpr(), BinaryExpr.Operator.EQUALS);
                    
                    BlockStmt thenBlock = new BlockStmt();
                    for (Statement stmt : entry.getStatements()) {
                        if (!(stmt instanceof BreakStmt)) {
                            thenBlock.addStatement(stmt.clone());
                        }
                    }

                    IfStmt newIf = new IfStmt(nullCheck, thenBlock, null);
                    if (firstIf == null) {
                        firstIf = newIf;
                    } else if (currentIf != null) {
                        currentIf.setElseStmt(newIf);
                    }
                    currentIf = newIf;
                }
            }
        }

        return firstIf;
    }

    private void processBlockForAssignment(BlockStmt source, String targetName, AssignmentTarget target, BlockStmt targetBlock) {
        for (Statement stmt : source.getStatements()) {
            if (stmt instanceof YieldStmt yieldStmt) {
                addAssignmentOrReturn(targetBlock, yieldStmt.getExpression(), targetName, target);
            } else {
                targetBlock.addStatement(stmt.clone());
            }
        }
    }

    private void addAssignmentOrReturn(BlockStmt block, Expression expr, String targetName, AssignmentTarget target) {
        if (target == AssignmentTarget.RETURN) {
            block.addStatement(new ReturnStmt(expr.clone()));
        } else {
            block.addStatement(new ExpressionStmt(new AssignExpr(
                    new NameExpr(targetName),
                    expr.clone(),
                    AssignExpr.Operator.ASSIGN)));
        }
    }

    private enum AssignmentTarget {
        VARIABLE,
        RETURN
    }
}
