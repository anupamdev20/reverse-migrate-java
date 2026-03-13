package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.List;

/**
 * Transforms JDK 17+ sealed class features into regular classes
 * compatible with older JDK versions.
 *
 * Removes:
 * - The 'sealed' modifier
 * - The 'non-sealed' modifier
 * - The 'permits' clause
 *
 * Example:
 * Input: public sealed class Shape permits Circle, Rectangle { }
 * Output: public class Shape { }
 *
 * Input: public non-sealed class Circle extends Shape { }
 * Output: public class Circle extends Shape { }
 */
public class SealedClassTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "SEALED_CLASS";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        List<ClassOrInterfaceDeclaration> classDecls = cu.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration classDecl : classDecls) {
            boolean modified = false;

            // Remove 'sealed' modifier
            NodeList<Modifier> modifiers = classDecl.getModifiers();
            NodeList<Modifier> newModifiers = new NodeList<>();
            for (Modifier mod : modifiers) {
                if (mod.getKeyword() == Modifier.Keyword.SEALED ||
                        mod.getKeyword() == Modifier.Keyword.NON_SEALED) {
                    modified = true;
                } else {
                    newModifiers.add(mod);
                }
            }

            if (modified) {
                classDecl.setModifiers(newModifiers);
            }

            // Remove 'permits' clause — JavaParser represents this as a list
            // of ClassOrInterfaceType in the permits field
            // We need to check if there are permitted types and clear them
            // JavaParser stores permits in the class declaration
            // Clear the permits types if present
            try {
                var permittedTypes = classDecl.getPermittedTypes();
                if (permittedTypes != null && !permittedTypes.isEmpty()) {
                    permittedTypes.clear();
                }
            } catch (Exception e) {
                // Method might not exist in older JavaParser versions — skip
            }
        }

        return cu;
    }
}
