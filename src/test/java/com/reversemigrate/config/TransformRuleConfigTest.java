package com.reversemigrate.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for TransformRuleConfig.
 * Covers classpath loading, file loading, filtering by JDK, disabled rules, and
 * save/load round-trip.
 */
class TransformRuleConfigTest {

    private TransformRuleConfig config;

    @BeforeEach
    void setUp() {
        config = new TransformRuleConfig();
    }

    @Test
    @DisplayName("Loading from classpath finds all 12 default rules")
    void testLoadFromClasspath() {
        config.loadFromClasspath();

        assertEquals(12, config.size(), "Should load 12 default rules");
        assertNotNull(config.getAllRules());
    }

    @Test
    @DisplayName("All default rules have required fields")
    void testDefaultRulesHaveRequiredFields() {
        config.loadFromClasspath();

        for (TransformRule rule : config.getAllRules()) {
            assertNotNull(rule.getFeatureId(), "featureId should not be null");
            assertTrue(rule.getIntroducedInJdk() > 0, "introducedInJdk should be positive");
            assertNotNull(rule.getDescription(), "description should not be null");
            assertNotNull(rule.getTransformerClass(), "transformerClass should not be null");
        }
    }

    @Test
    @DisplayName("Filtering for target JDK 8 returns all rules")
    void testFilterForJdk8() {
        config.loadFromClasspath();

        List<TransformRule> applicable = config.getApplicableRules(8);
        assertEquals(12, applicable.size(),
                "All features were introduced after JDK 8, so all should be applicable");
    }

    @Test
    @DisplayName("Filtering for target JDK 11 excludes var (JDK 10)")
    void testFilterForJdk11() {
        config.loadFromClasspath();

        List<TransformRule> applicable = config.getApplicableRules(11);

        // JDK 10 var should be excluded (10 <= 11)
        boolean hasVar = applicable.stream()
                .anyMatch(r -> r.getFeatureId().equals("LOCAL_VAR_TYPE"));
        assertFalse(hasVar, "var (JDK 10) should not be applicable when target is JDK 11");

        // JDK 14+ features should be included
        boolean hasSwitch = applicable.stream()
                .anyMatch(r -> r.getFeatureId().equals("SWITCH_EXPRESSION"));
        assertTrue(hasSwitch, "switch expression (JDK 14) should be applicable for JDK 11 target");
    }

    @Test
    @DisplayName("Filtering for target JDK 17 returns JDK 21+ features")
    void testFilterForJdk17() {
        config.loadFromClasspath();

        List<TransformRule> applicable = config.getApplicableRules(17);
        assertEquals(6, applicable.size(),
                "Features introduced in JDK 21+ need transformation when targeting JDK 17");
    }

    @Test
    @DisplayName("Disabled rules are excluded from applicable rules")
    void testDisabledRulesExcluded() {
        String json = """
                {
                    "rules": [
                        {
                            "featureId": "TEXT_BLOCK",
                            "introducedInJdk": 15,
                            "description": "test",
                            "transformerClass": "com.reversemigrate.transform.transformers.TextBlockTransformer",
                            "enabled": false
                        }
                    ]
                }
                """;

        config.loadFromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(1, config.size(), "Should have 1 rule loaded");
        assertEquals(0, config.getApplicableRules(8).size(),
                "Disabled rule should not be in applicable rules");
    }

    @Test
    @DisplayName("Loading from external file works")
    void testLoadFromFile(@TempDir Path tempDir) throws IOException {
        String json = """
                {
                    "rules": [
                        {
                            "featureId": "CUSTOM_FEATURE",
                            "introducedInJdk": 21,
                            "description": "Custom feature",
                            "transformerClass": "com.example.CustomTransformer",
                            "enabled": true
                        }
                    ]
                }
                """;

        Path configFile = tempDir.resolve("custom-rules.json");
        Files.writeString(configFile, json);

        config.loadFromFile(configFile);

        assertEquals(1, config.size(), "Should have 1 custom rule");
        assertEquals("CUSTOM_FEATURE", config.getAllRules().get(0).getFeatureId());
    }

    @Test
    @DisplayName("addRule adds a new rule dynamically")
    void testAddRule() {
        config.loadFromClasspath();
        int originalSize = config.size();

        TransformRule newRule = new TransformRule(
                "STRING_TEMPLATE", 21, "String templates",
                "com.example.StringTemplateTransformer", true);
        config.addRule(newRule);

        assertEquals(originalSize + 1, config.size(), "Size should increase by 1");
    }

    @Test
    @DisplayName("Save and reload produces identical rules")
    void testSaveAndReload(@TempDir Path tempDir) throws IOException {
        config.loadFromClasspath();
        Path savePath = tempDir.resolve("saved-rules.json");
        config.saveToFile(savePath);

        TransformRuleConfig reloaded = new TransformRuleConfig();
        reloaded.loadFromFile(savePath);

        assertEquals(config.size(), reloaded.size(), "Reloaded config should have same size");

        for (int i = 0; i < config.size(); i++) {
            assertEquals(config.getAllRules().get(i).getFeatureId(),
                    reloaded.getAllRules().get(i).getFeatureId(),
                    "Feature IDs should match after round-trip");
        }
    }

    @Test
    @DisplayName("Loading empty rules array results in empty config")
    void testEmptyRules() {
        String json = """
                { "rules": [] }
                """;
        config.loadFromStream(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8)));

        assertEquals(0, config.size(), "Should have 0 rules");
        assertEquals(0, config.getApplicableRules(8).size(), "Should have 0 applicable rules");
    }

    @Test
    @DisplayName("getAllRules returns unmodifiable list")
    void testGetAllRulesUnmodifiable() {
        config.loadFromClasspath();

        assertThrows(UnsupportedOperationException.class, () -> {
            config.getAllRules().add(new TransformRule());
        }, "Should not be able to modify the rules list externally");
    }
}
