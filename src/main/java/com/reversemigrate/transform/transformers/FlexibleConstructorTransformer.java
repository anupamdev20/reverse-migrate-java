package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExplicitConstructorInvocationStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms JDK 25+ flexible constructor bodies back to standard form.
 * In JDK 25, statements can appear before the explicit super() or this() call.
 * For older JDKs, super()/this() must be the FIRST statement.
 *
 * Strategy: Move any statements before super()/this() to after the call,
 * wrapping them in initialization logic where possible. Adds TODO comments
 * for cases that need manual review.
 *
 * Example:
 *   Input:
 *     public MyClass(int value) {
 *         validate(value);
 *         super(value);
 *         this.value = value;
 *     }
 *
 *   Output:
 *     public MyClass(int value) {
 *         super(value);
 *         // TODO: moved from before super() - verify correctness
 *         validate(value);
 *         this.value = value;
 *     }
 */
public class FlexibleConstructorTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "FLEXIBLE_CONSTRUCTOR_BODY";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        List<ConstructorDeclaration> constructors = cu.findAll(ConstructorDeclaration.class);

        for (ConstructorDeclaration constructor : constructors) {
            transformConstructor(constructor);
        }

        return cu;
    }

    private void transformConstructor(ConstructorDeclaration constructor) {
        BlockStmt body = constructor.getBody();
        List<Statement> statements = body.getStatements();

        if (statements.isEmpty()) return;

        // Find the super() or this() invocation
        int superOrThisIndex = -1;
        for (int i = 0; i < statements.size(); i++) {
            if (statements.get(i) instanceof ExplicitConstructorInvocationStmt) {
                superOrThisIndex = i;
                break;
            }
        }

        // No super/this call, or it's already first — nothing to do
        if (superOrThisIndex <= 0) return;

        // Collect statements before super()/this()
        List<Statement> beforeSuper = new ArrayList<>();
        for (int i = 0; i < superOrThisIndex; i++) {
            beforeSuper.add(statements.get(i).clone());
        }

        // Get the super/this statement
        ExplicitConstructorInvocationStmt superCall =
                (ExplicitConstructorInvocationStmt) statements.get(superOrThisIndex).clone();

        // Collect statements after super()/this()
        List<Statement> afterSuper = new ArrayList<>();
        for (int i = superOrThisIndex + 1; i < statements.size(); i++) {
            afterSuper.add(statements.get(i).clone());
        }

        // Rebuild the body: super() first, then moved statements, then rest
        body.getStatements().clear();
        body.addStatement(superCall);

        // Add a TODO comment for the first moved statement
        if (!beforeSuper.isEmpty()) {
            beforeSuper.get(0).setLineComment(
                    " TODO: moved from before super()/this() - verify correctness");
        }

        for (Statement stmt : beforeSuper) {
            body.addStatement(stmt);
        }

        for (Statement stmt : afterSuper) {
            body.addStatement(stmt);
        }
    }
}
