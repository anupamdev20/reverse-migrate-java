package com.reversemigrate.engine;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.reversemigrate.config.TransformRuleConfig;
import com.reversemigrate.transform.FeatureTransformer;
import com.reversemigrate.transform.TransformerRegistry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * Main orchestrator for Java source code migration.
 * Parses source files, applies applicable transformers based on source/target
 * JDK,
 * and produces migrated source code.
 */
public class JavaSourceMigrator {

    private final TransformRuleConfig config;
    private final TransformerRegistry registry;
    private final JavaParser parser;
    private boolean debug = false;

    public JavaSourceMigrator(TransformRuleConfig config) {
        this.config = config;
        this.registry = new TransformerRegistry(config);

        // Configure parser for the latest supported Java version
        ParserConfiguration parserConfig = new ParserConfiguration();
        parserConfig.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21);
        this.parser = new JavaParser(parserConfig);
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    /**
     * Creates a migrator with rules loaded from the default classpath config.
     */
    public static JavaSourceMigrator createDefault() {
        TransformRuleConfig config = new TransformRuleConfig();
        config.loadFromClasspath();
        return new JavaSourceMigrator(config);
    }

    /**
     * Creates a migrator with rules loaded from a custom config file.
     */
    public static JavaSourceMigrator createWithConfig(Path configPath) {
        TransformRuleConfig config = new TransformRuleConfig();
        config.loadFromFile(configPath);
        return new JavaSourceMigrator(config);
    }

    /**
     * Migrates a Java source code string from sourceJdk to targetJdk.
     *
     * @param source    the Java source code
     * @param sourceJdk the JDK version the source is written for
     * @param targetJdk the target JDK version to downgrade to
     * @return the migration result
     */
    public MigrationResult migrate(String source, int sourceJdk, int targetJdk) {
        MigrationResult.Builder result = new MigrationResult.Builder()
                .originalSource(source)
                .sourceJdk(sourceJdk)
                .targetJdk(targetJdk);

        if (sourceJdk <= targetJdk) {
            result.transformedSource(source);
            result.addWarning("Source JDK (" + sourceJdk + ") is not higher than target JDK (" +
                    targetJdk + "). No transformation needed.");
            return result.build();
        }

        try {
            ParseResult<CompilationUnit> parseResult = parser.parse(source);
            if (!parseResult.isSuccessful() || parseResult.getResult().isEmpty()) {
                result.success(false);
                result.errorMessage("Failed to parse source code: " +
                        parseResult.getProblems());
                result.transformedSource(source);
                return result.build();
            }

            CompilationUnit cu = parseResult.getResult().get();
            List<FeatureTransformer> transformers = registry.getTransformers(targetJdk);

            for (FeatureTransformer transformer : transformers) {
                try {
                    CompilationUnit before = cu.clone();
                    cu = transformer.transform(cu);

                    // Check if this transformer actually changed anything
                    if (!cu.toString().equals(before.toString())) {
                        result.addAppliedFeature(transformer.featureId());
                        if (debug) {
                            System.out.println("\n[DEBUG] \u2500\u2500\u2500 Transformer Applied: " + transformer.featureId() + " \u2500\u2500\u2500");
                            System.out.println("[DEBUG] --- Before ---");
                            System.out.println(before.toString());
                            System.out.println("[DEBUG] --- After ---");
                            System.out.println(cu.toString());
                            System.out.println("[DEBUG] \u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\n");
                        }
                    }
                } catch (Exception e) {
                    result.addWarning("Transformer " + transformer.featureId() +
                            " failed: " + e.getMessage());
                }
            }

            result.transformedSource(cu.toString());
        } catch (Exception e) {
            result.success(false);
            result.errorMessage("Migration failed: " + e.getMessage());
            result.transformedSource(source);
        }

        return result.build();
    }

    /**
     * Migrates a single Java file.
     *
     * @param inputFile  the source file
     * @param outputFile the output file (can be the same as input for in-place)
     * @param sourceJdk  source JDK version
     * @param targetJdk  target JDK version
     * @return the migration result
     */
    public MigrationResult migrateFile(Path inputFile, Path outputFile,
            int sourceJdk, int targetJdk) throws IOException {
        String source = Files.readString(inputFile, StandardCharsets.UTF_8);
        MigrationResult result = migrate(source, sourceJdk, targetJdk);

        if (result.isSuccess()) {
            Files.createDirectories(outputFile.getParent());
            Files.writeString(outputFile, result.getTransformedSource(), StandardCharsets.UTF_8);
        }

        return result;
    }

    /**
     * Migrates all Java files in a directory.
     *
     * @param inputDir  source directory
     * @param outputDir output directory (preserves package structure)
     * @param sourceJdk source JDK version
     * @param targetJdk target JDK version
     * @return list of migration results
     */
    public List<MigrationResult> migrateDirectory(Path inputDir, Path outputDir,
            int sourceJdk, int targetJdk)
            throws IOException {
        List<MigrationResult> results = new ArrayList<>();

        Files.walkFileTree(inputDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                    throws IOException {
                if (file.toString().endsWith(".java")) {
                    Path relative = inputDir.relativize(file);
                    Path outFile = outputDir.resolve(relative);
                    MigrationResult result = migrateFile(file, outFile, sourceJdk, targetJdk);
                    results.add(result);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return results;
    }

    /**
     * Returns the loaded configuration.
     */
    public TransformRuleConfig getConfig() {
        return config;
    }

    /**
     * Returns the transformer registry.
     */
    public TransformerRegistry getRegistry() {
        return registry;
    }
}
