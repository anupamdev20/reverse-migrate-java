package com.reversemigrate;

import com.reversemigrate.engine.JavaSourceMigrator;
import com.reversemigrate.engine.MigrationResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * CLI entry point for the Java Reverse Migrator.
 *
 * Usage:
 * java -jar reverse-migrate.jar --source-jdk 17 --target-jdk 11 --input
 * <file|dir> [--output
 * <dir>
 * ] [--config <rules.json>]
 */
public class ReverseMigrateApp {

    public static void main(String[] args) {
        if (args.length == 0 || containsFlag(args, "--help") || containsFlag(args, "-h")) {
            printUsage();
            return;
        }

        int sourceJdk = getIntOption(args, "--source-jdk", 17);
        int targetJdk = getIntOption(args, "--target-jdk", 11);
        String input = getOption(args, "--input", null);
        String output = getOption(args, "--output", null);
        String configPath = getOption(args, "--config", null);
        boolean debug = containsFlag(args, "--debug");

        if (input == null) {
            System.err.println("Error: --input is required");
            printUsage();
            System.exit(1);
        }

        try {
            JavaSourceMigrator migrator;
            if (configPath != null) {
                migrator = JavaSourceMigrator.createWithConfig(Paths.get(configPath));
            } else {
                migrator = JavaSourceMigrator.createDefault();
            }
            migrator.setDebug(debug);

            Path inputPath = Paths.get(input);

            if (!Files.exists(inputPath)) {
                System.err.println("Error: Input path does not exist: " + input);
                System.exit(1);
            }

            System.out.println("╔══════════════════════════════════════════╗");
            System.out.println("║      Java Reverse Migrator v1.0         ║");
            System.out.println("╚══════════════════════════════════════════╝");
            System.out.println();
            System.out.println("  Source JDK: " + sourceJdk);
            System.out.println("  Target JDK: " + targetJdk);
            System.out.println("  Input:      " + inputPath.toAbsolutePath());
            System.out.println();

            if (Files.isDirectory(inputPath)) {
                Path outputDir = output != null ? Paths.get(output) : inputPath.resolve("../migrated");
                migrateDirectory(migrator, inputPath, outputDir, sourceJdk, targetJdk);
            } else {
                Path outputFile = output != null ? Paths.get(output) : getDefaultOutputPath(inputPath);
                migrateSingleFile(migrator, inputPath, outputFile, sourceJdk, targetJdk);
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void migrateSingleFile(JavaSourceMigrator migrator, Path inputFile,
            Path outputFile, int sourceJdk, int targetJdk)
            throws IOException {
        System.out.println("Migrating: " + inputFile.getFileName());
        MigrationResult result = migrator.migrateFile(inputFile, outputFile, sourceJdk, targetJdk);
        printResult(inputFile.getFileName().toString(), result);
        System.out.println("\nOutput written to: " + outputFile.toAbsolutePath());
    }

    private static void migrateDirectory(JavaSourceMigrator migrator, Path inputDir,
            Path outputDir, int sourceJdk, int targetJdk)
            throws IOException {
        System.out.println("Migrating directory: " + inputDir.toAbsolutePath());
        System.out.println("Output directory:    " + outputDir.toAbsolutePath());
        System.out.println();

        List<MigrationResult> results = migrator.migrateDirectory(inputDir, outputDir,
                sourceJdk, targetJdk);

        int totalFiles = results.size();
        int successCount = (int) results.stream().filter(MigrationResult::isSuccess).count();
        int changedCount = (int) results.stream().filter(MigrationResult::hasChanges).count();

        System.out.println("═══════════════════════════════════════════");
        System.out.println("  Summary");
        System.out.println("═══════════════════════════════════════════");
        System.out.printf("  Total files:    %d%n", totalFiles);
        System.out.printf("  Successful:     %d%n", successCount);
        System.out.printf("  With changes:   %d%n", changedCount);
        System.out.printf("  Failed:         %d%n", totalFiles - successCount);
        System.out.println();

        for (MigrationResult result : results) {
            if (result.hasChanges() || !result.isSuccess()) {
                printResult("  file", result);
            }
        }
    }

    private static void printResult(String filename, MigrationResult result) {
        if (result.isSuccess()) {
            if (result.hasChanges()) {
                System.out.println("  ✓ " + filename + " — transformed features: " +
                        result.getAppliedFeatures());
            } else {
                System.out.println("  · " + filename + " — no changes needed");
            }
        } else {
            System.out.println("  ✗ " + filename + " — error: " + result.getErrorMessage());
        }

        for (String warning : result.getWarnings()) {
            System.out.println("    ⚠ " + warning);
        }
    }

    private static Path getDefaultOutputPath(Path inputFile) {
        String name = inputFile.getFileName().toString();
        String baseName = name.substring(0, name.lastIndexOf('.'));
        return inputFile.getParent().resolve(baseName + "_migrated.java");
    }

    private static void printUsage() {
        System.out.println("Java Reverse Migrator");
        System.out.println();
        System.out.println("Usage:");
        System.out.println("  java -jar reverse-migrate.jar [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("  --source-jdk <version>  Source JDK version (default: 17)");
        System.out.println("  --target-jdk <version>  Target JDK version (default: 11)");
        System.out.println("  --input <file|dir>      Input Java file or directory (required)");
        System.out.println("  --output <file|dir>     Output file or directory");
        System.out.println("  --config <path>         Custom transform rules JSON file");
        System.out.println("  --debug                 Enable detailed debug output showing AST changes");
        System.out.println("  --help, -h              Show this help message");
        System.out.println();
        System.out.println("Examples:");
        System.out.println("  java -jar reverse-migrate.jar --source-jdk 17 --target-jdk 11 --input src/");
        System.out.println("  java -jar reverse-migrate.jar --source-jdk 21 --target-jdk 8 --input App.java");
    }

    private static String getOption(String[] args, String name, String defaultValue) {
        for (int i = 0; i < args.length - 1; i++) {
            if (args[i].equals(name)) {
                return args[i + 1];
            }
        }
        return defaultValue;
    }

    private static int getIntOption(String[] args, String name, int defaultValue) {
        String value = getOption(args, name, null);
        if (value != null) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                System.err.println("Warning: invalid value for " + name + ": " + value +
                        ", using default: " + defaultValue);
            }
        }
        return defaultValue;
    }

    private static boolean containsFlag(String[] args, String flag) {
        for (String arg : args) {
            if (arg.equals(flag))
                return true;
        }
        return false;
    }
}
