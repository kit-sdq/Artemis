package de.tum.cit.aet.artemis.core.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FeatureToggleServiceTest extends AbstractSpringIntegrationIndependentTest {

    // science is disabled by default
    private static final int FEATURES_DISABLED_DEFAULT = 2;

    @Autowired
    private FeatureToggleService featureToggleService;

    @AfterEach
    void checkReset() {
        // Verify that the test has reset the state
        // Must be extended if additional features are added
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isTrue();
        assertThat(featureToggleService.isFeatureEnabled(Feature.PlagiarismChecks)).isTrue();
        assertThat(featureToggleService.isFeatureEnabled(Feature.Exports)).isTrue();
        assertThat(featureToggleService.isFeatureEnabled(Feature.LearningPaths)).isTrue();
        assertThat(featureToggleService.isFeatureEnabled(Feature.StandardizedCompetencies)).isTrue();
        assertThat(featureToggleService.isFeatureEnabled(Feature.StudentCourseAnalyticsDashboard)).isTrue();
        assertThat(featureToggleService.isFeatureEnabled(Feature.Science)).isFalse();
        assertThat(featureToggleService.isFeatureEnabled(Feature.TutorSuggestions)).isFalse();
    }

    @Test
    void testSetFeaturesEnabled() {
        Map<Feature, Boolean> featureStates = new HashMap<>();
        featureStates.put(Feature.ProgrammingExercises, true);
        featureToggleService.updateFeatureToggles(featureStates);
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isTrue();
    }

    @Test
    void testSetFeaturesDisabled() {
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isTrue();

        Map<Feature, Boolean> featureStates = new HashMap<>();
        featureStates.put(Feature.ProgrammingExercises, false);
        featureToggleService.updateFeatureToggles(featureStates);
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isFalse();

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }

    @Test
    void testEnableDisableFeature() {
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isTrue();

        featureToggleService.disableFeature(Feature.ProgrammingExercises);
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isFalse();

        featureToggleService.enableFeature(Feature.ProgrammingExercises);
        assertThat(featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)).isTrue();
    }

    @Test
    void testShouldNotEnableTwice() {
        assertThat(featureToggleService.enabledFeatures()).hasSize(Feature.values().length - FEATURES_DISABLED_DEFAULT);
        featureToggleService.enableFeature(Feature.ProgrammingExercises);

        // Feature should not be added multiple times
        assertThat(featureToggleService.enabledFeatures()).hasSize(Feature.values().length - FEATURES_DISABLED_DEFAULT);
    }

    @Test
    void testShouldNotDisableTwice() {
        featureToggleService.disableFeature(Feature.ProgrammingExercises);

        assertThat(featureToggleService.disabledFeatures()).hasSize(FEATURES_DISABLED_DEFAULT + 1);
        featureToggleService.disableFeature(Feature.ProgrammingExercises);

        // Feature should not be added multiple times
        assertThat(featureToggleService.disabledFeatures()).hasSize(FEATURES_DISABLED_DEFAULT + 1);

        // Reset
        featureToggleService.enableFeature(Feature.ProgrammingExercises);
    }
}
