package com.reversemigrate.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Result of a source code migration operation.
 * Contains the transformed source code and metadata about applied
 * transformations.
 */
public class MigrationResult {

    private final String originalSource;
    private final String transformedSource;
    private final int sourceJdk;
    private final int targetJdk;
    private final List<String> appliedFeatures;
    private final List<String> warnings;
    private final boolean success;
    private final String errorMessage;

    private MigrationResult(Builder builder) {
        this.originalSource = builder.originalSource;
        this.transformedSource = builder.transformedSource;
        this.sourceJdk = builder.sourceJdk;
        this.targetJdk = builder.targetJdk;
        this.appliedFeatures = Collections.unmodifiableList(builder.appliedFeatures);
        this.warnings = Collections.unmodifiableList(builder.warnings);
        this.success = builder.success;
        this.errorMessage = builder.errorMessage;
    }

    public String getOriginalSource() {
        return originalSource;
    }

    public String getTransformedSource() {
        return transformedSource;
    }

    public int getSourceJdk() {
        return sourceJdk;
    }

    public int getTargetJdk() {
        return targetJdk;
    }

    public List<String> getAppliedFeatures() {
        return appliedFeatures;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasChanges() {
        return !appliedFeatures.isEmpty();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MigrationResult{success=").append(success);
        sb.append(", sourceJdk=").append(sourceJdk);
        sb.append(", targetJdk=").append(targetJdk);
        sb.append(", appliedFeatures=").append(appliedFeatures);
        if (!warnings.isEmpty()) {
            sb.append(", warnings=").append(warnings);
        }
        if (errorMessage != null) {
            sb.append(", error='").append(errorMessage).append('\'');
        }
        sb.append('}');
        return sb.toString();
    }

    // Builder
    public static class Builder {
        private String originalSource;
        private String transformedSource;
        private int sourceJdk;
        private int targetJdk;
        private final List<String> appliedFeatures = new ArrayList<>();
        private final List<String> warnings = new ArrayList<>();
        private boolean success = true;
        private String errorMessage;

        public Builder originalSource(String originalSource) {
            this.originalSource = originalSource;
            return this;
        }

        public Builder transformedSource(String transformedSource) {
            this.transformedSource = transformedSource;
            return this;
        }

        public Builder sourceJdk(int sourceJdk) {
            this.sourceJdk = sourceJdk;
            return this;
        }

        public Builder targetJdk(int targetJdk) {
            this.targetJdk = targetJdk;
            return this;
        }

        public Builder addAppliedFeature(String featureId) {
            this.appliedFeatures.add(featureId);
            return this;
        }

        public Builder addWarning(String warning) {
            this.warnings.add(warning);
            return this;
        }

        public Builder success(boolean success) {
            this.success = success;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public MigrationResult build() {
            return new MigrationResult(this);
        }
    }
}
