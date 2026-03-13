package com.reversemigrate.engine;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JavaSourceMigrator.
 * Tests end-to-end migration with multiple features, file/directory processing,
 * and edge cases.
 */
class JavaSourceMigratorTest {

    private JavaSourceMigrator migrator;

    @BeforeEach
    void setUp() {
        migrator = JavaSourceMigrator.createDefault();
    }

    @Test
    @DisplayName("JDK 17 source with text blocks is migrated to JDK 8")
    void testTextBlockMigrationToJdk8() {
        String source = "class Test {\n"
                + "    String html = \"\"\"\n"
                + "            <html>\n"
                + "                <body>Hello</body>\n"
                + "            </html>\n"
                + "            \"\"\";\n"
                + "}\n";

        MigrationResult result = migrator.migrate(source, 17, 8);

        assertTrue(result.isSuccess(), "Migration should succeed");
        assertTrue(result.hasChanges(), "Should have transformations applied");
        assertTrue(result.getAppliedFeatures().contains("TEXT_BLOCK"),
                "TEXT_BLOCK should be in applied features");
        assertFalse(result.getTransformedSource().contains("\"\"\""),
                "Text blocks should be removed from output");
    }

    @Test
    @DisplayName("JDK 17 source with var is migrated to JDK 8")
    void testVarMigrationToJdk8() {
        String source = """
                class Test {
                    void test() {
                        var x = 42;
                        var name = "hello";
                    }
                }
                """;

        MigrationResult result = migrator.migrate(source, 17, 8);

        assertTrue(result.isSuccess());
        assertTrue(result.getAppliedFeatures().contains("LOCAL_VAR_TYPE"));

        String output = result.getTransformedSource();
        assertTrue(output.contains("int x"), "var should be replaced with int");
        assertTrue(output.contains("String name"), "var should be replaced with String");
    }

    @Test
    @DisplayName("JDK 17 source migrated to JDK 11 does NOT transform var")
    void testVarNotTransformedForJdk11() {
        String source = """
                class Test {
                    void test() {
                        var x = 42;
                    }
                }
                """;

        MigrationResult result = migrator.migrate(source, 17, 11);

        // var was introduced in JDK 10, so it should NOT be transformed when targeting
        // 11
        assertFalse(result.getAppliedFeatures().contains("LOCAL_VAR_TYPE"),
                "var should not be transformed for JDK 11 target");
    }

    @Test
    @DisplayName("Source JDK <= target JDK produces no-op with warning")
    void testNoTransformationNeeded() {
        String source = """
                class Test {
                    int x = 42;
                }
                """;

        MigrationResult result = migrator.migrate(source, 8, 11);

        assertTrue(result.isSuccess());
        assertFalse(result.hasChanges());
        assertFalse(result.getWarnings().isEmpty(),
                "Should have a warning about no transformation needed");
    }

    @Test
    @DisplayName("Invalid Java source returns error result")
    void testInvalidSource() {
        String source = "this is not valid java source code!!!";

        MigrationResult result = migrator.migrate(source, 17, 8);

        assertFalse(result.isSuccess(), "Should report failure for invalid source");
        assertNotNull(result.getErrorMessage(), "Should have error message");
    }

    @Test
    @DisplayName("Multiple features are transformed in a single pass")
    void testMultipleFeatures() {
        String source = "class Test {\n"
                + "    String greeting = \"\"\"\n"
                + "            Hello\n"
                + "            World\n"
                + "            \"\"\";\n"
                + "    void test() {\n"
                + "        var x = 42;\n"
                + "        var s = \"test\";\n"
                + "    }\n"
                + "}\n";

        MigrationResult result = migrator.migrate(source, 17, 8);

        assertTrue(result.isSuccess());
        assertTrue(result.getAppliedFeatures().size() >= 2,
                "Should have at least 2 features applied");
    }

    @Test
    @DisplayName("MigrationResult preserves original source")
    void testOriginalSourcePreserved() {
        String source = """
                class Test {
                    void test() {
                        var x = 42;
                    }
                }
                """;

        MigrationResult result = migrator.migrate(source, 17, 8);

        assertEquals(source, result.getOriginalSource(),
                "Original source should be preserved in result");
        assertNotEquals(source, result.getTransformedSource(),
                "Transformed source should differ from original");
    }

