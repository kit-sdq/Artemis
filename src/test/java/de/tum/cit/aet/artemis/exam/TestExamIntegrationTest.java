package de.tum.cit.aet.artemis.exam;

import static java.time.ZonedDateTime.now;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import java.net.URI;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.service.ParticipantScoreScheduleService;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.service.user.PasswordService;
import de.tum.cit.aet.artemis.core.user.util.UserFactory;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class TestExamIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "testexamintegration";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private PasswordService passwordService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    private ExamService examService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course1;

    private Course course2;

    private Exam testExam1;

    private static final int NUMBER_OF_STUDENTS = 1;

    private static final int NUMBER_OF_TUTORS = 1;

    private User student1;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, NUMBER_OF_TUTORS, 0, 1);
        // Add a student that is not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42", passwordService.hashPassword(UserFactory.USER_PASSWORD));

        course1 = courseUtilService.addEmptyCourse();
        course2 = courseUtilService.addEmptyCourse();

        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        testExam1 = examUtilService.addTestExam(course1);
        examUtilService.addStudentExamForTestExam(testExam1, student1);

        ParticipantScoreScheduleService.DEFAULT_WAITING_TIME_FOR_SCHEDULED_TASKS = 200;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateStudentExams_testExam() throws Exception {
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/generate-student-exams", Optional.empty(), StudentExam.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGenerateMissingStudentExams_testExam() throws Exception {
        request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/generate-missing-student-exams", Optional.empty(),
                StudentExam.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testEvaluateQuizExercises_testExam() throws Exception {
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/evaluate-quiz-exercises", Optional.empty(), HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor() throws Exception {
        // Test the creation of a test exam
        Exam examA = ExamFactory.generateTestExam(course1);
        URI examUri = request.post("/api/exam/courses/" + course1.getId() + "/exams", examA, HttpStatus.CREATED);
        Exam savedExam = request.get(String.valueOf(examUri), HttpStatus.OK, Exam.class);

        verify(examAccessService).checkCourseAccessForInstructorElseThrow(course1.getId());
        Channel channelFromDB = channelRepository.findChannelByExamId(savedExam.getId());
        assertThat(channelFromDB).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_withVisibleDateEqualsStartDate() throws Exception {
        // Test the creation of a test exam, where visibleDate equals StartDate
        Exam examB = ExamFactory.generateTestExam(course1);
        examB.setVisibleDate(examB.getStartDate());
        request.post("/api/exam/courses/" + course1.getId() + "/exams", examB, HttpStatus.CREATED);

        verify(examAccessService).checkCourseAccessForInstructorElseThrow(course1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_badRequestWithWorkingTimeGreaterThanWorkingWindow() throws Exception {
        // Test for bad request, where workingTime is greater than difference between StartDate and EndDate
        Exam examC = ExamFactory.generateTestExam(course1);
        examC.setWorkingTime(5000);
        request.post("/api/exam/courses/" + course1.getId() + "/exams", examC, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_badRequestWithWorkingTimeSetToZero() throws Exception {
        // Test for bad request, if the working time is 0
        Exam examD = ExamFactory.generateTestExam(course1);
        examD.setWorkingTime(0);
        request.post("/api/exam/courses/" + course1.getId() + "/exams", examD, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_attendanceCheckTurnedOn() throws Exception {
        // Test for bad request if the attendance check is turned on
        Exam examD = ExamFactory.generateTestExam(course1);
        examD.setExamWithAttendanceCheck(true);
        request.post("/api/exam/courses/" + course1.getId() + "/exams", examD, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_testExam_CorrectionRoundViolation() throws Exception {
        Exam exam = ExamFactory.generateTestExam(course1);
        exam.setNumberOfCorrectionRoundsInExam(1);
        request.post("/api/exam/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestExam_asInstructor_realExam_CorrectionRoundViolation() throws Exception {
        Exam exam = ExamFactory.generateExam(course1);
        exam.setNumberOfCorrectionRoundsInExam(0);
        request.post("/api/exam/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);

        exam.setNumberOfCorrectionRoundsInExam(3);
        request.post("/api/exam/courses/" + course1.getId() + "/exams", exam, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateTestExam_asInstructor_withExamModeChanged() throws Exception {
        // The Exam-Mode should not be changeable with a PUT / update operation, a CONFLICT should be returned instead
        // Case 1: test exam should be updated to real exam
        Exam examA = ExamFactory.generateTestExam(course1);
        Exam createdExamA = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", examA, Exam.class, HttpStatus.CREATED);
        createdExamA.setNumberOfCorrectionRoundsInExam(1);
        createdExamA.setTestExam(false);
        request.putWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", createdExamA, Exam.class, HttpStatus.CONFLICT);

        // Case 2: real exam should be updated to test exam
        Exam examB = ExamFactory.generateTestExam(course1);
        examB.setNumberOfCorrectionRoundsInExam(1);
        examB.setTestExam(false);
        examB.setChannelName("examB");
        Exam createdExamB = request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", examB, Exam.class, HttpStatus.CREATED);
        createdExamB.setTestExam(true);
        createdExamB.setNumberOfCorrectionRoundsInExam(0);
        request.putWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams", createdExamB, Exam.class, HttpStatus.CONFLICT);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteStudentForTestExam_badRequest() throws Exception {
        // Create an exam with registered students
        Exam exam = examUtilService.setupExamWithExerciseGroupsExercisesRegisteredStudents(TEST_PREFIX, course1, 1);
        exam.setTestExam(true);
        examRepository.save(exam);

        // Remove student1 from the exam
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/students/" + TEST_PREFIX + "student1", HttpStatus.BAD_REQUEST);
    }

    // ExamResource - getStudentExamForTestExamForStart
    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetStudentExamForTestExamForStart_notRegisteredInCourse() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/own-student-exam", HttpStatus.FORBIDDEN, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_notVisible() throws Exception {
        testExam1.setVisibleDate(now().plusMinutes(60));
        testExam1 = examRepository.save(testExam1);

        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/own-student-exam", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_tooEarly() throws Exception {
        Exam testExam = examUtilService.addTestExam(course2);
        testExam.setStartDate(now().plusMinutes(10));
        testExam = examRepository.save(testExam);
        // the request fails because the exam start is 10 min in the future
        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + testExam.getId() + "/own-student-exam", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_ExamDoesNotBelongToCourse() throws Exception {
        Exam testExam = examUtilService.addTestExam(course2);
        testExam.setStartDate(now().plusMinutes(4));
        testExam = examRepository.save(testExam);
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam.getId() + "/own-student-exam", HttpStatus.CONFLICT, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_fetchExam_successful() throws Exception {
        var testExam = examUtilService.addTestExam(course2);
        testExam = examRepository.save(testExam);
        var examUser = new ExamUser();
        examUser.setExam(testExam);
        examUser.setUser(student1);
        examUser = examUserRepository.save(examUser);
        testExam.addExamUser(examUser);
        examRepository.save(testExam);
        var studentExam5 = examUtilService.addStudentExamForTestExam(testExam, student1);
        StudentExam studentExamReceived = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + testExam.getId() + "/own-student-exam", HttpStatus.OK,
                StudentExam.class);
        assertThat(studentExamReceived).isEqualTo(studentExam5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_ExamEnded() throws Exception {
        testExam1.setEndDate(ZonedDateTime.now().minusHours(5));
        examRepository.save(testExam1);

        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/own-student-exam", HttpStatus.BAD_REQUEST, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForStart_MultipleUnfinishedAttempts() throws Exception {
        examUtilService.addStudentExamForTestExam(testExam1, student1);
        examUtilService.addStudentExamForTestExam(testExam1, student1);

        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/own-student-exam", HttpStatus.INTERNAL_SERVER_ERROR, StudentExam.class);
    }

    /**
     * Tests that students see their first correction points when the second correction is incomplete.
     * This test prevents Issue #4793
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStudentSeesFirstCorrectionPointsWhenSecondCorrectionIncomplete() throws Exception {
        // Setup test data
        User student = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Exam exam = examUtilService.setupExamWithTwoCorrectionRounds();
        ProgrammingExercise programmingExercise = examUtilService.setupProgrammingExerciseWithSecondCorrection(exam);
        StudentExam studentExam = examUtilService.setupStudentExamWithSubmission(exam, programmingExercise, student);
        participationUtilService.setupSubmissionWithTwoResults(programmingExercise, student);

        StudentExamWithGradeDTO studentGrade = examService.getStudentExamGradesForSummary(student, studentExam, false);
        assertThat(studentGrade).isNotNull();
        assertThat(studentGrade.studentExam().getUser().getId()).isEqualTo(student.getId());
        // Verify the student sees the correct points from the first correction (8.0), not 0
        assertThat(studentGrade.studentResult().overallPointsAchieved()).isEqualTo(8.0);
    }
}
