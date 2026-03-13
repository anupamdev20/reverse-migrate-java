package com.reversemigrate.transform;

import com.github.javaparser.ast.CompilationUnit;

/**
 * Interface for all JDK feature transformers.
 * Each implementation handles downgrading a specific JDK feature
 * to be compatible with older JDK versions.
 */
public interface FeatureTransformer {

    /**
     * Returns the unique feature identifier matching the config's featureId.
     */
    String featureId();

    /**
     * Transforms the given compilation unit by replacing newer JDK features
     * with equivalent older constructs.
     *
     * @param cu the parsed compilation unit to transform (modified in-place)
     * @return the transformed compilation unit
     */
    CompilationUnit transform(CompilationUnit cu);
}
