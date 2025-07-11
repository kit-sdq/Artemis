package de.tum.cit.aet.artemis.exam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.awaitility.Awaitility.await;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExerciseGroupRepository;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.service.QuizExerciseService;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizExerciseTestRepository;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.quiz.util.QuizExerciseFactory;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class ExamQuizServiceTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "eqservicetest";

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionRepository;

    @Autowired
    private ExerciseGroupRepository exerciseGroupRepository;

    @Autowired
    private QuizExerciseService quizExerciseService;

    @Autowired
    private QuizExerciseTestRepository quizExerciseTestRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private QuizExercise quizExercise;

    private Course course;

    private Exam exam;

    private ExerciseGroup exerciseGroup;

    private static final int NUMBER_OF_STUDENTS = 6;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 0, 1);
        course = courseUtilService.addEmptyCourse();
        exam = examUtilService.addExamWithExerciseGroup(course, true);
        exam.setStartDate(ZonedDateTime.now().minusHours(1));
        exam.setEndDate(ZonedDateTime.now().plusHours(1));
        exam.setWorkingTime(2 * 60 * 60);
        exam.setNumberOfExercisesInExam(1);
        exerciseGroup = exam.getExerciseGroups().getFirst();

        quizExercise = QuizExerciseFactory.createQuizForExam(exerciseGroup);
        exerciseGroup.addExercise(quizExercise);

        // Add an instructor who is not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor6");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void evaluateQuiz_asTutor_PreAuth_forbidden() throws Exception {
        evaluateQuiz_authorization_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void evaluateQuiz_asStudent_PreAuth_forbidden() throws Exception {
        evaluateQuiz_authorization_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor6", roles = "INSTRUCTOR")
    void evaluateQuiz_asInstructorNotInCourse_forbidden() throws Exception {
        evaluateQuiz_authorization_forbidden();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void evaluateQuiz_authorization_forbidden() throws Exception {
        exam = examRepository.save(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);

        request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class,
                HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz_testExam_forbidden() throws Exception {
        exam.setTestExam(true);
        exam.setEndDate(ZonedDateTime.now().minusMinutes(30));
        exam = examRepository.save(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);

        request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz_notOver_badRequest() throws Exception {
        exam = examRepository.save(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);

        request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz() throws Exception {
        exam = examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, NUMBER_OF_STUDENTS);

        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamService.generateStudentExams(exam)).hasSize(NUMBER_OF_STUDENTS);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(NUMBER_OF_STUDENTS);
        assertThat(studentExamService.startExercises(exam.getId()).join()).isEqualTo(NUMBER_OF_STUDENTS);

        for (int i = 0; i < NUMBER_OF_STUDENTS; i++) {
            userUtilService.changeUser(TEST_PREFIX + "student" + (i + 1));
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);
        }
        waitForParticipantScores();

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody(
                "/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class, HttpStatus.OK);

        waitForParticipantScores();

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        checkStatistics(quizExercise);

        studentExamRepository.deleteAllInBatch(studentExamRepository.findByExamId(exam.getId()));

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void evaluateQuizWithNoSubmissions() throws Exception {
        exam = examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, NUMBER_OF_STUDENTS);

        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamService.generateStudentExams(exam)).hasSize(NUMBER_OF_STUDENTS);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(NUMBER_OF_STUDENTS);

        // add participations with no submissions
        for (int i = 0; i < NUMBER_OF_STUDENTS; i++) {
            final var user = userUtilService.getUserByLogin(TEST_PREFIX + "student" + (i + 1));
            var participation = new StudentParticipation();
            participation.setExercise(quizExercise);
            participation.setParticipant(user);
            participation.setInitializationDate(ZonedDateTime.now());
            participation.setInitializationState(InitializationState.INITIALIZED);
            studentParticipationRepository.save(participation);
        }

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody(
                "/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class, HttpStatus.OK);

        waitForParticipantScores();

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        studentExamRepository.deleteAllInBatch(studentExamRepository.findByExamId(exam.getId()));

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    private void waitForParticipantScores() {
        participantScoreScheduleService.executeScheduledTasks();
        await().until(() -> participantScoreScheduleService.isIdle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void evaluateQuizWithMultipleSubmissions() throws Exception {
        exam = examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, NUMBER_OF_STUDENTS);

        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamService.generateStudentExams(exam)).hasSize(NUMBER_OF_STUDENTS);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(NUMBER_OF_STUDENTS);
        assertThat(studentExamService.startExercises(exam.getId()).join()).isEqualTo(NUMBER_OF_STUDENTS);

        for (int i = 0; i < NUMBER_OF_STUDENTS; i++) {
            final var user = userUtilService.getUserByLogin(TEST_PREFIX + "student" + (i + 1));
            userUtilService.changeUser(user.getLogin());
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);

            // add another submission manually to trigger multiple submission branch of evaluateQuizSubmission
            final var studentParticipation = studentParticipationRepository
                    .findWithEagerSubmissionsByExerciseIdAndStudentLoginAndTestRun(quizExercise.getId(), user.getLogin(), false).orElseThrow();
            QuizSubmission quizSubmission2 = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            quizSubmission2.setParticipation(studentParticipation);
            quizSubmissionRepository.save(quizSubmission2);
        }
        waitForParticipantScores();

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody(
                "/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class, HttpStatus.OK);
        waitForParticipantScores();

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        checkStatistics(quizExercise);

        studentExamRepository.deleteAllInBatch(studentExamRepository.findByExamId(exam.getId()));

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void evaluateQuiz_twice() throws Exception {
        exam = examUtilService.registerUsersForExamAndSaveExam(exam, TEST_PREFIX, NUMBER_OF_STUDENTS);

        exerciseGroup.setExam(exam);
        exerciseGroup = exerciseGroupRepository.save(exerciseGroup);
        exam.setExerciseGroups(List.of(exerciseGroup));
        quizExercise.setExerciseGroup(exerciseGroup);
        quizExercise = quizExerciseService.save(quizExercise);
        exerciseGroup.setExercises(Set.of(quizExercise));

        assertThat(studentExamService.generateStudentExams(exam)).hasSize(NUMBER_OF_STUDENTS);
        assertThat(studentExamRepository.findByExamId(exam.getId())).hasSize(NUMBER_OF_STUDENTS);
        assertThat(studentExamService.startExercises(exam.getId()).join()).isEqualTo(NUMBER_OF_STUDENTS);

        for (int i = 0; i < NUMBER_OF_STUDENTS; i++) {
            userUtilService.changeUser(TEST_PREFIX + "student" + (i + 1));
            QuizSubmission quizSubmission = QuizExerciseFactory.generateSubmissionForThreeQuestions(quizExercise, i + 1, true, ZonedDateTime.now());
            request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);
        }
        waitForParticipantScores();

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // All exams should be over before evaluation
        for (StudentExam studentExam : studentExamRepository.findByExamId(exam.getId())) {
            studentExam.setWorkingTime(0);
            studentExamRepository.save(studentExam);
        }

        Integer numberOfEvaluatedExercises = request.postWithResponseBody(
                "/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), Integer.class, HttpStatus.OK);
        waitForParticipantScores();

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        // Evaluate quiz twice
        numberOfEvaluatedExercises = request.postWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises",
                Optional.empty(), Integer.class, HttpStatus.OK);
        waitForParticipantScores();

        assertThat(numberOfEvaluatedExercises).isEqualTo(1);

        checkStatistics(quizExercise);

        studentExamRepository.deleteAllInBatch(studentExamRepository.findByExamId(exam.getId()));

        // Make sure delete also works if so many objects have been created before
        request.delete("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    private void checkStatistics(QuizExercise quizExercise) {
        QuizExercise quizExerciseWithStatistic = quizExerciseTestRepository.findOneWithQuestionsAndStatistics(quizExercise.getId());
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsUnrated()).isZero();
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getParticipantsRated()).isEqualTo(NUMBER_OF_STUDENTS);

        double questionScore = quizExerciseWithStatistic.getQuizQuestions().stream().map(QuizQuestion::getPoints).reduce(0.0, Double::sum);
        assertThat(quizExerciseWithStatistic.getMaxPoints()).isCloseTo(questionScore, within(0.0001));
        assertThat(quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()).hasSize((int) Math.round(questionScore + 1));
        // check general statistics
        for (var pointCounter : quizExerciseWithStatistic.getQuizPointStatistic().getPointCounters()) {
            // MC, DnD and short Answer are all incorrect
            if (pointCounter.getPoints() == 0.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(NUMBER_OF_STUDENTS - NUMBER_OF_STUDENTS / 2 - NUMBER_OF_STUDENTS / 3 + NUMBER_OF_STUDENTS / 6);
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            // only DnD is correct
            else if (pointCounter.getPoints() == 3.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(NUMBER_OF_STUDENTS / 3 - NUMBER_OF_STUDENTS / 6);
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            // only MC is correct
            else if (pointCounter.getPoints() == 4.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(NUMBER_OF_STUDENTS / 2 - NUMBER_OF_STUDENTS / 6 - NUMBER_OF_STUDENTS / 4);
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            // MC and short Answer are correct
            else if (pointCounter.getPoints() == 6.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(NUMBER_OF_STUDENTS / 4);
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            // MC and DnD are correct
            else if (pointCounter.getPoints() == 7.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(NUMBER_OF_STUDENTS / 6);
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            // MC, DnD and short Answer are all correct
            else if (pointCounter.getPoints() == 9.0) {
                assertThat(pointCounter.getRatedCounter()).isEqualTo(NUMBER_OF_STUDENTS / 12);
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
            else {
                assertThat(pointCounter.getRatedCounter()).isZero();
                assertThat(pointCounter.getUnRatedCounter()).isZero();
            }
        }
        // check statistic for each question
        for (var question : quizExerciseWithStatistic.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(NUMBER_OF_STUDENTS / 2);
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(NUMBER_OF_STUDENTS / 3);
            }
            else {
                assertThat(question.getQuizQuestionStatistic().getRatedCorrectCounter()).isEqualTo(NUMBER_OF_STUDENTS / 4);
            }
            assertThat(question.getQuizQuestionStatistic().getUnRatedCorrectCounter()).isZero();
            assertThat(question.getQuizQuestionStatistic().getParticipantsRated()).isEqualTo(NUMBER_OF_STUDENTS);
            assertThat(question.getQuizQuestionStatistic().getParticipantsUnrated()).isZero();
        }
    }
}
