package de.tum.cit.aet.artemis.exercise.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.assertj.core.api.Condition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ParticipationLifecycle;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ParticipationLifecycleServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "partlcservice";

    @Autowired
    private ParticipationLifecycleService participationLifecycleService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation participation;

    private final Runnable noop = () -> {
    };

    @BeforeEach
    void reset() {
        SecurityUtils.setAuthorizationObject();

        userUtilService.addUsers(TEST_PREFIX, 1, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        participation = participationUtilService.addStudentParticipationForProgrammingExercise(this.programmingExercise, TEST_PREFIX + "student1");
    }

    @Test
    void scheduleTaskNoBuildAndTestDate() {
        setupExerciseAndParticipation(null, ZonedDateTime.now().plusHours(1));

        // should not be scheduled at all
        var scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat(scheduledTask).isEmpty();
    }

    @Test
    void scheduleTaskOnlyBuildAndTestAfterDueDate() {
        setupExerciseAndParticipation(ZonedDateTime.now().plusHours(1), null);

        // should still be scheduled even if no individual due date affects the scheduling
        var scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat(scheduledTask).is(scheduledInMinutes(60));
    }

    @Test
    void scheduleTaskDueDateBeforeBuildAndTestDate() {
        setupExerciseAndParticipation(ZonedDateTime.now().plusHours(2), ZonedDateTime.now().plusHours(1));

        // scheduling should choose proper build and test after due date as date
        var scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat(scheduledTask).is(scheduledInMinutes(120));
    }

    @Test
    void scheduleTaskDueDateAfterBuildAndTestDate() {
        setupExerciseAndParticipation(ZonedDateTime.now().plusHours(1), ZonedDateTime.now().plusHours(2));

        // scheduling should choose individual due date (after build and test date) as scheduling date
        var scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.BUILD_AND_TEST_AFTER_DUE_DATE, noop);
        assertThat(scheduledTask).is(scheduledInMinutes(120));
    }

    @Test
    void scheduleOnIndividualDueDate() {
        setupExerciseAndParticipation(null, ZonedDateTime.now().plusHours(2));

        // scheduling should choose individual due date as scheduling date
        var scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.DUE, noop);
        assertThat(scheduledTask).is(scheduledInMinutes(120));
    }

    @Test
    void scheduleNoDueDate() {
        setupExerciseAndParticipation(null, null);
        programmingExercise.setDueDate(null);

        // should not be scheduled at all
        var scheduledTask = participationLifecycleService.scheduleTask(participation, ParticipationLifecycle.DUE, noop);
        assertThat(scheduledTask).isEmpty();
    }

    private void setupExerciseAndParticipation(ZonedDateTime exerciseBuildAndTestDate, ZonedDateTime individualDueDate) {
        programmingExercise.setDueDate(ZonedDateTime.now());
        programmingExercise.setBuildAndTestStudentSubmissionsAfterDueDate(exerciseBuildAndTestDate);

        participation.setIndividualDueDate(individualDueDate);
        participation.setExercise(programmingExercise);
    }

    private Condition<Optional<ScheduledFuture<?>>> scheduledInMinutes(long minutes) {
        return new Condition<>(s -> Math.abs(s.orElseThrow().getDelay(TimeUnit.MINUTES) - minutes) <= 1, "scheduled in %d minutes", minutes);
    }
}
