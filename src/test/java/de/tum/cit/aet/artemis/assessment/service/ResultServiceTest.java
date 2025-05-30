package de.tum.cit.aet.artemis.assessment.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ResultServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "resultservice";

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ResultService resultService;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository participationRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private ProgrammingExercise programmingExercise;

    private ProgrammingExerciseStudentParticipation programmingExerciseStudentParticipation;

    private StudentParticipation examStudentParticipation;

    @BeforeEach
    void reset() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        this.programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        // This is done to avoid proxy issues in the processNewResult method of the ResultService.
        this.programmingExerciseStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(this.programmingExercise, TEST_PREFIX + "student1");
        participationUtilService.addSubmission(this.programmingExerciseStudentParticipation, new ProgrammingSubmission());

        ProgrammingExercise examProgrammingExercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        this.examStudentParticipation = participationUtilService.addStudentParticipationForProgrammingExercise(examProgrammingExercise, TEST_PREFIX + "student1");
        participationUtilService.addSubmission(examStudentParticipation, new ProgrammingSubmission());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testFilterFeedbacksForClientAsTA() {
        this.testFilterFeedbacksForClientAsCurrentUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testFilterFeedbacksForClientAsEditor() {
        this.testFilterFeedbacksForClientAsCurrentUser();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFilterFeedbacksForClientAsInstructor() {
        this.testFilterFeedbacksForClientAsCurrentUser();
    }

    private void testFilterFeedbacksForClientAsCurrentUser() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAreSortedWithManualFirst() {
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousFeedbackTypeFeedbacksToResult(result);

        // The ordering should be the same as is declared in addVariousFeedbackTypeFeedbacksToResult()
        assertThat(resultService.filterFeedbackForClient(result)).isEqualTo(result.getFeedbacks());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInExamsBeforePublish() {
        Exam exam = examStudentParticipation.getExercise().getExam();
        exam.setPublishResultsDate(ZonedDateTime.now().plusDays(2));
        examRepository.save(exam);
        Result result = participationUtilService.addResultToSubmission(null, null, examStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInExamsAfterPublish() {
        Exam exam = examStudentParticipation.getExercise().getExam();
        exam.setPublishResultsDate(ZonedDateTime.now().minusDays(2));
        examRepository.save(exam);
        Result result = participationUtilService.addResultToSubmission(null, null, examStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseBeforeDueDate() {
        programmingExercise.setDueDate(ZonedDateTime.now().plusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseAfterDueDate() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(null, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = resultRepository.save(result);
        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseBeforeAssessmentDueDateWithNonAutomaticResult() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().plusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, null,
                programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream()
                .filter(feedback -> !feedback.isInvisible() && feedback.getType() != null && feedback.getType().equals(FeedbackType.AUTOMATIC)).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseAfterAssessmentDueDateWithNonAutomaticResult() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, null,
                programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudent_shouldFilterInCourseAfterAssessmentDueDateWithAutomaticResult() {
        programmingExercise.setDueDate(ZonedDateTime.now().minusDays(4));
        programmingExercise.setAssessmentDueDate(ZonedDateTime.now().minusDays(2));
        programmingExerciseRepository.save(programmingExercise);
        Result result = participationUtilService.addResultToSubmission(AssessmentType.AUTOMATIC, null,
                programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();

        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudentShouldOnlyFilterAutomaticResultBeforeLastDueDate(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        final var participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().plusDays(2));
        participationRepository.save(participation2);

        Result result = participationUtilService.addResultToSubmission(assessmentType, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks;
        if (AssessmentType.AUTOMATIC == assessmentType) {
            expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible() && !feedback.isAfterDueDate()).toList();
            assertThat(expectedFeedbacks).hasSize(2);
        }
        else {
            expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();
            assertThat(expectedFeedbacks).hasSize(3);
        }
        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(AssessmentType.class)
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "STUDENT")
    void testFilterFeedbacksForClientAsStudentShouldNotFilterAfterLatestDueDate(AssessmentType assessmentType) {
        programmingExercise.setDueDate(ZonedDateTime.now().minusHours(2));
        programmingExercise.setAssessmentDueDate(null);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        final var participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student2");
        participation2.setIndividualDueDate(ZonedDateTime.now().minusHours(1));
        participationRepository.save(participation2);

        Result result = participationUtilService.addResultToSubmission(assessmentType, null, programmingExerciseStudentParticipation.findLatestSubmission().orElseThrow());
        result = participationUtilService.addVariousVisibilityFeedbackToResult(result);
        result = participationUtilService.addFeedbackToResult(ParticipationFactory.createPositiveFeedback(FeedbackType.MANUAL), result);

        List<Feedback> expectedFeedbacks;
        expectedFeedbacks = result.getFeedbacks().stream().filter(feedback -> !feedback.isInvisible()).toList();
        assertThat(expectedFeedbacks).hasSize(3);
        assertThat(resultService.filterFeedbackForClient(result)).containsExactlyInAnyOrderElementsOf(expectedFeedbacks);
    }
}
