package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.List;

/**
 * Transforms JDK 22+ unnamed variables (underscore _) into
 * named variables compatible with older JDK versions.
 *
 * Example:
 *   Input:  var _ = someMethod();
 *   Output: var _unused1 = someMethod();
 *
 *   Input:  } catch (Exception _) {
 *   Output: } catch (Exception _unused2) {
 *
 *   Input:  for (var _ : list) { count++; }
 *   Output: for (var _unused3 : list) { count++; }
 */
public class UnnamedVariableTransformer implements FeatureTransformer {

    private int counter = 0;

    @Override
    public String featureId() {
        return "UNNAMED_VARIABLE";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        counter = 0;

        // Find all variable declarators named "_"
        List<VariableDeclarator> varDecls = cu.findAll(VariableDeclarator.class);
        for (VariableDeclarator varDecl : varDecls) {
            if ("_".equals(varDecl.getNameAsString())) {
                String newName = "_unused" + (++counter);
                varDecl.setName(newName);
            }
        }

        // Also handle catch clause parameters named "_"
        cu.findAll(com.github.javaparser.ast.body.Parameter.class).forEach(param -> {
            if ("_".equals(param.getNameAsString())) {
                String newName = "_unused" + (++counter);
                param.setName(newName);
            }
        });

        // Handle lambda parameters named "_"
        cu.findAll(com.github.javaparser.ast.expr.LambdaExpr.class).forEach(lambda -> {
            lambda.getParameters().forEach(param -> {
                if ("_".equals(param.getNameAsString())) {
                    String newName = "_unused" + (++counter);
                    param.setName(newName);
                }
            });
        });

        return cu;
    }
}
