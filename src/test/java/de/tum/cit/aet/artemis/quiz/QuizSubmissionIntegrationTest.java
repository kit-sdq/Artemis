package de.tum.cit.aet.artemis.quiz;

import static de.tum.cit.aet.artemis.core.config.Constants.EXERCISE_TOPIC_ROOT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.DropLocation;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ScoringType;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSpot;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.QuizBatchJoinDTO;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.service.QuizStatisticService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class QuizSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "quizsubmissiontest";

    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionIntegrationTest.class);

    private static final int NUMBER_OF_STUDENTS = 4;

    private static final int NUMBER_OF_TUTORS = 1;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionTestRepository;

    @Autowired
    private ParticipationTestRepository participationRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private QuizBatchService quizBatchService;

    @Autowired
    private QuizExerciseUtilService quizExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    QuizStatisticService quizStatisticService;

    @Autowired
    ParticipationUtilService participationUtilService;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);
    }

    @AfterEach
    @Override
    protected void resetSpyBeans() {
        super.resetSpyBeans();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmit_CalculateScore() {
        QuizExercise quizExercise = setupQuizExerciseParameters();
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission;

        // only half of the students submit
        for (int i = 1; i <= NUMBER_OF_STUDENTS / 2; i++) {
            quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            quizSubmission.setSubmitted(true);
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // check first half of the submissions
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(NUMBER_OF_STUDENTS / 2);

        for (int i = NUMBER_OF_STUDENTS / 2 + 1; i <= NUMBER_OF_STUDENTS; i++) {
            quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            quizSubmission.setSubmitted(true);
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // all submission are saved to the database
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(NUMBER_OF_STUDENTS);

        // update the statistics
        QuizExercise quizExerciseWithStatistic = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        quizStatisticService.recalculateStatistics(quizExerciseWithStatistic);

        // Test the statistics
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(NUMBER_OF_STUDENTS);
        double questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0.0, Double::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize((int) Math.round(questionScore + 1));
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            log.debug(pointCounter.toString());
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 3.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else if (pointCounter.getPoints() == 3.0 || pointCounter.getPoints() == 4.0 || pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 6.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else if (pointCounter.getPoints() == 7.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 12.0));
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            log.debug(question.getQuizQuestionStatistic().toString());
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 2.0));
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 3.0));
            }
            else {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(NUMBER_OF_STUDENTS / 4);
            }
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isEqualTo(NUMBER_OF_STUDENTS);
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testQuizSubmit_partial_points() {
        QuizExercise quizExercise = setupQuizExerciseParameters();
        // force getting partial points
        quizExercise.getQuizQuestions().getFirst().setScoringType(ScoringType.PROPORTIONAL_WITHOUT_PENALTY);
        quizExercise.getQuizQuestions().get(1).score(1d);
        quizExercise.getQuizQuestions().get(2).score(1d);
        quizExercise = quizExerciseService.save(quizExercise);

        MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) quizExercise.getQuizQuestions().getFirst();
        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) quizExercise.getQuizQuestions().get(1);
        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().get(2);

        List<QuizSubmission> submissions = new ArrayList<>();

        QuizSubmission student1Submission = new QuizSubmission();

        // student 1 achieves 1/4 points for the DnD question
        setupDragAndDropSubmission(dndQuestion, student1Submission, 1);
        // and 0.5 points for the SA question
        setupShortAnswerSubmission(saQuestion, student1Submission, 1);

        // in total, student 1 achieves 0.75 points -> rounded to 1 point
        student1Submission.submitted(true);
        student1Submission.submissionDate(null);
        submissions.add(student1Submission);

        QuizSubmission student2Submission = new QuizSubmission();

        // student 2 achieves 1/4 points for the DnD question
        setupDragAndDropSubmission(dndQuestion, student2Submission, 1);

        // in total, student 2 achieves 0.25 points -> rounded to 0 points
        student2Submission.submitted(true);
        student2Submission.submissionDate(null);
        submissions.add(student2Submission);

        QuizSubmission student3Submission = new QuizSubmission();
        var correctAnswerOption = mcQuestion.getAnswerOptions().stream().filter(AnswerOption::isIsCorrect).findFirst().orElseThrow();

        MultipleChoiceSubmittedAnswer student3mcAnswer = new MultipleChoiceSubmittedAnswer();
        student3mcAnswer.setQuizQuestion(mcQuestion);
        student3mcAnswer.addSelectedOptions(correctAnswerOption);

        // student 3 achieves 4 points for the MC question
        student3Submission.addSubmittedAnswers(student3mcAnswer);

        // and 1 point for the DnD question
        setupDragAndDropSubmission(dndQuestion, student3Submission, 3);
        // and 1 point for the SA question
        setupShortAnswerSubmission(saQuestion, student3Submission, 2);

        // in total, student 3 achieves 6 points
        student3Submission.submitted(true);
        student3Submission.submissionDate(null);
        submissions.add(student3Submission);

        for (int i = 0; i < 3; i++) {
            participationUtilService.addSubmission(quizExercise, submissions.get(i), TEST_PREFIX + "student" + (i + 1));
            participationUtilService.addResultToSubmission(submissions.get(i), AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(submissions.get(i)), true);
        }

        // update the statistics
        QuizExercise quizExerciseWithStatistic = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        quizStatisticService.recalculateStatistics(quizExerciseWithStatistic);

        var quizPointStatistic = quizExerciseWithStatistic.getQuizPointStatistic();
        assertThat(quizExerciseWithStatistic).isNotNull();

        for (var pointCounter : quizPointStatistic.getPointCounters()) {
            assertThat(pointCounter.getUnRatedCounter()).as("Unrated counter is always 0").isZero();
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 0.0 contains 0 rated submission -> 0.33 points").isEqualTo(1);
            }
            else if (pointCounter.getPoints() == 1.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 1.0 contains 1 rated submission -> 1 point").isEqualTo(1);
            }
            else if (pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).as("Bucket 6.0 contains 1 rated submission -> 6 points").isEqualTo(1);
            }
            else {
                assertThat(pointCounter.getRatedCounter()).as("All other buckets contain 0 rated submissions").isZero();
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitLiveMode_forbidden_notActive(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().plusSeconds(20), ZonedDateTime.now().plusSeconds(30), quizMode);
        quizExercise.setDuration(10);
        quizExercise = quizExerciseService.save(quizExercise);

        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource
    void testQuizStartParticipationCorrectDataWhileActive_asStudent(QuizMode quizMode) throws Exception {
        var quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusSeconds(20), ZonedDateTime.now().plusHours(1), quizMode);
        quizExercise.setDuration(500);
        quizExercise = exerciseRepository.save(quizExercise);

        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);

        if (quizMode != QuizMode.SYNCHRONIZED) {
            var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().minusSeconds(10)));
            request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(batch.getPassword()), QuizBatch.class, HttpStatus.OK);
        }
        var participation = request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class,
                HttpStatus.OK);

        assertThat(participation).isNotNull();
        Exercise exercise = participation.getExercise();
        assertThat(exercise).isNotNull();
        assertThat(exercise).isInstanceOf(QuizExercise.class);

        QuizExercise quizExerciseFromParticipation = (QuizExercise) exercise;
        List<QuizQuestion> questions = quizExerciseFromParticipation.getQuizQuestions();
        assertThat(questions).isNotNull();
        assertThat(questions).hasSize(3);
        assertThat(questions.get(0)).isInstanceOf(MultipleChoiceQuestion.class);
        assertThat(questions.get(1)).isInstanceOf(DragAndDropQuestion.class);
        assertThat(questions.get(2)).isInstanceOf(ShortAnswerQuestion.class);

        MultipleChoiceQuestion mcQuestion = (MultipleChoiceQuestion) questions.get(0);
        for (var answerOption : mcQuestion.getAnswerOptions()) {
            assertThat(answerOption.getExplanation()).isNull();
            assertThat(answerOption.isIsCorrect()).isNull();
        }

        DragAndDropQuestion dndQuestion = (DragAndDropQuestion) questions.get(1);
        assertThat(dndQuestion.getCorrectMappings()).hasSize(0);

        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) questions.get(2);
        assertThat(saQuestion.getCorrectMappings()).hasSize(0);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testQuizSubmitEmptyQuizInLiveMode() throws Exception {
        int invalidExerciseId = -1;

        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusHours(5), null, QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(350);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now().minusMinutes(5)));
        quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, false, ZonedDateTime.now());

        // submit quiz more times than the allowed number of attempts
        request.postWithResponseBody("/api/quiz/exercises/" + invalidExerciseId + "/submissions/live", quizSubmission, Result.class, HttpStatus.FORBIDDEN);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @EnumSource(QuizMode.class)
    void testQuizSubmitPractice(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusSeconds(10), ZonedDateTime.now().minusSeconds(8), quizMode);
        quizExercise.setDuration(2);
        quizExercise.setIsOpenForPractice(true);
        quizExerciseService.save(quizExercise);

        // at the beginning there are no submissions and participants
        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExercise.getId())).isEmpty();
        assertThat(participationRepository.findByExerciseId(quizExercise.getId())).isEmpty();

        // submit 10 times for 10 different students
        for (int i = 1; i <= NUMBER_OF_STUDENTS; i++) {
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, true, null);
            userUtilService.changeUser(TEST_PREFIX + "student" + i);

            Result receivedResult = request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/practice", quizSubmission, Result.class,
                    HttpStatus.OK);
            assertThat(((QuizSubmission) receivedResult.getSubmission()).getSubmittedAnswers()).hasSameSizeAs(quizSubmission.getSubmittedAnswers());
        }

        // all submission are saved to the database
        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExercise.getId())).hasSize(NUMBER_OF_STUDENTS);
        assertThat(participationRepository.findByExerciseId(quizExercise.getId())).hasSize(NUMBER_OF_STUDENTS);

        // update the statistics
        QuizExercise quizExerciseWithStatistic = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        quizStatisticService.recalculateStatistics(quizExerciseWithStatistic);

        // Test the statistics
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isEqualTo(NUMBER_OF_STUDENTS);
        double questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0.0, Double::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize((int) Math.round(questionScore + 1));
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 3.0));
            }
            else if (pointCounter.getPoints() == 3.0 || pointCounter.getPoints() == 4.0 || pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 6.0));
            }
            else if (pointCounter.getPoints() == 7.0) {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 12.0));
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 2.0));
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(Math.round(NUMBER_OF_STUDENTS / 3.0));
            }
            else {
                assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isEqualTo(NUMBER_OF_STUDENTS / 4);
            }
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isEqualTo(NUMBER_OF_STUDENTS);
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPractice_badRequest() throws Exception {
        QuizExercise quizExerciseServer = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusSeconds(4), ZonedDateTime.now().minusSeconds(2), QuizMode.SYNCHRONIZED);
        quizExerciseServer.setDuration(2);
        quizExerciseServer.setIsOpenForPractice(false);
        quizExerciseService.save(quizExerciseServer);

        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExerciseServer.getId())).isEmpty();

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExerciseServer.getQuizQuestions()) {
            for (int i = 1; i <= 10; i++) {
                var answer = QuizExerciseFactory.generateSubmittedAnswerFor(question, i % 2 == 0);
                quizSubmission.addSubmittedAnswers(answer);
                // also remove once
                quizSubmission.removeSubmittedAnswers(answer);
                quizSubmission.addSubmittedAnswers(answer);
            }
        }
        quizSubmission.setSubmitted(true);
        // quiz not open for practice --> bad request expected
        Result result = request.postWithResponseBody("/api/quiz/exercises/" + quizExerciseServer.getId() + "/submissions/practice", quizSubmission, Result.class,
                HttpStatus.BAD_REQUEST);
        assertThat(result).isNull();
        verifyNoWebsocketMessageForExercise(quizExerciseServer);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPractice_accessForbidden_exam() throws Exception {
        ExerciseGroup exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        QuizExercise quizExerciseServer = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        quizExerciseService.save(quizExerciseServer);

        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExerciseServer.getId())).isEmpty();

        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExerciseServer, 1, true, null);
        // exam quiz not open for practice --> bad request expected
        Result result = request.postWithResponseBody("/api/quiz/exercises/" + quizExerciseServer.getId() + "/submissions/practice", quizSubmission, Result.class,
                HttpStatus.FORBIDDEN);
        assertThat(result).isNull();
        verifyNoWebsocketMessageForExercise(quizExerciseServer);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPreview_forbidden() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testQuizSubmitPractice_forbidden() throws Exception {
        Course course = courseUtilService.createCourse();
        course.setStudentGroupName("abc");
        courseRepository.save(course);
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/practice", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
        verifyNoWebsocketMessageForExercise(quizExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testQuizSubmitPreview_forbidden_otherTa() throws Exception {
        Course course = courseUtilService.createCourse();
        course.setTeachingAssistantGroupName("tutor2");
        courseRepository.save(course);
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        quizExerciseService.save(quizExercise);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPreview_badRequest_noQuiz() throws Exception {
        request.postWithResponseBody("/api/quiz/exercises/" + 11223344 + "/submissions/preview", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPractice_badRequest_noQuiz() throws Exception {
        request.postWithResponseBody("/api/quiz/exercises/" + 11223344 + "/submissions/practice", new QuizSubmission(), Result.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPreview_badRequest_submissionId() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        var quizSubmission = new QuizSubmission();
        quizSubmission.setId(1L);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitPractice_badRequest_submissionId() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        var quizSubmission = new QuizSubmission();
        quizSubmission.setId(1L);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/practice", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @EnumSource(QuizMode.class)
    void testQuizSubmitPreview(QuizMode quizMode) throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusSeconds(4), null, quizMode);
        quizExerciseService.save(quizExercise);

        int numberOfParticipants = 10;

        for (int i = 1; i <= numberOfParticipants; i++) {
            QuizSubmission quizSubmission = new QuizSubmission();
            for (var question : quizExercise.getQuizQuestions()) {
                quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerFor(question, i % 2 == 0));
            }
            Result receivedResult = request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/preview", quizSubmission, Result.class,
                    HttpStatus.OK);
            assertThat(((QuizSubmission) receivedResult.getSubmission()).getSubmittedAnswers()).hasSameSizeAs(quizSubmission.getSubmittedAnswers());
        }

        // in the preview the submission will not be saved to the database
        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExercise.getId())).isEmpty();

        // update the statistics
        QuizExercise quizExerciseWithStatistic = quizExerciseTestRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExercise.getId());
        quizStatisticService.recalculateStatistics(quizExerciseWithStatistic);

        // all stats must be 0 because we have a preview here
        // Test the statistics
        assertThat(quizExerciseWithStatistic).isNotNull();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isZero();
        double questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0.0, Double::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isEqualTo(questionScore);
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize((int) Math.round(questionScore + 1));
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            assertThat(pointCounter.getRatedCounter()).isZero();
            assertThat(pointCounter.getUnRatedCounter()).isZero();
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitScheduledAndDeleted() throws Exception {
        Course course = courseUtilService.createCourse();
        String publishQuizPath = "/topic/courses/" + course.getId() + "/quizExercises";
        log.debug("// Creating the quiz exercise 2s in the future");
        var initialReleaseDate = ZonedDateTime.now().plusSeconds(2);
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(initialReleaseDate));
        quizExercise.duration(60);

        // also schedules the quiz
        log.debug("// Saving the quiz initially");
        quizExercise = quizExerciseService.save(quizExercise);
        checkQuizNotStarted(publishQuizPath);

        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);

        // check that submission fails
        QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, null);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();

        // reschedule
        log.debug("// Rescheduling the quiz to now");
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(ZonedDateTime.now()));
        quizExercise = quizExerciseService.save(quizExercise);

        // check that quiz and quiz batches have started
        log.debug("// Check that the quiz has started");
        assertThat(quizExercise.isQuizStarted()).isTrue();
        assertThat(quizExercise.getQuizBatches()).allMatch(QuizBatch::isStarted);

        // save submissions
        for (int i = 1; i <= 2; i++) {
            quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i, false, null);
            participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student" + i);
            participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);
        }

        // set the quiz end to now and ...
        log.debug("// End the quiz and delete it");
        quizExercise = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExercise).isNotNull();
        quizExercise.setDuration((int) Duration.between(quizExercise.getReleaseDate(), ZonedDateTime.now()).getSeconds() - Constants.QUIZ_GRACE_PERIOD_IN_SECONDS);
        quizExercise = exerciseRepository.saveAndFlush(quizExercise);

        // ...delete the quiz
        request.delete("/api/quiz/quiz-exercises/" + quizExercise.getId(), HttpStatus.OK);

        QuizExercise finalQuizExercise = quizExercise;
        await().until(() -> exerciseRepository.findById(finalQuizExercise.getId()).isEmpty());

        // no submissions left
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isZero();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student4", roles = "USER")
    void testQuizScoringTypes() throws IOException {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusMinutes(1), null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(60);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }
        quizSubmission.submitted(true);
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student4");
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        quizExerciseService.reEvaluate(quizExercise, quizExercise, generateMultipartFilesFromQuizExercise(quizExercise));
        assertThat(quizSubmissionTestRepository.findByQuizExerciseId(quizExercise.getId())).isPresent();

        List<Result> results = resultRepository.findBySubmissionParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        assertThat(results).hasSize(1);
        var result = results.getFirst();

        assertThat(result.getScore()).isEqualTo(11.1);

        quizSubmission = quizSubmissionTestRepository.findWithEagerSubmittedAnswersById(result.getSubmission().getId());
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            // MC submitted answers 0 points as one correct and one false -> ALL_OR_NOTHING
            // or
            // DND submitted answers 0 points as one correct and two false -> PROPORTIONAL_WITH_PENALTY
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer || submittedAnswer instanceof DragAndDropSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isZero();
            } // SA submitted answers 1 points as one correct and one false -> PROPORTIONAL_WITHOUT_PENALTY
            else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(1D);
            }
        }
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @EnumSource(ScoringType.class)
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testQuizScoringType(ScoringType scoringType) throws IOException {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusMinutes(1), null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(60);
        quizExercise.setQuizQuestions(quizExercise.getQuizQuestions().stream().peek(quizQuestion -> quizQuestion.setScoringType(scoringType)).toList());
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (var question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }
        quizSubmission.submitted(true);
        participationUtilService.addSubmission(quizExercise, quizSubmission, TEST_PREFIX + "student3");
        participationUtilService.addResultToSubmission(quizSubmission, AssessmentType.AUTOMATIC, null, quizExercise.getScoreForSubmission(quizSubmission), true);

        quizExerciseService.reEvaluate(quizExercise, quizExercise, generateMultipartFilesFromQuizExercise(quizExercise));
        assertThat(submissionRepository.countByExerciseIdSubmitted(quizExercise.getId())).isEqualTo(1);

        List<Result> results = resultRepository.findBySubmissionParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        assertThat(results).hasSize(1);
        var result = results.getFirst();

        double expectedScore = switch (scoringType) {
            case ALL_OR_NOTHING, PROPORTIONAL_WITH_PENALTY -> 0;
            case PROPORTIONAL_WITHOUT_PENALTY -> 41.7;
        };
        assertThat(result.getScore()).isEqualTo(expectedScore);
    }

    private List<MultipartFile> generateMultipartFilesFromQuizExercise(QuizExercise quizExercise) {
        return quizExercise.getQuizQuestions().stream().filter(quizQuestion -> quizQuestion instanceof DragAndDropQuestion).map(quizQuestion -> (DragAndDropQuestion) quizQuestion)
                .flatMap(quizQuestion -> {
                    var list = new ArrayList<MultipartFile>();
                    if (quizQuestion.getBackgroundFilePath() != null) {
                        list.add(new MockMultipartFile("files", quizQuestion.getBackgroundFilePath(), MediaType.IMAGE_PNG_VALUE, "test".getBytes()));
                    }
                    list.addAll(quizQuestion.getDragItems().stream().filter(dragItem -> dragItem.getPictureFilePath() != null)
                            .map(dragItem -> new MockMultipartFile("files", dragItem.getPictureFilePath(), MediaType.IMAGE_PNG_VALUE, "test".getBytes())).toList());
                    return list.stream();
                }).toList();
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    @ValueSource(booleans = { true, false })
    void submitExercise_shortAnswer_tooLarge(boolean tooLarge) throws Exception {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = QuizExerciseFactory.generateQuizExercise(ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now().plusSeconds(10), QuizMode.SYNCHRONIZED,
                course);
        quizExercise.addQuestions(QuizExerciseFactory.createShortAnswerQuestion());
        quizExercise.setDuration(10);
        quizExercise = quizExerciseService.save(quizExercise);

        ShortAnswerQuestion saQuestion = (ShortAnswerQuestion) quizExercise.getQuizQuestions().getFirst();
        ShortAnswerSubmittedAnswer submittedAnswer = new ShortAnswerSubmittedAnswer();
        submittedAnswer.setQuizQuestion(saQuestion);
        List<ShortAnswerSpot> spots = saQuestion.getSpots();

        ShortAnswerSubmittedText text = new ShortAnswerSubmittedText();
        text.setSpot(spots.getFirst());
        char[] chars = new char[(int) (Constants.MAX_QUIZ_SHORT_ANSWER_TEXT_LENGTH + (tooLarge ? 1 : 0))];
        Arrays.fill(chars, 'a');
        text.setText(new String(chars));
        submittedAnswer.addSubmittedTexts(text);

        QuizSubmission quizSubmission = new QuizSubmission();
        quizSubmission.addSubmittedAnswers(submittedAnswer);
        request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);

        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/live", quizSubmission, Result.class,
                tooLarge ? HttpStatus.BAD_REQUEST : HttpStatus.OK);
    }

    private void checkQuizNotStarted(String path) {
        // check that quiz has not started now
        log.debug("// Check that the quiz has not started and submissions are not allowed");
        verify(websocketMessagingService, never()).sendMessage(eq(path), any());
    }

    private void setupShortAnswerSubmission(ShortAnswerQuestion saQuestion, QuizSubmission submission, int amountOfCorrectAnswers) {
        ShortAnswerSubmittedAnswer submittedAnswer = new ShortAnswerSubmittedAnswer();
        submittedAnswer.setQuizQuestion(saQuestion);
        List<ShortAnswerSpot> spots = saQuestion.getSpots();

        for (int i = 0; i < amountOfCorrectAnswers; i++) {
            ShortAnswerSubmittedText text = new ShortAnswerSubmittedText();
            text.setSpot(spots.get(i));
            var correctSolution = saQuestion.getCorrectSolutionForSpot(spots.get(i)).iterator().next().getText();
            text.setText(correctSolution);
            submittedAnswer.addSubmittedTexts(text);
        }

        submission.addSubmittedAnswers(submittedAnswer);
    }

    private void setupDragAndDropSubmission(DragAndDropQuestion dndQuestion, QuizSubmission submission, int amountOfCorrectAnswers) {
        List<DragItem> dragItems = dndQuestion.getDragItems();
        List<DropLocation> dropLocations = dndQuestion.getDropLocations();

        DragAndDropSubmittedAnswer submittedDndAnswer = new DragAndDropSubmittedAnswer();
        submittedDndAnswer.setQuizQuestion(dndQuestion);

        for (int i = 0; i < amountOfCorrectAnswers; i++) {
            submittedDndAnswer.addMappings(new DragAndDropMapping().dragItem(dragItems.get(i)).dropLocation(dropLocations.get(i)));
        }

        submission.addSubmittedAnswers(submittedDndAnswer);
    }

    private QuizExercise setupQuizExerciseParameters() {
        Course course = quizExerciseUtilService.addCourseWithOneQuizExercise();
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now(), null, QuizMode.SYNCHRONIZED);
        quizExercise.duration(240);
        return quizExercise;
    }

    private void verifyNoWebsocketMessageForExercise(QuizExercise exercise) {
        String topic = EXERCISE_TOPIC_ROOT + exercise.getId() + "/newResults";
        verify(websocketMessagingService, never()).sendMessage(eq(topic), any());
        verify(websocketMessagingService, never()).sendMessageToUser(any(), eq(topic), any());
    }

    @Nested
    @Isolated
    class QuizSubmitLiveModeIsolatedTest {

        @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        @EnumSource(QuizMode.class)
        void testQuizSubmitLiveMode(QuizMode quizMode) throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusMinutes(2), null, quizMode);
            quizExercise.setDuration(600);
            quizExercise = quizExerciseService.save(quizExercise);

            // at the beginning there are no submissions and no participants
            assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExercise.getId())).isEmpty();
            assertThat(participationRepository.findByExerciseId(quizExercise.getId())).isEmpty();

            request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);

            if (quizMode != QuizMode.SYNCHRONIZED) {
                var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().minusSeconds(10)));
                request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(batch.getPassword()), QuizBatch.class,
                        HttpStatus.OK);

                // quizExerciseUtilService.joinQuizBatch(quizExercise, batch, TEST_PREFIX + "student1");
            }

            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, false, null);

            QuizSubmission updatedSubmission = request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/live?submit=true", quizSubmission,
                    QuizSubmission.class, HttpStatus.OK);
            // check whether submission flag was updated
            assertThat(updatedSubmission.isSubmitted()).isTrue();
            // check whether all answers were submitted properly
            assertThat(updatedSubmission.getSubmittedAnswers()).hasSameSizeAs(quizSubmission.getSubmittedAnswers());
            // check whether submission date was set
            assertThat(updatedSubmission.getSubmissionDate()).isNotNull();
        }

        @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
        @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
        @EnumSource(QuizMode.class)
        void testQuizSubmitLiveMode_badRequest_alreadySubmitted(QuizMode quizMode) throws Exception {
            QuizExercise quizExercise = quizExerciseUtilService.createQuiz(ZonedDateTime.now().minusSeconds(5), ZonedDateTime.now().plusSeconds(10), quizMode);
            quizExercise.setDuration(10);
            quizExercise = quizExerciseService.save(quizExercise);

            request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/start-participation", null, StudentParticipation.class, HttpStatus.OK);

            if (quizMode != QuizMode.SYNCHRONIZED) {
                var batch = quizBatchService.save(QuizExerciseFactory.generateQuizBatch(quizExercise, ZonedDateTime.now().minusSeconds(5)));
                request.postWithResponseBody("/api/quiz/quiz-exercises/" + quizExercise.getId() + "/join", new QuizBatchJoinDTO(batch.getPassword()), QuizBatch.class,
                        HttpStatus.OK);
            }

            // create a submission for the first time
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, 1, true, ZonedDateTime.now());
            // submit quiz for the second time, expected status = BAD_REQUEST
            request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/live?submit=true", quizSubmission, Result.class, HttpStatus.OK);
            request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/live?submit=true", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitForTraining_shouldReturnResultAndSetSubmissionProperties() throws Exception {
        // Setup: Create a course and a quiz exercise
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusMinutes(2), null, QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(60);
        quizExercise.setIsOpenForPractice(true);
        quizExercise = quizExerciseService.save(quizExercise);

        // Create a quiz submission with answers
        QuizSubmission quizSubmission = new QuizSubmission();
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }

        // REST API Call: Submit the quiz for training
        Result result = request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/training", quizSubmission, Result.class, HttpStatus.OK);

        assertThat(result).isNotNull();
        assertThat(result.getSubmission()).isInstanceOf(QuizSubmission.class);
        QuizSubmission returnedSubmission = (QuizSubmission) result.getSubmission();
        assertThat(returnedSubmission.isSubmitted()).isTrue();
        assertThat(returnedSubmission.getSubmissionDate()).isNotNull();
        assertThat(returnedSubmission.getSubmittedAnswers()).hasSameSizeAs(quizSubmission.getSubmittedAnswers());

        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExercise.getId())).isEmpty();
        assertThat(participationRepository.findByExerciseId(quizExercise.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testQuizSubmitTraining_badRequest_submissionId() throws Exception {
        QuizExercise quizExercise = quizExerciseUtilService.createAndSaveQuiz(ZonedDateTime.now().minusSeconds(4), null, QuizMode.SYNCHRONIZED);
        var quizSubmission = new QuizSubmission();
        quizSubmission.setId(1L);
        request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/training", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitForTraining_badRequest_notOpenForPracticeOrNotEnded() throws Exception {
        Course course = courseUtilService.createCourse();
        QuizExercise quizExercise = QuizExerciseFactory.createQuiz(course, ZonedDateTime.now().minusMinutes(2), null, QuizMode.SYNCHRONIZED);
        quizExercise.setDuration(60);
        quizExercise.setIsOpenForPractice(false);
        quizExercise = quizExerciseService.save(quizExercise);

        QuizSubmission quizSubmission = new QuizSubmission();
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            quizSubmission.addSubmittedAnswers(QuizExerciseFactory.generateSubmittedAnswerForQuizWithCorrectAndFalseAnswers(question));
        }

        Result result = request.postWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/training", quizSubmission, Result.class, HttpStatus.BAD_REQUEST);
    }
}
