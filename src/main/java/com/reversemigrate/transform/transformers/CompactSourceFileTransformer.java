package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.List;

/**
 * Transforms JDK 25+ compact source files with implicit class declarations
 * and instance main methods into standard class-based files.
 *
 * Example:
 *   Input (compact source file):
 *     void main() {
 *         System.out.println("Hello");
 *     }
 *
 *   Output:
 *     public class Main {
 *         public static void main(String[] args) {
 *             System.out.println("Hello");
 *         }
 *     }
 *
 * Note: This transformer inspects the AST for methods at top-level or
 * classes with a void main() instance method, and converts them to the
 * traditional static main(String[] args) signature.
 */
public class CompactSourceFileTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "COMPACT_SOURCE_FILE";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        // Check for classes with instance main() method (no static, no String[] args)
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);

        for (ClassOrInterfaceDeclaration classDecl : classes) {
            transformInstanceMain(classDecl);
        }

        return cu;
    }

    private void transformInstanceMain(ClassOrInterfaceDeclaration classDecl) {
        // Look for void main() without "static" and without String[] args
        classDecl.getMethodsByName("main").forEach(method -> {
            if (isInstanceMainMethod(method)) {
                convertToStaticMain(method);
            }
        });
    }

    /**
     * Checks if this is an instance main method:
     * - void return type
     * - name is "main"
     * - either no parameters, or has String[] args but is NOT static
     */
    private boolean isInstanceMainMethod(MethodDeclaration method) {
        if (!method.getType().isVoidType()) return false;
        if (method.isStatic()) return false;

        // instance main(): no params
        if (method.getParameters().isEmpty()) return true;

        // instance main(String[] args): has args param but not static
        if (method.getParameters().size() == 1) {
            var param = method.getParameter(0);
            String typeStr = param.getTypeAsString();
            return typeStr.equals("String[]") || typeStr.equals("String...");
        }

        return false;
    }

    private void convertToStaticMain(MethodDeclaration method) {
        // Clear and set modifiers to standard "public static"
        method.getModifiers().clear();
        method.addModifier(Modifier.Keyword.PUBLIC);
        method.addModifier(Modifier.Keyword.STATIC);

        // Replace parameters with a single String[] args parameter
        method.getParameters().clear();
        method.addParameter("String[]", "args");
    }
}
