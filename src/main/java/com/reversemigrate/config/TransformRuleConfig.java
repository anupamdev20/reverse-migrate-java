package com.reversemigrate.config;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Loads and manages transformation rules from a JSON configuration file.
 * This is the central configuration component that determines which
 * transformers
 * are applicable for a given source→target JDK migration.
 */
public class TransformRuleConfig {

    private final List<TransformRule> rules;
    private final Gson gson;

    public TransformRuleConfig() {
        this.rules = new ArrayList<>();
        this.gson = new Gson();
    }

    /**
     * Loads rules from the default classpath resource (jdk-transform-rules.json).
     */
    public void loadFromClasspath() {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("jdk-transform-rules.json")) {
            if (is == null) {
                throw new IllegalStateException(
                        "jdk-transform-rules.json not found on classpath");
            }
            loadFromStream(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load rules from classpath", e);
        }
    }

    /**
     * Loads rules from an external file path.
     * This allows users to provide custom or extended rule sets.
     */
    public void loadFromFile(Path filePath) {
        try (InputStream is = Files.newInputStream(filePath)) {
            loadFromStream(is);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to load rules from " + filePath, e);
        }
    }

    /**
     * Loads rules from an input stream containing JSON.
     */
    public void loadFromStream(InputStream inputStream) {
        try (Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8)) {
            JsonObject root = gson.fromJson(reader, JsonObject.class);
            Type listType = new TypeToken<List<TransformRule>>() {
            }.getType();
            List<TransformRule> loaded = gson.fromJson(root.get("rules"), listType);
            rules.clear();
            if (loaded != null) {
                rules.addAll(loaded);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to parse rules JSON", e);
        }
    }

    /**
     * Returns all rules whose feature was introduced AFTER the target JDK version
     * and that are enabled. These are the features that need to be downgraded.
     *
     * @param targetJdk the JDK version to target (e.g. 8, 11)
     * @return list of applicable transformation rules
     */
    public List<TransformRule> getApplicableRules(int targetJdk) {
        return rules.stream()
                .filter(TransformRule::isEnabled)
                .filter(rule -> rule.getIntroducedInJdk() > targetJdk)
                .collect(Collectors.toList());
    }

    /**
     * Returns all loaded rules (regardless of enabled status or JDK version).
     */
    public List<TransformRule> getAllRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * Adds a new rule to the configuration.
     */
    public void addRule(TransformRule rule) {
        rules.add(rule);
    }

    /**
     * Saves the current rules to a JSON file.
     */
    public void saveToFile(Path filePath) throws IOException {
        JsonObject root = new JsonObject();
        root.add("rules", gson.toJsonTree(rules));
        String json = new com.google.gson.GsonBuilder()
                .setPrettyPrinting()
                .create()
                .toJson(root);
        Files.writeString(filePath, json, StandardCharsets.UTF_8);
    }

    /**
     * Returns the number of loaded rules.
     */
    public int size() {
        return rules.size();
    }
}
