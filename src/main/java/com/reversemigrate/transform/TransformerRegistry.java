package com.reversemigrate.transform;

import com.reversemigrate.config.TransformRule;
import com.reversemigrate.config.TransformRuleConfig;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Registry that maps feature IDs to transformer instances.
 * Instantiates transformers via reflection from the config's transformerClass
 * field.
 */
public class TransformerRegistry {

    private final Map<String, FeatureTransformer> transformers = new LinkedHashMap<>();
    private final TransformRuleConfig config;

    public TransformerRegistry(TransformRuleConfig config) {
        this.config = config;
        registerAllTransformers();
    }

    /**
     * Instantiates all transformer classes declared in the configuration.
     */
    private void registerAllTransformers() {
        for (TransformRule rule : config.getAllRules()) {
            if (!rule.isEnabled()) {
                continue;
            }
            try {
                Class<?> clazz = Class.forName(rule.getTransformerClass());
                if (!FeatureTransformer.class.isAssignableFrom(clazz)) {
                    throw new IllegalStateException(
                            "Class " + rule.getTransformerClass() +
                                    " does not implement FeatureTransformer");
                }
                FeatureTransformer transformer = (FeatureTransformer) clazz.getDeclaredConstructor().newInstance();
                transformers.put(rule.getFeatureId(), transformer);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException(
                        "Failed to instantiate transformer: " + rule.getTransformerClass(), e);
            }
        }
    }

    /**
     * Returns transformers applicable for the given target JDK,
     * ordered by feature introduction JDK (highest first — process newest features
     * first).
     *
     * @param targetJdk the target JDK version
     * @return ordered list of applicable transformers
     */
    public List<FeatureTransformer> getTransformers(int targetJdk) {
        List<TransformRule> applicable = config.getApplicableRules(targetJdk);

        // Sort by introducedInJdk descending — transform newest features first
        applicable.sort((a, b) -> Integer.compare(b.getIntroducedInJdk(), a.getIntroducedInJdk()));

        return applicable.stream()
                .map(rule -> transformers.get(rule.getFeatureId()))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Returns a specific transformer by feature ID, or empty if not registered.
     */
    public Optional<FeatureTransformer> getTransformer(String featureId) {
        return Optional.ofNullable(transformers.get(featureId));
    }

    /**
     * Returns all registered transformer feature IDs.
     */
    public Set<String> getRegisteredFeatureIds() {
        return Collections.unmodifiableSet(transformers.keySet());
    }
}
