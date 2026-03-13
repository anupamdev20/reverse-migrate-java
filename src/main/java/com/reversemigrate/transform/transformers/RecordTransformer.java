package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms JDK 16+ records into equivalent plain Java classes.
 *
 * A record like:
 * public record Point(int x, int y) { }
 *
 * Becomes:
 * public final class Point {
 * private final int x;
 * private final int y;
 *
 * public Point(int x, int y) {
 * this.x = x;
 * this.y = y;
 * }
 *
 * public int x() { return x; }
 * public int y() { return y; }
 *
 * @Override public boolean equals(Object o) { ... }
 * @Override public int hashCode() { ... }
 * @Override public String toString() { ... }
 *           }
 */
public class RecordTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "RECORD";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        List<RecordDeclaration> records = cu.findAll(RecordDeclaration.class);

        for (RecordDeclaration record : records) {
            ClassOrInterfaceDeclaration classDecl = convertRecordToClass(record);
            record.replace(classDecl);
        }

        return cu;
    }

    private ClassOrInterfaceDeclaration convertRecordToClass(RecordDeclaration record) {
        ClassOrInterfaceDeclaration classDecl = new ClassOrInterfaceDeclaration();
        classDecl.setName(record.getNameAsString());

        // Copy modifiers and add FINAL
        NodeList<Modifier> modifiers = new NodeList<>(record.getModifiers());
        if (modifiers.stream().noneMatch(m -> m.getKeyword() == Modifier.Keyword.FINAL)) {
            modifiers.add(Modifier.finalModifier());
        }
        classDecl.setModifiers(modifiers);

        // Copy implemented interfaces
        record.getImplementedTypes().forEach(classDecl::addImplementedType);

        // Copy type parameters
        record.getTypeParameters().forEach(classDecl::addTypeParameter);

        // Copy annotations
        record.getAnnotations().forEach(classDecl::addAnnotation);

        // Get record parameters (components)
        List<Parameter> parameters = record.getParameters();
        List<String> paramNames = new ArrayList<>();
        List<Type> paramTypes = new ArrayList<>();

        for (Parameter param : parameters) {
            paramNames.add(param.getNameAsString());
            paramTypes.add(param.getType());
        }

        // Add private final fields
        for (int i = 0; i < paramNames.size(); i++) {
            FieldDeclaration field = classDecl.addField(
                    paramTypes.get(i), paramNames.get(i),
                    Modifier.Keyword.PRIVATE, Modifier.Keyword.FINAL);
        }

        // Check if the record has a compact constructor, and build the all-args
        // constructor
        boolean hasCompactConstructor = false;
        BlockStmt compactBody = null;
        for (BodyDeclaration<?> member : record.getMembers()) {
            if (member instanceof CompactConstructorDeclaration compact) {
                hasCompactConstructor = true;
                compactBody = compact.getBody();
                break;
            }
        }

        // Add all-args constructor
        ConstructorDeclaration constructor = classDecl.addConstructor(Modifier.Keyword.PUBLIC);
        for (int i = 0; i < paramNames.size(); i++) {
            constructor.addParameter(paramTypes.get(i), paramNames.get(i));
        }
        BlockStmt constructorBody = new BlockStmt();

        // If there was a compact constructor, prepend its body
        if (hasCompactConstructor && compactBody != null) {
            compactBody.getStatements().forEach(constructorBody::addStatement);
        }

        // Add field assignments: this.x = x;
        for (String name : paramNames) {
            constructorBody.addStatement(new AssignExpr(
                    new FieldAccessExpr(new ThisExpr(), name),
                    new NameExpr(name),
                    AssignExpr.Operator.ASSIGN));
        }
        constructor.setBody(constructorBody);

        // Add accessor methods: public Type name() { return name; }
        for (int i = 0; i < paramNames.size(); i++) {
            MethodDeclaration getter = classDecl.addMethod(paramNames.get(i), Modifier.Keyword.PUBLIC);
            getter.setType(paramTypes.get(i));
            getter.setBody(new BlockStmt().addStatement(
                    new ReturnStmt(new NameExpr(paramNames.get(i)))));
        }

        // Add equals method
        addEqualsMethod(classDecl, paramNames, paramTypes);

        // Add hashCode method
        addHashCodeMethod(classDecl, paramNames);

        // Add toString method
        addToStringMethod(classDecl, record.getNameAsString(), paramNames);

        // Copy any additional methods/fields from the record body
        // (skip compact constructors, they've been handled)
        for (BodyDeclaration<?> member : record.getMembers()) {
            if (member instanceof CompactConstructorDeclaration) {
                continue; // already handled
            }
            classDecl.addMember(member.clone());
        }

        return classDecl;
    }

    private void addEqualsMethod(ClassOrInterfaceDeclaration classDecl,
            List<String> paramNames, List<Type> paramTypes) {
        MethodDeclaration equals = classDecl.addMethod("equals", Modifier.Keyword.PUBLIC);
        equals.addAnnotation("Override");
        equals.setType("boolean");
        equals.addParameter(new ClassOrInterfaceType(null, "Object"), "o");

        StringBuilder body = new StringBuilder();
        body.append("{\n");
        body.append("    if (this == o) return true;\n");
        body.append("    if (o == null || getClass() != o.getClass()) return false;\n");
        body.append("    ").append(classDecl.getNameAsString()).append(" that = (")
                .append(classDecl.getNameAsString()).append(") o;\n");
        body.append("    return ");

        if (paramNames.isEmpty()) {
            body.append("true");
        } else {
            List<String> comparisons = new ArrayList<>();
            for (int i = 0; i < paramNames.size(); i++) {
                String name = paramNames.get(i);
                String typeStr = paramTypes.get(i).asString();
                if (isPrimitiveType(typeStr)) {
                    comparisons.add(name + " == that." + name);
                } else {
                    comparisons.add("java.util.Objects.equals(" + name + ", that." + name + ")");
                }
            }
            body.append(String.join(" && ", comparisons));
        }
        body.append(";\n}");

        equals.setBody(com.github.javaparser.StaticJavaParser.parseBlock(body.toString()));
    }

    private void addHashCodeMethod(ClassOrInterfaceDeclaration classDecl,
            List<String> paramNames) {
        MethodDeclaration hashCode = classDecl.addMethod("hashCode", Modifier.Keyword.PUBLIC);
        hashCode.addAnnotation("Override");
        hashCode.setType("int");

        StringBuilder body = new StringBuilder();
        body.append("{\n");
        if (paramNames.isEmpty()) {
            body.append("    return 0;\n");
        } else {
            body.append("    return java.util.Objects.hash(");
            body.append(String.join(", ", paramNames));
            body.append(");\n");
        }
        body.append("}");

        hashCode.setBody(com.github.javaparser.StaticJavaParser.parseBlock(body.toString()));
    }

    private void addToStringMethod(ClassOrInterfaceDeclaration classDecl,
            String recordName, List<String> paramNames) {
        MethodDeclaration toString = classDecl.addMethod("toString", Modifier.Keyword.PUBLIC);
        toString.addAnnotation("Override");
        toString.setType("String");

        StringBuilder body = new StringBuilder();
        body.append("{\n    return \"").append(recordName).append("[\" + ");
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < paramNames.size(); i++) {
            String name = paramNames.get(i);
            parts.add("\"" + name + "=\" + " + name);
        }
        if (parts.isEmpty()) {
            body.append("\"]\";\n");
        } else {
            body.append(String.join(" + \", \" + ", parts));
            body.append(" + \"]\";\n");
        }
        body.append("}");

        toString.setBody(com.github.javaparser.StaticJavaParser.parseBlock(body.toString()));
    }

    private boolean isPrimitiveType(String type) {
        return switch (type) {
            case "int", "long", "short", "byte", "char", "float", "double", "boolean" -> true;
            default -> false;
        };
    }
}
