package de.tum.cit.aet.artemis.iris;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.ArgumentMatcher;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;

import de.tum.cit.aet.artemis.core.connector.IrisRequestMockProvider;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.iris.domain.settings.IrisSubSettings;
import de.tum.cit.aet.artemis.iris.repository.IrisSettingsRepository;
import de.tum.cit.aet.artemis.iris.service.settings.IrisSettingsService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationLocalCILocalVCTest;

public abstract class AbstractIrisIntegrationTest extends AbstractSpringIntegrationLocalCILocalVCTest {

    @Autowired
    protected IrisSettingsService irisSettingsService;

    @Autowired
    @Qualifier("irisRequestMockProvider")
    protected IrisRequestMockProvider irisRequestMockProvider;

    @Autowired
    protected ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private IrisSettingsRepository irisSettingsRepository;

    @Autowired
    protected ProgrammingExerciseUtilService programmingExerciseUtilService;

    private static final long TIMEOUT_MS = 200;

    @BeforeEach
    void setup() {
        irisRequestMockProvider.enableMockingOfRequests();
    }

    @AfterEach
    void tearDown() throws Exception {
        irisRequestMockProvider.reset();
    }

    protected void activateIrisGlobally() {
        var globalSettings = irisSettingsService.getGlobalSettings();
        activateSubSettings(globalSettings.getIrisProgrammingExerciseChatSettings());
        activateSubSettings(globalSettings.getIrisTextExerciseChatSettings());
        activateSubSettings(globalSettings.getIrisCourseChatSettings());
        activateSubSettings(globalSettings.getIrisLectureIngestionSettings());
        activateSubSettings(globalSettings.getIrisCompetencyGenerationSettings());
        activateSubSettings(globalSettings.getIrisLectureChatSettings());
        activateSubSettings(globalSettings.getIrisFaqIngestionSettings());
        activateSubSettings(globalSettings.getIrisTutorSuggestionSettings());
        irisSettingsRepository.save(globalSettings);
    }

    protected void disableIrisGlobally() {
        var globalSettings = irisSettingsService.getGlobalSettings();
        deactivateSubSettings(globalSettings.getIrisProgrammingExerciseChatSettings());
        deactivateSubSettings(globalSettings.getIrisTextExerciseChatSettings());
        deactivateSubSettings(globalSettings.getIrisCourseChatSettings());
        deactivateSubSettings(globalSettings.getIrisLectureIngestionSettings());
        deactivateSubSettings(globalSettings.getIrisCompetencyGenerationSettings());
        deactivateSubSettings(globalSettings.getIrisLectureChatSettings());
        deactivateSubSettings(globalSettings.getIrisFaqIngestionSettings());
        deactivateSubSettings(globalSettings.getIrisTutorSuggestionSettings());
        irisSettingsRepository.save(globalSettings);
    }

    /**
     * Sets a type of IrisSubSettings to enabled and their preferred model to null.
     *
     * @param settings the settings to be enabled
     */
    private void activateSubSettings(IrisSubSettings settings) {
        settings.setEnabled(true);
        settings.setSelectedVariant("default");
        settings.setAllowedVariants(new TreeSet<>(Set.of("default")));
    }

    /**
     * Sets a type of IrisSubSettings to disabled.
     *
     * @param settings the settings to be disabled
     */
    private void deactivateSubSettings(IrisSubSettings settings) {
        settings.setEnabled(false);
        settings.setSelectedVariant("default");
        settings.setAllowedVariants(new TreeSet<>(Set.of("default")));
    }

    protected void activateIrisFor(Course course) {
        var courseSettings = irisSettingsService.getDefaultSettingsFor(course);

        activateSubSettings(courseSettings.getIrisProgrammingExerciseChatSettings());
        activateSubSettings(courseSettings.getIrisTextExerciseChatSettings());
        activateSubSettings(courseSettings.getIrisLectureChatSettings());
        activateSubSettings(courseSettings.getIrisCourseChatSettings());
        activateSubSettings(courseSettings.getIrisCompetencyGenerationSettings());
        activateSubSettings(courseSettings.getIrisLectureIngestionSettings());
        activateSubSettings(courseSettings.getIrisFaqIngestionSettings());
        activateSubSettings(courseSettings.getIrisTutorSuggestionSettings());

        irisSettingsRepository.save(courseSettings);
    }

    protected void disableCourseChatFor(Course course) {
        var courseSettings = irisSettingsService.getDefaultSettingsFor(course);
        deactivateSubSettings(courseSettings.getIrisCourseChatSettings());
        irisSettingsRepository.save(courseSettings);
    }

    protected void disableProgrammingExerciseChatFor(Course course) {
        var courseSettings = irisSettingsService.getDefaultSettingsFor(course);
        deactivateSubSettings(courseSettings.getIrisProgrammingExerciseChatSettings());
        irisSettingsRepository.save(courseSettings);
    }

    protected void activateIrisFor(Exercise exercise) {
        var exerciseSettings = irisSettingsService.getDefaultSettingsFor(exercise);
        activateSubSettings(exerciseSettings.getIrisProgrammingExerciseChatSettings());
        activateSubSettings(exerciseSettings.getIrisTextExerciseChatSettings());

        irisSettingsRepository.save(exerciseSettings);
    }

    protected void disableIrisFor(Exercise exercise) {
        var exerciseSettings = irisSettingsService.getDefaultSettingsFor(exercise);
        deactivateSubSettings(exerciseSettings.getIrisProgrammingExerciseChatSettings());
        deactivateSubSettings(exerciseSettings.getIrisTextExerciseChatSettings());

        irisSettingsRepository.save(exerciseSettings);
    }

    /**
     * Verify that the given messages were sent through the websocket for the given user and topic.
     *
     * @param userLogin   The user login
     * @param topicSuffix The chat session
     * @param matchers    Argument matchers which describe the messages that should have been sent
     */
    protected void verifyWebsocketActivityWasExactly(String userLogin, String topicSuffix, ArgumentMatcher<?>... matchers) {
        for (ArgumentMatcher<?> callDescriptor : matchers) {
            verifyMessageWasSentOverWebsocket(userLogin, topicSuffix, callDescriptor);
        }
        verifyNumberOfCallsToWebsocket(userLogin, topicSuffix, matchers.length);
    }

    /**
     * Verify that the given message was sent through the websocket for the given user and topic.
     *
     * @param userLogin   The user login
     * @param topicSuffix The topic suffix, e.g. "sessions/123"
     * @param matcher     Argument matcher which describes the message that should have been sent
     */
    protected void verifyMessageWasSentOverWebsocket(String userLogin, String topicSuffix, ArgumentMatcher<?> matcher) {
        // @formatter:off
        verify(websocketMessagingService, timeout(TIMEOUT_MS).times(1))
            .sendMessageToUser(
                eq(userLogin),
                eq("/topic/iris/" + topicSuffix),
                ArgumentMatchers.argThat(matcher)
            );
        // @formatter:on
    }

    /**
     * Verify that exactly `numberOfCalls` messages were sent through the websocket for the given user and topic.
     */
    protected void verifyNumberOfCallsToWebsocket(String userLogin, String topicSuffix, int numberOfCalls) {
        // @formatter:off
        verify(websocketMessagingService, times(numberOfCalls))
            .sendMessageToUser(
                eq(userLogin),
                eq("/topic/iris/" + topicSuffix),
                any()
            );
        // @formatter:on
    }
}
