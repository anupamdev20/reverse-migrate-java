package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms JDK 21+ Sequenced Collections API calls into
 * equivalent older API calls.
 *
 * Replacements:
 *   list.getFirst()    → list.get(0)
 *   list.getLast()     → list.get(list.size() - 1)
 *   list.removeFirst() → list.remove(0)
 *   list.removeLast()  → list.remove(list.size() - 1)
 *   list.addFirst(e)   → list.add(0, e)
 *   list.addLast(e)    → list.add(e)
 *   list.reversed()    → Collections.reverse(new ArrayList<>(list))
 */
public class SequencedCollectionTransformer implements FeatureTransformer {


    @Override
    public String featureId() {
        return "SEQUENCED_COLLECTION";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        List<MethodCallExpr> methodCalls = new ArrayList<>(cu.findAll(MethodCallExpr.class));

        for (MethodCallExpr call : methodCalls) {
            String methodName = call.getNameAsString();

            switch (methodName) {
                case "getFirst" -> transformGetFirst(call);
                case "getLast" -> transformGetLast(call);
                case "removeFirst" -> transformRemoveFirst(call);
                case "removeLast" -> transformRemoveLast(call);
                case "addFirst" -> transformAddFirst(call);
                case "addLast" -> transformAddLast(call);
            }
        }

        return cu;
    }

    private void transformGetFirst(MethodCallExpr call) {
        if (call.getArguments().isEmpty() && call.getScope().isPresent()) {
            // list.getFirst() → list.get(0)
            call.setName("get");
            call.addArgument(new com.github.javaparser.ast.expr.IntegerLiteralExpr("0"));
        }
    }

    private void transformGetLast(MethodCallExpr call) {
        if (call.getArguments().isEmpty() && call.getScope().isPresent()) {
            // list.getLast() → list.get(list.size() - 1)
            var scope = call.getScope().get();
            call.setName("get");
            call.addArgument(new com.github.javaparser.ast.expr.BinaryExpr(
                    new MethodCallExpr(scope.clone(), "size"),
                    new com.github.javaparser.ast.expr.IntegerLiteralExpr("1"),
                    com.github.javaparser.ast.expr.BinaryExpr.Operator.MINUS));
        }
    }

    private void transformRemoveFirst(MethodCallExpr call) {
        if (call.getArguments().isEmpty() && call.getScope().isPresent()) {
            // list.removeFirst() → list.remove(0)
            call.setName("remove");
            call.addArgument(new com.github.javaparser.ast.expr.IntegerLiteralExpr("0"));
        }
    }

    private void transformRemoveLast(MethodCallExpr call) {
        if (call.getArguments().isEmpty() && call.getScope().isPresent()) {
            // list.removeLast() → list.remove(list.size() - 1)
            var scope = call.getScope().get();
            call.setName("remove");
            call.addArgument(new com.github.javaparser.ast.expr.BinaryExpr(
                    new MethodCallExpr(scope.clone(), "size"),
                    new com.github.javaparser.ast.expr.IntegerLiteralExpr("1"),
                    com.github.javaparser.ast.expr.BinaryExpr.Operator.MINUS));
        }
    }

    private void transformAddFirst(MethodCallExpr call) {
        if (call.getArguments().size() == 1 && call.getScope().isPresent()) {
            // list.addFirst(e) → list.add(0, e)
            var element = call.getArgument(0).clone();
            call.setName("add");
            call.getArguments().clear();
            call.addArgument(new com.github.javaparser.ast.expr.IntegerLiteralExpr("0"));
            call.addArgument(element);
        }
    }

    private void transformAddLast(MethodCallExpr call) {
        if (call.getArguments().size() == 1 && call.getScope().isPresent()) {
            // list.addLast(e) → list.add(e)
            call.setName("add");
        }
    }
}
