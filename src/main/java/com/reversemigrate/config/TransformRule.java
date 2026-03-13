package com.reversemigrate.config;

/**
 * Represents a single transformation rule from the jdk-transform-rules.json config.
 * Each rule maps a JDK feature to the transformer class that handles downgrading it.
 */
public class TransformRule {

    private String featureId;
    private int introducedInJdk;
    private String description;
    private String transformerClass;
    private boolean enabled;

    public TransformRule() {
    }

    public TransformRule(String featureId, int introducedInJdk, String description,
                         String transformerClass, boolean enabled) {
        this.featureId = featureId;
        this.introducedInJdk = introducedInJdk;
        this.description = description;
        this.transformerClass = transformerClass;
        this.enabled = enabled;
    }

    public String getFeatureId() {
        return featureId;
    }

    public void setFeatureId(String featureId) {
        this.featureId = featureId;
    }

    public int getIntroducedInJdk() {
        return introducedInJdk;
    }

    public void setIntroducedInJdk(int introducedInJdk) {
        this.introducedInJdk = introducedInJdk;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTransformerClass() {
        return transformerClass;
    }

    public void setTransformerClass(String transformerClass) {
        this.transformerClass = transformerClass;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public String toString() {
        return "TransformRule{featureId='" + featureId + "', introducedInJdk=" + introducedInJdk +
                ", enabled=" + enabled + '}';
    }
}
