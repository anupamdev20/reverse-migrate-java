package com.reversemigrate.transform.transformers;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.TextBlockLiteralExpr;
import com.reversemigrate.transform.FeatureTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Transforms JDK 15+ text blocks (triple-quoted strings) into
 * string concatenation expressions compatible with older JDK versions.
 *
 * Example:
 * Input: String s = """
 * Hello
 * World
 * """;
 * Output: String s = "Hello\n" + "World\n";
 */
public class TextBlockTransformer implements FeatureTransformer {

    @Override
    public String featureId() {
        return "TEXT_BLOCK";
    }

    @Override
    public CompilationUnit transform(CompilationUnit cu) {
        List<TextBlockLiteralExpr> textBlocks = cu.findAll(TextBlockLiteralExpr.class);

        for (TextBlockLiteralExpr textBlock : textBlocks) {
            // Use JavaParser's built-in asString() which applies the JLS stripping
            // algorithm
            String processed = textBlock.asString();

            if (processed.isEmpty()) {
                textBlock.replace(new StringLiteralExpr(""));
                continue;
            }

            // Split into lines for concatenation
            List<String> segments = buildStringSegments(processed);

            if (segments.size() == 1) {
                textBlock.replace(new StringLiteralExpr(escapeForStringLiteral(segments.get(0))));
            } else {
                // Build "line1" + "line2" + ...
                StringLiteralExpr first = new StringLiteralExpr(escapeForStringLiteral(segments.get(0)));
                BinaryExpr chain = new BinaryExpr(
                        first,
                        new StringLiteralExpr(escapeForStringLiteral(segments.get(1))),
                        BinaryExpr.Operator.PLUS);
                for (int i = 2; i < segments.size(); i++) {
                    chain = new BinaryExpr(
                            chain,
                            new StringLiteralExpr(escapeForStringLiteral(segments.get(i))),
                            BinaryExpr.Operator.PLUS);
                }
                textBlock.replace(chain);
            }
        }

        return cu;
    }

    /**
     * Splits the processed text block string into segments for concatenation.
     * Each segment (except possibly the last) includes a trailing \n.
     */
    List<String> buildStringSegments(String content) {
        if (content.isEmpty()) {
            return List.of();
        }

        List<String> result = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (i < lines.length - 1) {
                result.add(lines[i] + "\n");
            } else if (!lines[i].isEmpty()) {
                result.add(lines[i]);
            }
        }
        return result;
    }

    /**
     * Escapes characters that need escaping inside a Java string literal.
     * Note: StringLiteralExpr stores the "internal" value (what appears between
     * quotes),
     * so we need to escape things like newlines to their escape sequences.
     */
    private String escapeForStringLiteral(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}