    @Test
    @DisplayName("File migration writes output to disk")
    void testFileMigration(@TempDir Path tempDir) throws IOException {
        String source = """
                class Test {
                    void test() {
                        var x = 42;
                    }
                }
                """;

        Path inputFile = tempDir.resolve("Test.java");
        Files.writeString(inputFile, source, StandardCharsets.UTF_8);

        Path outputFile = tempDir.resolve("output/Test.java");

        MigrationResult result = migrator.migrateFile(inputFile, outputFile, 17, 8);

        assertTrue(result.isSuccess());
        assertTrue(Files.exists(outputFile), "Output file should be created");

        String output = Files.readString(outputFile, StandardCharsets.UTF_8);
        assertTrue(output.contains("int x"), "Output file should contain transformed code");
    }

    @Test
    @DisplayName("Directory migration processes all Java files")
    void testDirectoryMigration(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("src");
        Path outputDir = tempDir.resolve("out");
        Files.createDirectories(inputDir.resolve("com/example"));

        // Create two Java files
        Files.writeString(inputDir.resolve("com/example/Foo.java"),
                "class Foo { void test() { var x = 1; } }");
        Files.writeString(inputDir.resolve("com/example/Bar.java"),
                "class Bar { String s = \"normal\"; }");

        List<MigrationResult> results = migrator.migrateDirectory(inputDir, outputDir, 17, 8);

        assertEquals(2, results.size(), "Should process 2 files");
        assertTrue(results.stream().allMatch(MigrationResult::isSuccess),
                "All files should succeed");

        assertTrue(Files.exists(outputDir.resolve("com/example/Foo.java")),
                "Output should preserve directory structure");
    }

    @Test
    @DisplayName("MigrationResult toString is informative")
    void testResultToString() {
        MigrationResult result = new MigrationResult.Builder()
                .originalSource("class A {}")
                .transformedSource("class A {}")
                .sourceJdk(17)
                .targetJdk(8)
                .addAppliedFeature("TEXT_BLOCK")
                .addWarning("test warning")
                .build();

        String str = result.toString();
        assertTrue(str.contains("TEXT_BLOCK"));
        assertTrue(str.contains("17"));
        assertTrue(str.contains("8"));
        assertTrue(str.contains("test warning"));
    }

    @Test
    @DisplayName("Result JDK versions are recorded correctly")
    void testResultJdkVersions() {
        String source = "class Test {}";
        MigrationResult result = migrator.migrate(source, 17, 11);

        assertEquals(17, result.getSourceJdk());
        assertEquals(11, result.getTargetJdk());
    }

    @Test
    @DisplayName("Non-Java files are skipped during directory migration")
    void testNonJavaFilesSkipped(@TempDir Path tempDir) throws IOException {
        Path inputDir = tempDir.resolve("src");
        Files.createDirectories(inputDir);

        Files.writeString(inputDir.resolve("Test.java"), "class Test {}");
        Files.writeString(inputDir.resolve("readme.txt"), "Not a Java file");
        Files.writeString(inputDir.resolve("config.xml"), "<config/>");

        Path outputDir = tempDir.resolve("out");
        List<MigrationResult> results = migrator.migrateDirectory(inputDir, outputDir, 17, 8);

        assertEquals(1, results.size(), "Should only process .java files");
    }

    @Test
    @DisplayName("Pattern matching instanceof is transformed for JDK 8 target")
    void testPatternMatchingMigration() {
        String source = """
                class Test {
                    void test(Object obj) {
                        if (obj instanceof String s) {
                            System.out.println(s.length());
                        }
                    }
                }
                """;

        MigrationResult result = migrator.migrate(source, 17, 8);

        assertTrue(result.isSuccess());
        assertTrue(result.getAppliedFeatures().contains("PATTERN_MATCHING_INSTANCEOF"));
        assertTrue(result.getTransformedSource().contains("(String) obj"));
    }

    @Test
    @DisplayName("Custom config path creates migrator with custom rules")
    void testCustomConfigPath(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                    "rules": [
                        {
                            "featureId": "TEXT_BLOCK",
                            "introducedInJdk": 15,
                            "description": "Text blocks",
                            "transformerClass": "com.reversemigrate.transform.transformers.TextBlockTransformer",
                            "enabled": true
                        }
                    ]
                }
                """;

        Path configFile = tempDir.resolve("custom.json");
        Files.writeString(configFile, json);

        JavaSourceMigrator customMigrator = JavaSourceMigrator.createWithConfig(configFile);
        assertEquals(1, customMigrator.getConfig().size(),
                "Custom config should have 1 rule");
    }
}
