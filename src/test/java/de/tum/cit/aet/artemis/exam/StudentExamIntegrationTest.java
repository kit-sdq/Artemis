package de.tum.cit.aet.artemis.exam;

import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredFileUploadExercise;
import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredModelingExercise;
import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredProgrammingExercise;
import static de.tum.cit.aet.artemis.core.util.SensitiveInformationUtil.assertSensitiveInformationWasFilteredTextExercise;
import static de.tum.cit.aet.artemis.core.util.TestConstants.COMMIT_HASH_OBJECT_ID;
import static de.tum.cit.aet.artemis.core.util.TestConstants.COMMIT_HASH_STRING;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.lib.ObjectId;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.BonusStrategy;
import de.tum.cit.aet.artemis.assessment.domain.GradeType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.BonusRepository;
import de.tum.cit.aet.artemis.assessment.repository.GradingScaleRepository;
import de.tum.cit.aet.artemis.assessment.util.BonusFactory;
import de.tum.cit.aet.artemis.assessment.util.GradingScaleUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.util.RoundingUtil;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.dto.StudentExamWithGradeDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamAttendanceCheckEventDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamLiveEventBaseDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.ExamWideAnnouncementEventDTO;
import de.tum.cit.aet.artemis.exam.dto.examevent.WorkingTimeUpdateEventDTO;
import de.tum.cit.aet.artemis.exam.repository.ExamSessionRepository;
import de.tum.cit.aet.artemis.exam.repository.ExamUserRepository;
import de.tum.cit.aet.artemis.exam.service.ExamQuizService;
import de.tum.cit.aet.artemis.exam.service.StudentExamService;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.test_repository.StudentExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamFactory;
import de.tum.cit.aet.artemis.exam.util.ExamPrepareExercisesTestUtil;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadSubmission;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.LockRepositoryPolicy;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseTestService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.AnswerOption;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropMapping;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerQuestion;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.domain.ShortAnswerSubmittedText;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.test_repository.QuizSubmissionTestRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationJenkinsLocalVCTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

class StudentExamIntegrationTest extends AbstractSpringIntegrationJenkinsLocalVCTest {

    private static final Logger log = LoggerFactory.getLogger(StudentExamIntegrationTest.class);

    private static final String TEST_PREFIX = "studexam";

    @Autowired
    private ProgrammingExerciseTestService programmingExerciseTestService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ExamUserRepository examUserRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private StudentExamTestRepository studentExamRepository;

    @Autowired
    private ExamSessionRepository examSessionRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepository;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private ExamQuizService examQuizService;

    @Autowired
    private QuizSubmissionTestRepository quizSubmissionTestRepository;

    @Autowired
    private ParticipationDeletionService participationDeletionService;

    @Autowired
    private StudentExamService studentExamService;

    @Autowired
    private GradingScaleRepository gradingScaleRepository;

    @Autowired
    private BonusRepository bonusRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private GradingScaleUtilService gradingScaleUtilService;

    private User student1;

    private Course course1;

    private Course course2;

    private Exam exam1;

    private Exam exam2;

    private Exam testExam1;

    private Exam testExam2;

    private Exam testRunExam;

    private StudentExam studentExam1;

    private StudentExam studentExamForTestExam1;

    private StudentExam studentExamForTestExam2;

    private final List<LocalRepository> studentRepos = new ArrayList<>();

    private static final int NUMBER_OF_STUDENTS = 2;

    private static final boolean IS_TEST_RUN = false;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 0, 2);

        student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        course1 = courseUtilService.addEmptyCourse();
        exam1 = examUtilService.addActiveExamWithRegisteredUser(course1, student2);
        exam1 = examRepository.save(exam1);

        exam2 = examUtilService.addExam(course1);
        exam2 = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, true, false);

        studentExam1 = examUtilService.addStudentExam(exam1);
        studentExam1.setWorkingTime(7200);
        studentExam1.setUser(student1);
        studentExamRepository.save(studentExam1);
        examUtilService.addStudentExam(exam2);

        testExam1 = examUtilService.addActiveTestExamWithRegisteredUserWithoutStudentExam(course1, student1);
        studentExamForTestExam1 = examUtilService.addStudentExamForTestExam(testExam1, student1);

        testExam2 = examUtilService.addTestExamWithRegisteredUser(course1, student1);
        testExam2.setVisibleDate(ZonedDateTime.now().minusHours(3));
        testExam2.setStartDate(ZonedDateTime.now().minusHours(2));
        testExam2.setEndDate(ZonedDateTime.now().minusHours(1));
        testExam2 = examRepository.save(testExam2);
        testExam2 = examUtilService.addTextModelingProgrammingExercisesToExam(testExam2, false, true);
        studentExamForTestExam2 = examUtilService.addStudentExamForTestExam(testExam2, student1);
        studentExamForTestExam2.setSubmitted(true);
        studentExamForTestExam2.setSubmissionDate(ZonedDateTime.now().minusMinutes(65));
        studentExamRepository.save(studentExamForTestExam2);

        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        doReturn(new Repository("ab", new VcsRepositoryUri("uri"))).when(gitService).getOrCheckoutRepositoryIntoTargetDirectory(any(), any(), anyBoolean(), anyBoolean());
        doReturn(new Repository("ab", new VcsRepositoryUri("uri"))).when(gitService).getExistingCheckedOutRepositoryByLocalPath(any(), any(), any(), anyBoolean());
        doReturn(new Repository("ab", new VcsRepositoryUri("uri"))).when(gitService).copyBareRepositoryWithoutHistory(any(), any(), any());
        // TODO: all parts using programmingExerciseTestService should also be provided for LocalVC+Jenkins
        programmingExerciseTestService.setup(this, versionControlService);
        jenkinsRequestMockProvider.enableMockingOfRequests(jenkinsJobPermissionsService);
    }

    @AfterEach
    void tearDown() throws Exception {
        programmingExerciseTestService.tearDown();
        jenkinsRequestMockProvider.reset();

        for (var repo : studentRepos) {
            repo.resetLocalRepo();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindOne() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> studentExamRepository.findByIdElseThrow(Long.MAX_VALUE));
        assertThat(studentExamRepository.findByIdElseThrow(studentExam1.getId())).isEqualTo(studentExam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindOneWithExercisesByUserIdAndExamId() {
        var studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(Long.MAX_VALUE, exam1.getId(), IS_TEST_RUN);
        assertThat(studentExam).isEmpty();
        studentExam = studentExamRepository.findWithExercisesByUserIdAndExamId(student1.getId(), exam1.getId(), IS_TEST_RUN);
        assertThat(studentExam).contains(studentExam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindAllDistinctWorkingTimesByExamId() {
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(Long.MAX_VALUE)).isEqualTo(Set.of());
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam1.getId())).isEqualTo(Set.of(studentExam1.getWorkingTime()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindMaxWorkingTimeById() {
        assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(() -> studentExamRepository.findMaxWorkingTimeByExamIdElseThrow(Long.MAX_VALUE));

        assertThat(studentExamRepository.findMaxWorkingTimeByExamIdElseThrow(exam1.getId())).isEqualTo(studentExam1.getWorkingTime());
    }

    private void deleteExamWithInstructor(Exam exam) throws Exception {
        // change back to instructor user
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        // Clean up to prevent exceptions during reset database
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    private void testAllPreAuthorize() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.FORBIDDEN, StudentExam.class);
        request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExam_asInstructor() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExamsForExam_asInstructor() throws Exception {
        List<StudentExam> studentExams = request.getList("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams", HttpStatus.OK, StudentExam.class);
        assertThat(studentExams).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExamForExam_withProgrammingExerciseWithActiveSubmissionPolicy_asInstructor() throws Exception {

        // set up a programming exercise with a submission policy
        SubmissionPolicy submissionPolicy = new LockRepositoryPolicy();
        submissionPolicy.setSubmissionLimit(5);
        submissionPolicy.setActive(true);
        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(exam2, ProgrammingExercise.class);
        programmingExerciseUtilService.addSubmissionPolicyToExercise(submissionPolicy, programmingExercise);

        StudentExam studentExam = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId(), HttpStatus.OK,
                StudentExam.class);

        // check that the submission policy is included in the response
        for (var exercise : studentExam.getExercises()) {
            if (exercise instanceof ProgrammingExercise) {
                assertThat(((ProgrammingExercise) exercise).getSubmissionPolicy().isActive()).isTrue();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithMultipleTestRuns() throws Exception {
        prepareStudentExamsForConduction(false, true, 1);

        assertThat(studentExamRepository.findByExamId(exam2.getId())).hasSize(1);

        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        exam2 = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(exam2.getId());
        var usersOfExam = exam2.getRegisteredUsers();
        usersOfExam.add(instructor);

        var programmingExercise = ExerciseUtilService.getFirstExerciseWithType(exam2, ProgrammingExercise.class);

        jenkinsRequestMockProvider.reset();

        // the empty commit is not necessary for this test
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getParticipantIdentifier(), Set.of(instructor), true);
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getParticipantIdentifier(), Set.of(instructor), true);
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getParticipantIdentifier(), Set.of(instructor), true);

        // create multiple test runs for the same user (i.e. instructor1), login again because "createTestRun" invokes a server method with changes the authorization
        createTestRun(exam2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        createTestRun(exam2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        createTestRun(exam2);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");

        assertThat(studentExamRepository.findAllTestRunsByExamId(exam2.getId())).hasSize(3);

        jenkinsRequestMockProvider.reset();
        mockDeleteProgrammingExercise(programmingExercise, usersOfExam);

        request.delete("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId(), HttpStatus.OK);

        assertThat(studentExamRepository.findAllTestRunsByExamId(exam2.getId())).isEmpty();
        assertThat(studentExamRepository.findByExamId(exam2.getId())).isEmpty();
    }

    private List<StudentExam> prepareStudentExamsForConduction(boolean early, boolean setFields, int numberOfStudents) throws Exception {
        ZonedDateTime visibleDate;
        ZonedDateTime startDate;
        ZonedDateTime endDate;
        if (early) {
            startDate = ZonedDateTime.now().plusHours(1);
            endDate = ZonedDateTime.now().plusHours(3);
        }
        else {
            // If the exam is prepared only 5 minutes before the release date, the repositories of the students are unlocked as well.
            startDate = ZonedDateTime.now().plusMinutes(6);
            endDate = ZonedDateTime.now().plusMinutes(8);
        }

        visibleDate = ZonedDateTime.now().minusMinutes(15);
        // --> 2 min = 120s working time

        Set<User> registeredStudents = getRegisteredStudents(numberOfStudents);
        var studentExams = programmingExerciseTestService.prepareStudentExamsForConduction(TEST_PREFIX, visibleDate, startDate, endDate, registeredStudents, studentRepos);
        Exam exam = examRepository.findByIdElseThrow(studentExams.getFirst().getExam().getId());
        Course course = exam.getCourse();

        if (!early) {
            // simulate "wait" for exam to start
            exam.setStartDate(ZonedDateTime.now());
            exam.setEndDate(ZonedDateTime.now().plusMinutes(2));
            examRepository.save(exam);
        }

        if (setFields) {
            exam2 = exam;
            course2 = course;
        }
        return studentExams;
    }

    private Set<User> getRegisteredStudents(int numberOfRegisteredStudents) {
        Set<User> registeredStudents = new HashSet<>();
        for (int i = 1; i <= numberOfRegisteredStudents; i++) {
            registeredStudents.add(userUtilService.getUserByLogin(TEST_PREFIX + "student" + i));
        }
        return registeredStudents;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStartExercises_testExam() throws Exception {
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/start-exercises", null, HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetStudentExamForConduction() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        for (var studentExam : studentExams) {
            var user = studentExam.getUser();
            userUtilService.changeUser(user.getLogin());
            final HttpHeaders headers = getHttpHeadersForExamSession();
            var response = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.OK,
                    StudentExam.class, headers);
            assertThat(response).isEqualTo(studentExam);
            assertThat(response.isStarted()).isTrue();
            assertThat(response.getExercises()).hasSize(exam2.getNumberOfExercisesInExam());
            for (Exercise exercise : response.getExercises()) {
                assertThat(exercise.getExerciseGroup()).isNotNull();
                assertThat(exercise.getExerciseGroup().getExercises()).isEmpty();
                assertThat(exercise.getExerciseGroup().getExam()).isNull();
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(((ProgrammingExercise) exercise).getBuildConfig()).isNull();
                }
            }
            assertThat(studentExamRepository.findById(studentExam.getId()).orElseThrow().isStarted()).isTrue();
            assertParticipationAndSubmissions(response, user);
        }

        deleteExamWithInstructor(exam1);
    }

    private static HttpHeaders getHttpHeadersForExamSession() {
        final HttpHeaders headers = new HttpHeaders();
        headers.set("User-Agent", "foo");
        headers.set("X-Artemis-Client-Fingerprint", "bar");
        headers.set("X-Forwarded-For", "10.0.28.1");
        return headers;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_testExam() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        var exam = examUtilService.addExam(course1, examVisibleDate, examStartDate, examEndDate);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);
        exam.setTestExam(true);
        var examUser5 = new ExamUser();
        examUser5.setExam(exam);
        examUser5.setUser(student1);
        examUser5 = examUserRepository.save(examUser5);
        exam.addExamUser(examUser5);
        exam = examRepository.save(exam);

        var programmingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(6).getExercises().iterator().next();
        programmingExerciseTestService.setupRepositoryMocks(programmingExercise);
        var repo = new LocalRepository(defaultBranch);
        repo.configureRepos(localVCRepoPath, "studentRepo", "studentOriginRepo");
        programmingExerciseTestService.setupRepositoryMocksParticipant(programmingExercise, student1.getLogin(), repo);
        mockConnectorRequestsForStartParticipation(programmingExercise, student1.getLogin(), Set.of(student1), true);

        StudentExam studentExamForStart = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/own-student-exam", HttpStatus.OK, StudentExam.class);

        final HttpHeaders headers = getHttpHeadersForExamSession();
        var response = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExamForStart.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class, headers);
        assertThat(response).isEqualTo(studentExamForStart);
        assertThat(studentExamRepository.findById(studentExamForStart.getId()).orElseThrow().isStarted()).isTrue();
        assertParticipationAndSubmissions(response, student1);

        // TODO: test the conduction / submission of the test exams, in particular that the summary includes all submissions

        deleteExamWithInstructor(testExam1);
        repo.resetLocalRepo();
    }

    private void assertParticipationAndSubmissions(StudentExam response, User user) {
        for (var exercise : response.getExercises()) {
            assertThat(exercise.getStudentParticipations()).as(exercise.getClass().getName() + " should have 1 participation").hasSize(1);
            var participation = exercise.getStudentParticipations().iterator().next();
            if (!(exercise instanceof ProgrammingExercise)) {
                assertThat(participation.getSubmissions()).as(exercise.getClass().getName() + " should have 1 submission").hasSize(1);
                var submission = participation.getSubmissions().iterator().next();
                assertThat(participation.getParticipant()).isEqualTo(user);
                assertThat(submission.isSubmitted()).isFalse();
                assertThat(submission.getResults()).as(exercise.getClass().getName() + " should have no results").isNullOrEmpty();
            }
            assertThat(exercise.getGradingCriteria()).isNullOrEmpty();
            assertThat(exercise.getGradingInstructions()).isNullOrEmpty();
        }
        var textExercise = (TextExercise) response.getExercises().getFirst();
        var quizExercise = (QuizExercise) response.getExercises().get(1);

        // Check that sensitive information has been removed
        assertThat(textExercise.getExampleSolution()).isNull();

        assertThat(quizExercise.getQuizQuestions()).hasSize(3);

        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            if (question instanceof MultipleChoiceQuestion) {
                assertThat(((MultipleChoiceQuestion) question).getAnswerOptions()).hasSize(2);
                for (AnswerOption answerOption : ((MultipleChoiceQuestion) question).getAnswerOptions()) {
                    assertThat(answerOption.getExplanation()).isNull();
                    assertThat(answerOption.isIsCorrect()).isNull();
                }
            }
            else if (question instanceof DragAndDropQuestion) {
                assertThat(((DragAndDropQuestion) question).getCorrectMappings()).isEmpty();
            }
            else if (question instanceof ShortAnswerQuestion) {
                assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isEmpty();
            }
        }

        assertThat(response.getExamSessions()).hasSize(1);
        var examSession = response.getExamSessions().iterator().next();
        final var optionalExamSession = examSessionRepository.findById(examSession.getId());
        assertThat(optionalExamSession).isPresent();

        assertThat(examSession.getSessionToken()).isNotNull();
        assertThat(examSession.getUserAgent()).isNull();
        assertThat(examSession.getBrowserFingerprintHash()).isNull();
        assertThat(examSession.getIpAddress()).isNull();
        assertThat(optionalExamSession.get().getUserAgent()).isEqualTo("foo");
        assertThat(optionalExamSession.get().getBrowserFingerprintHash()).isEqualTo("bar");
        assertThat(optionalExamSession.get().getIpAddressAsIpAddress().toNormalizedString()).isEqualTo("10.0.28.1");
    }

    @ParameterizedTest
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    @ValueSource(booleans = { true, false })
    void testGetTestRunForConduction(boolean isTestExam) throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        // --> 2 min = 120s working time

        course2 = courseUtilService.addEmptyCourse();
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);

        exam2.setTestExam(isTestExam);
        exam2 = examRepository.save(exam2);

        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, true, false);
        final var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var programmingExercise = (ProgrammingExercise) exam.getExerciseGroups().get(2).getExercises().iterator().next();
        programmingExerciseTestService.setupRepositoryMocks(programmingExercise);
        var repo = new LocalRepository(defaultBranch);
        repo.configureRepos(localVCRepoPath, "instructorRepo", "instructorOriginRepo");
        programmingExerciseTestService.setupRepositoryMocksParticipant(programmingExercise, instructor.getLogin(), repo);
        mockConnectorRequestsForStartParticipation(programmingExercise, instructor.getLogin(), Set.of(instructor), true);

        assertThat(testRun.isTestRun()).isTrue();

        var response = request.get("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        assertThat(response).isEqualTo(testRun);
        assertThat(response.isStarted()).isTrue();
        assertThat(response.isTestRun()).isTrue();
        assertThat(response.getExercises()).hasSize(exam.getNumberOfExercisesInExam());
        for (Exercise exercise : response.getExercises()) {
            assertThat(exercise.getStudentParticipations()).hasSize(1);
        }
        // Ensure that student exam was marked as started
        assertThat(studentExamRepository.findById(testRun.getId()).orElseThrow().isStarted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindAllTestRunsForExam() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var instructor2 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor2");
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        // --> 2 min = 120s working time

        course2 = courseUtilService.addEmptyCourse();
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, true, false);
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor2, exam.getExerciseGroups());

        List<StudentExam> response = request.getList("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-runs", HttpStatus.OK, StudentExam.class);
        assertThat(response).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course2 = courseUtilService.addEmptyCourse();
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        List<Submission> response = request.getList("/api/exercise/exercises/" + testRun.getExercises().getFirst().getId() + "/test-run-submissions", HttpStatus.OK,
                Submission.class);
        assertThat(response).isNotEmpty();
        assertThat((response.getFirst().getParticipation()).isTestRun()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise_notExamExercise() throws Exception {
        course2 = courseUtilService.addEmptyCourse();
        var exercise = programmingExerciseUtilService.addProgrammingExerciseToCourse(course2, false);
        request.getList("/api/exercise/exercises/" + exercise.getId() + "/test-run-submissions", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise_notInstructor() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        course2 = courseUtilService.addEmptyCourse();
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        userUtilService.changeUser(TEST_PREFIX + "student2");
        request.getList("/api/exercise/exercises/" + testRun.getExercises().getFirst().getId() + "/test-run-submissions", HttpStatus.FORBIDDEN, Submission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetAllTestRunSubmissionsForExercise_noTestRunSubmissions() throws Exception {
        course2 = courseUtilService.addEmptyCourse();
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(4);
        var examEndDate = ZonedDateTime.now().plusMinutes(3);
        exam2 = examUtilService.addExam(course2, examVisibleDate, examStartDate, examEndDate);
        var exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam2, false, false);
        final var latestSubmissions = request.getList(
                "/api/exercise/exercises/" + exam.getExerciseGroups().getFirst().getExercises().iterator().next().getId() + "/test-run-submissions", HttpStatus.OK,
                Submission.class);
        assertThat(latestSubmissions).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetWorkingTimesNoStudentExams() {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);

        // register user
        Set<User> registeredStudents = getRegisteredStudents(NUMBER_OF_STUDENTS);
        for (User student : registeredStudents) {
            var examUser = new ExamUser();
            examUser.setUser(student);
            examUser.setExam(exam);
            examUser = examUserRepository.save(examUser);
            exam.addExamUser(examUser);
        }
        exam.setNumberOfExercisesInExam(2);
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        /*
         * don't generate individual student exams
         */
        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).isEmpty();
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetWorkingTimesDifferentStudentExams() throws Exception {
        var examVisibleDate = ZonedDateTime.now().minusMinutes(5);
        var examStartDate = ZonedDateTime.now().plusMinutes(5);
        var examEndDate = ZonedDateTime.now().plusMinutes(20);

        Course course = courseUtilService.addEmptyCourse();
        Exam exam = examUtilService.addExam(course, examVisibleDate, examStartDate, examEndDate);
        exam = examUtilService.addExerciseGroupsAndExercisesToExam(exam, true);

        // register user
        Set<User> registeredStudents = getRegisteredStudents(NUMBER_OF_STUDENTS);
        for (User student : registeredStudents) {
            var examUser = new ExamUser();
            examUser.setUser(student);
            examUser.setExam(exam);
            examUser = examUserRepository.save(examUser);
            exam.addExamUser(examUser);
        }
        exam.setRandomizeExerciseOrder(false);
        exam = examRepository.save(exam);

        // generate individual student exams
        List<StudentExam> studentExams = request.postListWithResponseBody("/api/exam/courses/" + course.getId() + "/exams/" + exam.getId() + "/generate-student-exams",
                Optional.empty(), StudentExam.class, HttpStatus.OK);

        // Modify working times

        var expectedWorkingTimes = new HashSet<Integer>();
        int maxWorkingTime = (int) Duration.between(examStartDate, examEndDate).getSeconds();

        for (int i = 0; i < studentExams.size(); i++) {
            if (i % 2 == 0) {
                maxWorkingTime += 35;
            }
            expectedWorkingTimes.add(maxWorkingTime);

            var studentExam = studentExams.get(i);
            studentExam.setWorkingTime(maxWorkingTime);
            studentExamRepository.save(studentExam);
        }

        assertThat(studentExamRepository.findMaxWorkingTimeByExamId(exam.getId())).contains(maxWorkingTime);
        assertThat(studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId())).containsExactlyInAnyOrderElementsOf(expectedWorkingTimes);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTime() throws Exception {
        int newWorkingTime = 180 * 60;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        StudentExam result = request.patchWithResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime, StudentExam.class,
                HttpStatus.OK);
        assertThat(result.getWorkingTime()).isEqualTo(newWorkingTime);
        assertThat(studentExamRepository.findById(studentExam1.getId()).orElseThrow().getWorkingTime()).isEqualTo(newWorkingTime);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTimeInvalid() throws Exception {
        int newWorkingTime = 0;
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(5));
        exam1 = examRepository.save(exam1);
        request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time",
                newWorkingTime, StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        var studentExamDB = studentExamRepository.findById(studentExam1.getId()).orElseThrow();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());

        newWorkingTime = -10;
        request.patchWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time",
                newWorkingTime, StudentExam.class, HttpStatus.BAD_REQUEST);
        // working time did not change
        studentExamDB = studentExamRepository.findById(studentExam1.getId()).orElseThrow();
        assertThat(studentExamDB.getWorkingTime()).isEqualTo(studentExam1.getWorkingTime());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testUpdateWorkingTimeLate() throws Exception {
        int newWorkingTime = 180 * 60;
        int oldWorkingTime = studentExam1.getWorkingTime();
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);
        StudentExam result = request.patchWithResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/working-time", newWorkingTime, StudentExam.class,
                HttpStatus.OK);
        assertThat(result.getWorkingTime()).isEqualTo(newWorkingTime);
        assertThat(studentExamRepository.findById(studentExam1.getId()).orElseThrow().getWorkingTime()).isEqualTo(newWorkingTime);

        var capturedEvent = (WorkingTimeUpdateEventDTO) captureExamLiveEventForId(studentExam1.getId(), false);

        assertThat(capturedEvent.newWorkingTime()).isEqualTo(newWorkingTime);
        assertThat(capturedEvent.oldWorkingTime()).isEqualTo(oldWorkingTime);
    }

    private ExamLiveEventBaseDTO captureExamLiveEventForId(Long studentExamOrExamId, boolean examWide) {
        // Create an ArgumentCaptor for the WebSocket message
        ArgumentCaptor<ExamLiveEventBaseDTO> websocketEventCaptor = ArgumentCaptor.forClass(ExamLiveEventBaseDTO.class);

        // Verify that the sendMessage method was called with the expected WebSocket event
        var expectedTopic = examWide ? "/topic/exam-participation/exam/" + studentExamOrExamId + "/events"
                : "/topic/exam-participation/studentExam/" + studentExamOrExamId + "/events";
        verify(websocketMessagingService, timeout(2000)).sendMessage(eq(expectedTopic), websocketEventCaptor.capture());

        // Get the captured WebSocket event
        return websocketEventCaptor.getValue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamAnnouncementSent() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        var result = request.postWithPlainStringResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/announcements", testMessage,
                ExamWideAnnouncementEventDTO.class, HttpStatus.OK);

        assertThat(result.id()).isGreaterThan(0L);
        assertThat(result.text()).isEqualTo(testMessage);
        assertThat(result.createdDate()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

        var event = captureExamLiveEventForId(exam1.getId(), true);
        assertThat(event).isEqualTo(result);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamsCanNotBeSentBeforeVisibleDate() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/announcements", testMessage, ExamWideAnnouncementEventDTO.class,
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamAttendanceCheck() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        var result = request.postWithPlainStringResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + studentExam1.getUser().getLogin() + "/attendance-check", testMessage,
                ExamAttendanceCheckEventDTO.class, HttpStatus.OK);

        assertThat(result.id()).isGreaterThan(0L);
        assertThat(result.text()).isEqualTo(testMessage);
        assertThat(result.createdDate()).isCloseTo(Instant.now(), within(5, ChronoUnit.SECONDS));

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testExamsCanNotBeSentBeforeVisibleDateForAttendance() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().plusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        request.postWithPlainStringResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + studentExam1.getUser().getLogin() + "/attendance-check", testMessage,
                ExamAttendanceCheckEventDTO.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testExamAttendanceCheckForbidden() throws Exception {
        exam1.setVisibleDate(ZonedDateTime.now().minusMinutes(1));
        exam1 = examRepository.save(exam1);

        var testMessage = "Test message";
        request.postWithPlainStringResponseBody(
                "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/students/" + studentExam1.getUser().getLogin() + "/attendance-check", testMessage,
                ExamAttendanceCheckEventDTO.class, HttpStatus.FORBIDDEN);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_alreadySubmitted() throws Exception {
        // Set up an exercise
        exam1 = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, false);
        var exercise = exam1.getExerciseGroups().getFirst().getExercises().iterator().next();
        var participation = ParticipationFactory.generateStudentParticipation(InitializationState.INITIALIZED, exercise, studentExam1.getUser());
        var submission = ParticipationFactory.generateTextSubmission("Test1", Language.ENGLISH, true);
        studentExam1.addExercise(exercise);
        exercise.addParticipation(participation);
        participation.addSubmission(submission);
        studentParticipationRepository.save(participation);
        submissionRepository.save(submission);
        exerciseRepository.save(exercise);
        studentExamRepository.save(studentExam1);

        // Change our submission
        submission.setText("Test2");
        submission.setSubmitted(false);

        // if the submitted exam has the submitted flag set to true, the request should be ignored, but still return OK
        studentExam1.setSubmitted(true);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);

        // Check that submission change is not saved
        assertStudentExam1HasSingleTextSubmissionWithTextAndIsSubmitted("Test1", null);

        // if the submitted exam has the submitted flag set to false, and the studentExam is not yet submitted, the request should be accepted ...
        studentExam1.setSubmitted(false);
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);
        // ... and the exam actually be submitted and the change be saved
        assertStudentExam1HasSingleTextSubmissionWithTextAndIsSubmitted("Test2", true);

        // Change submission again
        submission.setText("Test3");
        submission.setSubmitted(false);

        // Subsequent calls should still return OK, but not persist my new submission change
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);
        assertStudentExam1HasSingleTextSubmissionWithTextAndIsSubmitted("Test2", true);
    }

    private void assertStudentExam1HasSingleTextSubmissionWithTextAndIsSubmitted(String content, Boolean submitted) {
        var fromDB = studentExamRepository.findWithExercisesParticipationsSubmissionsById(studentExam1.getId(), false).orElseThrow();
        assertThat(fromDB.isSubmitted()).isEqualTo(submitted);
        assertThat(fromDB.getExercises().getFirst().getStudentParticipations().size()).isEqualTo(1);
        assertThat(fromDB.getExercises().getFirst().getStudentParticipations().iterator().next().getSubmissions().size()).isEqualTo(1);
        assertThat(((TextSubmission) fromDB.getExercises().getFirst().getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow()).getText())
                .isEqualTo(content);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_notInTime() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // Forbidden because user tried to submit before start
        exam1.setStartDate(ZonedDateTime.now().plusHours(1));
        examRepository.save(exam1);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);
        // Forbidden because user tried to submit after end
        exam1.setStartDate(ZonedDateTime.now().minusHours(5));
        examRepository.save(exam1);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_differentUser() throws Exception {
        studentExam1.setSubmitted(false);
        studentExamRepository.save(studentExam1);
        // Forbidden because user object is wrong
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        studentExam1.setUser(student2);
        request.post("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.FORBIDDEN);

        studentExam1 = studentExamRepository.findByIdElseThrow(studentExam1.getId());
        assertThat(studentExam1.getUser()).isEqualTo(student1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam() throws Exception {
        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExam1, HttpStatus.OK, null);
        StudentExam submittedStudentExam = studentExamRepository.findById(studentExam1.getId()).orElseThrow();
        // Ensure that student exam has been marked as submitted
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        // Ensure that student exam has been set
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSubmitStudentExam_testExamTriggersBuilds() throws Exception {
        ExamFactory.generateExerciseGroup(true, testExam1);
        testExam1 = examRepository.save(testExam1);
        ProgrammingExercise programmingExercise = programmingExerciseUtilService.addProgrammingExerciseToExam(testExam1, 0);

        studentExamForTestExam1.addExercise(programmingExercise);
        studentExamForTestExam1 = studentExamRepository.save(studentExamForTestExam1);
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "student1");
        ProgrammingSubmission submission = new ProgrammingSubmission();
        programmingExerciseUtilService.addProgrammingSubmission(programmingExercise, submission, TEST_PREFIX + "student1");

        request.postWithoutLocation("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/submit", studentExamForTestExam1, HttpStatus.OK, null);

        StudentExam submittedStudentExam = studentExamRepository.findById(studentExamForTestExam1.getId()).orElseThrow();
        assertThat(submittedStudentExam.isSubmitted()).isTrue();

        verify(programmingTriggerService, timeout(4000)).triggerBuildForParticipations(List.of(participation));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitExamOtherUser_forbidden() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        // make sure the exam is generally accessible
        exam2.setStartDate(ZonedDateTime.now().plusMinutes(4));
        exam2 = examRepository.save(exam2);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        studentExamResponse.setExercises(null);
        // use a different user
        userUtilService.changeUser(TEST_PREFIX + "student2");
        request.post("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.FORBIDDEN);
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testgetExamTooEarly_forbidden() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(true, true, 1).getFirst();

        userUtilService.changeUser(TEST_PREFIX + "student1");

        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExams() throws Exception {
        prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(8));
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Set<StudentExam> unsubmittedStudentExams = studentExamRepository.findAllUnsubmittedWithExercisesByExamId(exam2.getId());
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(unsubmittedStudentExams);
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    var result = studentParticipation.findLatestSubmission().get().getLatestResult();
                    assertThat(result).isNotNull();
                    assertThat(result.getScore()).isZero();
                    assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                    result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                    assertThat(result.getFeedbacks()).isNotEmpty();
                    assertThat(result.getFeedbacks().getFirst().getDetailText()).isEqualTo("You did not submit your exam");
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExamsForMultipleCorrectionRounds() throws Exception {
        prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);
        exam2.setNumberOfCorrectionRoundsInExam(2);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(8));
        exam2.setWorkingTime(2 * 60);
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Set<StudentExam> unsubmittedStudentExams = studentExamRepository.findAllUnsubmittedWithExercisesByExamId(exam2.getId());
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(unsubmittedStudentExams);
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    assertThat(studentParticipation.findLatestSubmission().get().getResults()).isNotNull().hasSize(exam2.getNumberOfCorrectionRoundsInExam());
                    for (var result : Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults())) {
                        assertThat(result).isNotNull();
                        assertThat(result.getScore()).isZero();
                        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                        result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                        assertThat(result.getFeedbacks()).isNotEmpty();
                        assertThat(result.getFeedbacks().getFirst().getDetailText()).isEqualTo("You did not submit your exam");
                    }
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessEmptyExamSubmissions() throws Exception {
        final var studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        // submit student exam with empty submissions
        for (final var studentExam : studentExams) {
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.save(studentExam);
        }
        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(7));
        exam2.setWorkingTime(3 * 60);
        examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(new HashSet<>(studentExams));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    var result = studentParticipation.findLatestSubmission().get().getLatestResult();
                    assertThat(result).isNotNull();
                    assertThat(result.getScore()).isZero();
                    assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                    result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                    assertThat(result.getFeedbacks()).isNotEmpty();
                    assertThat(result.getFeedbacks().getFirst().getDetailText()).isEqualTo("Empty submission");
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessEmptyExamSubmissionsForMultipleCorrectionRounds() throws Exception {
        final var studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        // submit student exam with empty submissions
        for (final var studentExam : studentExams) {
            studentExam.setSubmitted(true);
            studentExam.setSubmissionDate(ZonedDateTime.now());
            studentExamRepository.save(studentExam);
        }
        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(7));
        exam2.setNumberOfCorrectionRoundsInExam(2);
        examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams",
                Optional.empty(), HttpStatus.OK, null);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(new HashSet<>(studentExams));
        for (final var user : exercisesOfUser.keySet()) {
            final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionsResultIgnoreTestRuns(user.getId(),
                    exercisesOfUser.get(user));
            for (final var studentParticipation : studentParticipations) {
                if (studentParticipation.findLatestSubmission().isPresent()) {
                    assertThat(studentParticipation.findLatestSubmission().get().getResults()).isNotNull().hasSize(exam2.getNumberOfCorrectionRoundsInExam());
                    for (var result : Objects.requireNonNull(studentParticipation.findLatestSubmission().get().getResults())) {
                        assertThat(result).isNotNull();
                        assertThat(result.getScore()).isZero();
                        assertThat(result.getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                        result = resultRepository.findByIdWithEagerFeedbacks(result.getId()).orElseThrow();
                        assertThat(result.getFeedbacks()).isNotEmpty();
                        assertThat(result.getFeedbacks().getFirst().getDetailText()).isEqualTo("Empty submission");
                    }
                }
                else {
                    fail("StudentParticipation which is part of an unsubmitted StudentExam contains no submission or result after automatic assessment of unsubmitted student exams call.");
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExams_forbidden() throws Exception {
        prepareStudentExamsForConduction(false, true, 1);
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", null,
                HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessUnsubmittedStudentExams_badRequest() throws Exception {
        prepareStudentExamsForConduction(false, true, 1);
        exam2 = examRepository.save(exam2);

        request.postWithoutLocation("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/assess-unsubmitted-and-empty-student-exams", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testAssessExamWithSubmissionResult() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        // this test should be after the end date of the exam
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        examRepository.save(exam2);

        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            final var submission = createSubmission(exercise);
            if (submission != null) {
                submission.addResult(new Result());
                Set<Submission> submissions = new HashSet<>();
                submissions.add(submission);
                participation.setSubmissions(submissions);
            }
        }

        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.OK);

        // check that the result was not injected and that the student exam was still submitted correctly

        var studentExamDatabase = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        assertThat(studentExamDatabase.isSubmitted()).isTrue();
        assertThat(studentExamDatabase.getSubmissionDate()).isNotNull();
        for (var exercise : studentExamDatabase.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            var iterator = participation.getSubmissions().iterator();
            if (iterator.hasNext()) {
                var submission = iterator.next();
                assertThat(submission.getLatestResult()).isNull();
            }
        }
        deleteExamWithInstructor(exam1);
    }

    private static Submission createSubmission(Exercise exercise) {
        return switch (exercise) {
            case ProgrammingExercise ignored -> new ProgrammingSubmission();
            case TextExercise ignored -> new TextSubmission();
            case ModelingExercise ignored -> new ModelingSubmission();
            case QuizExercise ignored -> new QuizSubmission();
            case null, default -> null;
        };
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitStudentExam_early() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);
        final List<ProgrammingExercise> exercisesToBeLocked = new ArrayList<>();
        final List<ProgrammingExerciseStudentParticipation> studentProgrammingParticipations = new ArrayList<>();

        for (var exercise : studentExamResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                studentProgrammingParticipations.add((ProgrammingExerciseStudentParticipation) participation);
                exercisesToBeLocked.add(programmingExercise);
            }
        }

        // submit early
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamResponse, HttpStatus.OK);
        var submittedStudentExam = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamResponse.getId() + "/summary",
                HttpStatus.OK, StudentExam.class);
        assertThat(submittedStudentExam.isSubmitted()).isTrue();
        assertThat(submittedStudentExam.getSubmissionDate()).isNotNull();

        // assert that all repositories of programming exercises have been locked
        assertThat(exercisesToBeLocked).hasSameSizeAs(studentProgrammingParticipations);
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitStudentExam_realistic() throws Exception {
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        List<StudentExam> studentExamsAfterStart = new ArrayList<>();
        for (var studentExam : studentExams) {
            userUtilService.changeUser(studentExam.getUser().getLogin());
            var studentExamResponse = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                    HttpStatus.OK, StudentExam.class);

            for (var exercise : studentExamResponse.getExercises()) {
                saveSubmissionByExerciseType(exercise);
            }

            studentExamsAfterStart.add(studentExamResponse);
        }

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        jenkinsRequestMockProvider.reset();

        final String newCommitHash = "2ec6050142b9c187909abede819c083c8745c19b";
        final ObjectId newCommitHashObjectId = ObjectId.fromString(newCommitHash);

        for (var studentExam : studentExamsAfterStart) {
            for (var exercise : studentExam.getExercises()) {
                var participation = exercise.getStudentParticipations().iterator().next();
                if (exercise instanceof ProgrammingExercise programmingExercise) {
                    // do another programming submission to check if the StudentExam after submit contains the new commit hash
                    doReturn(newCommitHashObjectId).when(gitService).getLastCommitHash(any());
                    jenkinsRequestMockProvider.reset();
                    jenkinsRequestMockProvider.mockTriggerBuild(programmingExercise.getProjectKey(), ((ProgrammingExerciseStudentParticipation) participation).getBuildPlanId(),
                            false);
                    userUtilService.changeUser(studentExam.getUser().getLogin());
                    request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                    // do not add programming submission to participation, because we want to simulate, that the latest submission is not present
                }
            }
        }

        List<StudentExam> studentExamsAfterFinish = new ArrayList<>();
        for (var studentExamAfterStart : studentExamsAfterStart) {
            userUtilService.changeUser(studentExamAfterStart.getUser().getLogin());
            request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamAfterStart, HttpStatus.OK);
            var studentExamFinished = request.get(
                    "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamAfterStart.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            // Check that all text/quiz/modeling submissions were saved and that submitted versions were created
            for (var exercise : studentExamFinished.getExercises()) {
                var participationAfterFinish = exercise.getStudentParticipations().iterator().next();
                var submissionAfterFinish = participationAfterFinish.getSubmissions().iterator().next();

                var exerciseAfterStart = studentExamAfterStart.getExercises().stream().filter(exAfterStart -> exAfterStart.getId().equals(exercise.getId())).findFirst()
                        .orElseThrow();
                var participationAfterStart = exerciseAfterStart.getStudentParticipations().iterator().next();
                var submissionAfterStart = participationAfterStart.getSubmissions().iterator().next();

                switch (exercise) {
                    case ModelingExercise ignored -> {
                        var modelingSubmissionAfterFinish = (ModelingSubmission) submissionAfterFinish;
                        var modelingSubmissionAfterStart = (ModelingSubmission) submissionAfterStart;
                        assertThat(modelingSubmissionAfterFinish).isEqualTo(modelingSubmissionAfterStart);
                        assertVersionedSubmission(modelingSubmissionAfterStart);
                        assertVersionedSubmission(modelingSubmissionAfterFinish);
                    }
                    case TextExercise ignored -> {
                        var textSubmissionAfterFinish = (TextSubmission) submissionAfterFinish;
                        var textSubmissionAfterStart = (TextSubmission) submissionAfterStart;
                        assertThat(textSubmissionAfterFinish).isEqualTo(textSubmissionAfterStart);
                        assertVersionedSubmission(textSubmissionAfterStart);
                        assertVersionedSubmission(textSubmissionAfterFinish);
                    }
                    case QuizExercise ignored -> {
                        var quizSubmissionAfterFinish = (QuizSubmission) submissionAfterFinish;
                        var quizSubmissionAfterStart = (QuizSubmission) submissionAfterStart;
                        assertThat(quizSubmissionAfterFinish).isEqualTo(quizSubmissionAfterStart);
                        assertVersionedSubmission(quizSubmissionAfterStart);
                        assertVersionedSubmission(quizSubmissionAfterFinish);
                    }
                    case ProgrammingExercise ignored -> {
                        var programmingSubmissionAfterStart = (ProgrammingSubmission) submissionAfterStart;
                        var programmingSubmissionAfterFinish = (ProgrammingSubmission) submissionAfterFinish;
                        // assert that we did not update the submission prematurely
                        assertThat(programmingSubmissionAfterStart.getCommitHash()).isEqualTo(COMMIT_HASH_STRING);
                        // assert that we get the correct commit hash after submit
                        assertThat(programmingSubmissionAfterFinish.getCommitHash()).isEqualTo(newCommitHash);
                    }
                    default -> {
                    }
                }

            }

            studentExamsAfterFinish.add(studentExamFinished);

            assertThat(studentExamFinished.isSubmitted()).isTrue();
            assertThat(studentExamFinished.getSubmissionDate()).isNotNull();
        }
        assertThat(studentExamsAfterFinish).hasSize(studentExamsAfterStart.size());

        deleteExamWithInstructor(exam1);
    }

    private void saveSubmissionByExerciseType(Exercise exercise) throws Exception {
        var participation = exercise.getStudentParticipations().iterator().next();
        if (exercise instanceof ProgrammingExercise programmingExercise) {
            doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
            jenkinsRequestMockProvider.reset();
            jenkinsRequestMockProvider.mockTriggerBuild(programmingExercise.getProjectKey(), ((ProgrammingExerciseStudentParticipation) participation).getBuildPlanId(), false);
            request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
            Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participation.getId());
            assertThat(programmingSubmission).isPresent();
            assertSensitiveInformationWasFilteredProgrammingExercise(programmingExercise);
            participation.getSubmissions().add(programmingSubmission.get());
            return;
        }
        var submission = participation.getSubmissions().iterator().next();
        switch (exercise) {
            case ModelingExercise modelingExercise -> {
                // check that the submission was saved and that a submitted version was created
                String newModel = "This is a new model";
                String newExplanation = "This is an explanation";
                var modelingSubmission = (ModelingSubmission) submission;
                modelingSubmission.setModel(newModel);
                modelingSubmission.setExplanationText(newExplanation);
                request.put("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
                var savedModelingSubmission = request.get(
                        "/api/modeling/participations/" + exercise.getStudentParticipations().iterator().next().getId() + "/latest-modeling-submission", HttpStatus.OK,
                        ModelingSubmission.class);
                // check that the submission was saved
                assertThat(newModel).isEqualTo(savedModelingSubmission.getModel());
                assertSensitiveInformationWasFilteredModelingExercise(modelingExercise);
                // check that a submitted version was created
                assertVersionedSubmission(modelingSubmission);
            }
            case TextExercise textExercise -> {
                var textSubmission = (TextSubmission) submission;
                final var newText = "New Text";
                textSubmission.setText(newText);
                request.put("/api/text/exercises/" + exercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
                var savedTextSubmission = (TextSubmission) submissionRepository.findById(textSubmission.getId()).orElseThrow();
                // check that the submission was saved
                assertThat(newText).isEqualTo(savedTextSubmission.getText());
                // check that a submitted version was created
                assertVersionedSubmission(textSubmission);
                assertSensitiveInformationWasFilteredTextExercise(textExercise);
            }
            case QuizExercise quizExercise -> {
                assertThat(quizExercise.getQuizQuestions()).hasSize(3);
                quizExercise.getQuizQuestions().forEach(quizQuestion -> {
                    assertThat(quizQuestion.getQuizQuestionStatistic()).isNull();
                    assertThat(quizQuestion.getExplanation()).isNull();
                    switch (quizQuestion) {
                        case MultipleChoiceQuestion mcQuestion -> mcQuestion.getAnswerOptions().forEach(answerOption -> {
                            assertThat(answerOption.getExplanation()).isNull();
                            assertThat(answerOption.isIsCorrect()).isNull();
                        });
                        case DragAndDropQuestion dndQuestion -> assertThat(dndQuestion.getCorrectMappings()).isNullOrEmpty();
                        case ShortAnswerQuestion saQuestion -> assertThat(saQuestion.getCorrectMappings()).isNullOrEmpty();
                        default -> {
                        }
                    }
                });

                submitQuizInExam(quizExercise, (QuizSubmission) submission);
            }
            case FileUploadExercise fileUploadExercise -> assertSensitiveInformationWasFilteredFileUploadExercise(fileUploadExercise);
            default -> {
            }
        }
    }

    private void submitQuizInExam(QuizExercise quizExercise, QuizSubmission quizSubmission) throws Exception {
        // check that the submission was saved and that a submitted version was created
        int dndDragItemIndex = 1;
        int dndLocationIndex = 2;
        String shortAnswerText = "New Short Answer Text";
        int saSpotIndex = 1;
        int mcSelectedOptionIndex = 0;
        quizExercise.getQuizQuestions().forEach(quizQuestion -> {
            if (quizQuestion instanceof DragAndDropQuestion dragAndDropQuestion) {
                var submittedAnswer = new DragAndDropSubmittedAnswer();
                DragAndDropMapping dndMapping = new DragAndDropMapping();
                dndMapping.setDragItemIndex(dndDragItemIndex);
                dndMapping.setDragItem(dragAndDropQuestion.getDragItems().get(dndDragItemIndex));
                dndMapping.setDropLocationIndex(dndLocationIndex);
                dndMapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(dndLocationIndex));
                submittedAnswer.getMappings().add(dndMapping);
                submittedAnswer.setQuizQuestion(dragAndDropQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
            else if (quizQuestion instanceof ShortAnswerQuestion shortAnswerQuestion) {
                var submittedAnswer = new ShortAnswerSubmittedAnswer();
                ShortAnswerSubmittedText shortAnswerSubmittedText = new ShortAnswerSubmittedText();
                shortAnswerSubmittedText.setText(shortAnswerText);
                shortAnswerSubmittedText.setSpot(shortAnswerQuestion.getSpots().get(saSpotIndex));
                submittedAnswer.getSubmittedTexts().add(shortAnswerSubmittedText);
                submittedAnswer.setQuizQuestion(shortAnswerQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
            else if (quizQuestion instanceof MultipleChoiceQuestion multipleChoiceQuestion) {
                var answerOptions = multipleChoiceQuestion.getAnswerOptions();
                var submittedAnswer = new MultipleChoiceSubmittedAnswer();
                submittedAnswer.addSelectedOptions(answerOptions.get(mcSelectedOptionIndex));
                submittedAnswer.setQuizQuestion(quizQuestion);
                quizSubmission.getSubmittedAnswers().add(submittedAnswer);
            }
        });
        QuizSubmission savedQuizSubmission = request.putWithResponseBody("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, QuizSubmission.class,
                HttpStatus.OK);
        // check the submission
        assertThat(savedQuizSubmission.getSubmittedAnswers()).isNotNull().isNotEmpty();
        quizExercise.getQuizQuestions().forEach(quizQuestion -> {
            SubmittedAnswer submittedAnswer = savedQuizSubmission.getSubmittedAnswerForQuestion(quizQuestion);
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer answer) {
                assertThat(answer.getSelectedOptions()).isNotNull().isNotEmpty();
                assertThat(answer.getSelectedOptions().iterator().next()).isNotNull();
                assertThat(answer.getSelectedOptions().iterator().next()).isEqualTo(((MultipleChoiceQuestion) quizQuestion).getAnswerOptions().get(mcSelectedOptionIndex));
            }
            else if (submittedAnswer instanceof ShortAnswerSubmittedAnswer answer) {
                assertThat(answer.getSubmittedTexts()).isNotNull().isNotEmpty();
                assertThat(answer.getSubmittedTexts().iterator().next()).isNotNull();
                assertThat(answer.getSubmittedTexts().iterator().next().getText()).isEqualTo(shortAnswerText);
                assertThat(answer.getSubmittedTexts().iterator().next().getSpot()).isEqualTo(((ShortAnswerQuestion) quizQuestion).getSpots().get(saSpotIndex));
            }
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer answer) {
                assertThat(answer.getMappings()).isNotNull().isNotEmpty();
                assertThat(answer.getMappings().iterator().next()).isNotNull();
                assertThat(answer.getMappings().iterator().next().getDragItem()).isEqualTo(((DragAndDropQuestion) quizQuestion).getDragItems().get(dndDragItemIndex));
                assertThat(answer.getMappings().iterator().next().getDropLocation()).isEqualTo(((DragAndDropQuestion) quizQuestion).getDropLocations().get(dndLocationIndex));
            }
        });
        assertVersionedSubmission(quizSubmission);
    }

    private void assertVersionedSubmission(Submission submission) {
        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());
        var versionedSubmission = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(versionedSubmission).isPresent();
        switch (submission) {
            case TextSubmission textSubmission -> assertThat(textSubmission.getText()).isEqualTo(versionedSubmission.get().getContent());
            case ModelingSubmission modelingSubmission -> assertThat("Model: " + modelingSubmission.getModel() + "; Explanation: " + modelingSubmission.getExplanationText())
                    .isEqualTo(versionedSubmission.get().getContent());
            case FileUploadSubmission fileUploadSubmission -> assertThat(fileUploadSubmission.getFilePath()).isEqualTo(versionedSubmission.get().getContent());
            default -> {
                assertThat(submission).isInstanceOf(QuizSubmission.class);

                /*
                 * When comparing the JSON of the submitted answers to the versioned submission,
                 * a direct string comparison may not always be accurate due to the following reasons:
                 * 1. The order of the submitted answers can change since they are stored as sets.
                 * 2. Data fetched from the server might occasionally contain IDs, while the data returned directly might not.
                 * To account for these discrepancies, we perform a non-strict (= order-agnostic) deep JSON comparison after removing any IDs.
                 * This ensures that the content is accurately matched, irrespective of the order or the presence of IDs.
                 */
                try {
                    var submittedAnswersAsJson = removeIdFieldsFromJSONString(objectMapper.writeValueAsString(((QuizSubmission) submission).getSubmittedAnswers()));
                    var versionedSubmissionAsJson = removeIdFieldsFromJSONString(versionedSubmission.get().getContent());
                    JSONAssert.assertEquals(versionedSubmissionAsJson, submittedAnswersAsJson, false);
                }
                catch (JsonProcessingException | JSONException e) {
                    fail("Exception thrown while serializing submitted answers", e);
                }
                assertThat(submission).isEqualTo(versionedSubmission.get().getSubmission());
            }
        }
    }

    /**
     * Removes the id fields from the JSON string, so that the comparison between the submission and the versioned submission is easier.
     *
     * @param jsonString the JSON string to remove the id fields from
     * @return the JSON string without the id fields
     */
    private String removeIdFieldsFromJSONString(String jsonString) {
        return jsonString.replaceAll(" +\"id\"\\s*:\\s*[0-9]+,\n", "");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStudentExamSummaryAsStudentBeforePublishResults_doFilter() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();
        StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin(), studentExam);

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(1));
        exam2 = examRepository.save(exam2);

        // submitExam
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions, HttpStatus.OK);
        var studentExamFinished = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamWithSubmissions.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);

        // Add results to all exercise submissions
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            participation.setExercise(exercise);
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            participationUtilService.addResultToSubmission(participation, latestSubmission.orElseThrow());
        }
        // evaluate quizzes
        request.postWithoutLocation("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());

        // user tries to access exam summary
        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamSummary = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        // check that all relevant information is visible to the student
        for (final var exercise : studentExamSummary.getExercises()) {
            assertThat(participationUtilService.getResultsForParticipation(exercise.getStudentParticipations().iterator().next())).isEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getGradingCriteria()).isEmpty();

            if (exercise instanceof QuizExercise quizExercise) {
                assertThat(quizExercise.getQuizQuestions()).hasSize(3);
                QuizSubmission submission = (QuizSubmission) exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next();
                assertThat(submission.getScoreInPoints()).isNull();
                submission.getSubmittedAnswers().forEach(submittedAnswer -> {
                    assertThat(submittedAnswer.getScoreInPoints()).isNull();
                    QuizQuestion question = submittedAnswer.getQuizQuestion();
                    if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                        ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNull());
                    }
                    if (question != null) {
                        assertThat(question.getExplanation()).isNull();
                        assertThat(question.getQuizQuestionStatistic()).isNull();
                        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                            ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNull());
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isEmpty();
                        }
                        if (question instanceof DragAndDropQuestion) {
                            assertThat(((DragAndDropQuestion) question).getCorrectMappings()).isEmpty();
                        }
                        if (question instanceof ShortAnswerQuestion) {
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isEmpty();
                        }
                        if (question instanceof MultipleChoiceQuestion) {
                            ((MultipleChoiceQuestion) question).getAnswerOptions().forEach(answerOption -> {
                                assertThat(answerOption.isIsCorrect()).isNull();
                                assertThat(answerOption.getExplanation()).isNull();
                            });
                        }
                    }
                });
            }
            else {
                var participation = exercise.getStudentParticipations().iterator().next();
                assertThat(participationUtilService.getResultsForParticipation(participation)).isEmpty();
                assertThat(participation.getSubmissions().iterator().next().getResults()).isEmpty();
            }
        }
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testStudentExamSummaryAsStudentAfterPublishResults_dontFilter() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamSummary = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        // check that all relevant information is visible to the student
        for (final var exercise : studentExamSummary.getExercises()) {
            assertThat(participationUtilService.getResultsForParticipation(exercise.getStudentParticipations().iterator().next())).isNotEmpty();
            assertThat(exercise.getGradingInstructions()).isNull();
            assertThat(exercise.getGradingCriteria()).isEmpty();

            if (exercise instanceof QuizExercise quizExercise) {
                assertThat(quizExercise.getQuizQuestions()).hasSize(3);
                QuizSubmission submission = (QuizSubmission) exercise.getStudentParticipations().iterator().next().getSubmissions().iterator().next();
                assertThat(submission.getScoreInPoints()).isNotNull();
                submission.getSubmittedAnswers().forEach(submittedAnswer -> {
                    assertThat(submittedAnswer.getScoreInPoints()).isNotNull();
                    if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                        ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNotNull());
                    }
                    QuizQuestion question = submittedAnswer.getQuizQuestion();
                    if (question != null) {
                        assertThat(question.getExplanation()).isNotNull();
                        assertThat(question.getQuizQuestionStatistic()).isNull();
                        if (submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                            ((ShortAnswerSubmittedAnswer) submittedAnswer).getSubmittedTexts().forEach(submittedText -> assertThat(submittedText.isIsCorrect()).isNotNull());
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isNotEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isNotEmpty();
                        }
                        if (question instanceof DragAndDropQuestion) {
                            assertThat(((DragAndDropQuestion) question).getCorrectMappings()).isNotEmpty();
                        }
                        if (question instanceof ShortAnswerQuestion) {
                            assertThat(((ShortAnswerQuestion) question).getCorrectMappings()).isNotEmpty();
                            assertThat(((ShortAnswerQuestion) question).getSolutions()).isNotEmpty();
                        }
                        if (question instanceof MultipleChoiceQuestion) {
                            ((MultipleChoiceQuestion) question).getAnswerOptions().forEach(answerOption -> {
                                assertThat(answerOption.isIsCorrect()).isNotNull();
                                assertThat(answerOption.getExplanation()).isNotNull();
                            });
                        }
                    }
                });
            }
            else {
                var participation = exercise.getStudentParticipations().iterator().next();
                Set<Result> results = participationUtilService.getResultsForParticipation(participation);
                assertThat(results).hasSize(1);
                var result = results.iterator().next();
                assertThat(result.getAssessor()).as("no sensitive inforation get leaked").isNull();
            }
        }
        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithoutGradingScaleAsStudentAfterPublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallScoreAchieved()).isEqualTo(100.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isFalse();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchievedInFirstCorrection()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGradeInFirstCorrection()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentExam()).isEqualTo(studentExam);

        var studentExamFromServer = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        for (final var exercise : studentExamFromServer.getExercises()) {
            if (exercise instanceof QuizExercise) {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(4.0);
            }
            else {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(5.0);
            }
        }
        deleteExamWithInstructor(exam1);
    }

    @NotNull
    private StudentExam createStudentExamWithResultsAndAssessments(boolean setFields, int numberOfStudents) throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, setFields, numberOfStudents).getFirst();
        var exam = examRepository.findById(studentExam.getExam().getId()).orElseThrow();
        StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam, studentExam.getUser().getLogin(), studentExam);

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam = examRepository.save(exam);

        // submitExam
        request.postWithoutResponseBody("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/submit", studentExamWithSubmissions,
                HttpStatus.OK);
        var studentExamFinished = request.get(
                "/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExamWithSubmissions.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);

        exam.setEndDate(ZonedDateTime.now());
        exam = examRepository.save(exam);

        // Add results to all exercise submissions
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            participation.setExercise(exercise);
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            participationUtilService.addResultToSubmission(participation, latestSubmission.orElseThrow());
        }
        exam.setPublishResultsDate(ZonedDateTime.now());
        exam = examRepository.save(exam);

        // evaluate quizzes
        request.postWithoutLocation("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());
        return studentExam;
    }

    private GradingScale createGradeScale(boolean isBonus) {
        GradingScale gradingScale;
        if (isBonus) {
            gradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 40, 50 }, Optional.of(new String[] { "0", "0.3", "0.6" }), true, 1);
            gradingScale.setGradeType(GradeType.BONUS);
        }
        else {
            gradingScale = gradingScaleUtilService.generateGradingScaleWithStickyStep(new double[] { 60, 25, 15, 50 }, Optional.of(new String[] { "5.0", "3.0", "1.0", "1.0" }),
                    true, 1);
        }
        gradingScaleRepository.save(gradingScale);
        return gradingScale;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentAfterPublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallScoreAchieved()).isEqualTo(100.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("1.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchievedInFirstCorrection()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGradeInFirstCorrection()).isEqualTo("5.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentExam()).isEqualTo(studentExam);

        var studentExamFromServer = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                HttpStatus.OK, StudentExam.class);

        for (final var exercise : studentExamFromServer.getExercises()) {
            if (exercise instanceof QuizExercise) {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(4.0);
            }
            else {
                assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().get(exercise.getId())).isEqualTo(5.0);
            }
        }
        deleteExamWithInstructor(exam1);
    }

    private StudentExam addExamExerciseSubmissionsForUser(Exam exam, String userLogin, StudentExam studentExam) throws Exception {
        if (userLogin != null) {
            userUtilService.changeUser(userLogin);
        }
        // start exam conduction for a user
        var studentExamFromServer = request.get(
                "/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        for (var exercise : studentExamFromServer.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            if (exercise instanceof ProgrammingExercise programmingExercise) {
                doReturn(COMMIT_HASH_OBJECT_ID).when(gitService).getLastCommitHash(any());
                jenkinsRequestMockProvider.reset();
                jenkinsRequestMockProvider.mockTriggerBuild(programmingExercise.getProjectKey(), ((ProgrammingExerciseStudentParticipation) participation).getBuildPlanId(), false);
                request.postWithoutLocation("/api/programming/programming-submissions/" + participation.getId() + "/trigger-build", null, HttpStatus.OK, new HttpHeaders());
                Optional<ProgrammingSubmission> programmingSubmission = programmingSubmissionRepository.findFirstByParticipationIdOrderBySubmissionDateDesc(participation.getId());
                programmingSubmission.ifPresent(submission -> participation.getSubmissions().add(submission));
                continue;
            }
            var submission = participation.getSubmissions().iterator().next();
            switch (exercise) {
                case ModelingExercise ignored -> {
                    // check that the submission was saved and that a submitted version was created
                    String newModel = "This is a new model";
                    var modelingSubmission = (ModelingSubmission) submission;
                    modelingSubmission.setModel(newModel);
                    request.put("/api/modeling/exercises/" + exercise.getId() + "/modeling-submissions", modelingSubmission, HttpStatus.OK);
                }
                case TextExercise ignored -> {
                    var textSubmission = (TextSubmission) submission;
                    final var newText = "New Text";
                    textSubmission.setText(newText);
                    request.put("/api/text/exercises/" + exercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
                }
                case QuizExercise quizExercise -> submitQuizInExam(quizExercise, (QuizSubmission) submission);
                default -> {
                }
            }
        }
        return studentExamFromServer;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentBeforePublishResults() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        exam2.setPublishResultsDate(ZonedDateTime.now().plusDays(1));
        exam2 = examRepository.save(exam2);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.FORBIDDEN,
                StudentExamWithGradeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentAfterPublishResultsWithOwnUserId() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(studentExam.getUser().getLogin());

        var studentExamGradeInfoFromServerForUserId = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId()
                + "/grade-summary?userId=" + studentExam.getUser().getId(), HttpStatus.OK, StudentExamWithGradeDTO.class);

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServerForUserId.gradeType()).isEqualTo(studentExamGradeInfoFromServer.gradeType());
        assertThat(studentExamGradeInfoFromServerForUserId.studentResult().overallGrade()).isEqualTo(studentExamGradeInfoFromServer.studentResult().overallGrade());
        assertThat(studentExamGradeInfoFromServerForUserId.studentResult().overallPointsAchieved())
                .isEqualTo(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved());
        assertThat(studentExamGradeInfoFromServerForUserId.studentResult().hasPassed()).isEqualTo(studentExamGradeInfoFromServer.studentResult().hasPassed());
        assertThat(studentExamGradeInfoFromServer.studentExam()).isEqualTo(studentExam);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsStudentAfterPublishResultsWithOtherUserId() throws Exception {
        exam2 = createStudentExamWithResultsAndAssessments(true, 2).getExam();

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        // users try to access exam summary after results are published
        User student1 = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        userUtilService.changeUser(student1.getLogin());
        User student2 = userUtilService.getUserByLogin(TEST_PREFIX + "student2");
        // Note: student1 cannot see the grade summary for student2
        request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam1.getId() + "/grade-summary?userId=" + student2.getId(),
                HttpStatus.FORBIDDEN, StudentExamWithGradeDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleAsInstructorAfterPublishResultsWithOtherUserId() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);
        exam2 = studentExam.getExam();

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        var studentExamGradeInfoFromServer = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId()
                + "/grade-summary?userId=" + studentExam.getUser().getId(), HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallScoreAchieved()).isEqualTo(100.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("1.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedStudentExamSummaryWithGradingScaleWithCorrectlyRoundedPoints() throws Exception {
        StudentExam studentExam = createStudentExamWithResultsAndAssessments(true, 1);

        GradingScale gradingScale = createGradeScale(false);
        gradingScale.setExam(exam2);
        gradingScaleRepository.save(gradingScale);

        studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionsResultIgnoreTestRuns(studentExam.getUser().getId(),
                studentExam.getExercises());
        List<StudentParticipation> participations = studentParticipationRepository
                .findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionsResultIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
        var latestResults = participations.stream().flatMap(participation -> participation.getSubmissions().stream().map(Submission::getLatestResult)).toList();
        for (var result : latestResults) {
            // First set all results to 0 since we don't want any additions to affect the manually assigned results below.
            result.setScore(0.0);
        }

        // The sum of the below scores have more than 1 digits after decimal due to how doubles are stored.
        // i.e. 0.3 + 0.3 + 0.3 = 0.8999999999999999
        latestResults.stream().limit(3).forEach(result -> {
            Exercise exercise = result.getSubmission().getParticipation().getExercise();
            exercise.setMaxPoints(100.0);  // To make points equal to scores for simplicity.
            result.setScore(0.3);
        });

        resultRepository.saveAll(latestResults);
        exerciseRepository.saveAll(latestResults.stream().map(result -> result.getSubmission().getParticipation().getExercise()).toList());

        // Assert prerequisites of this test case
        final int desiredAccuracyOfScores = 1;
        assertThat(studentExam.getExam().getCourse().getAccuracyOfScores()).isEqualTo(desiredAccuracyOfScores);

        double sumOfResultScores = latestResults.stream().mapToDouble(Result::getScore).sum();
        double expectedOverallPoints = RoundingUtil.roundToNDecimalPlaces(sumOfResultScores, desiredAccuracyOfScores);

        assertThat(sumOfResultScores).isNotEqualTo(expectedOverallPoints);

        // Assert actual computed result.
        userUtilService.changeUser(studentExam.getUser().getLogin());
        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/grade-summary", HttpStatus.OK,
                StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(expectedOverallPoints);
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @ValueSource(booleans = { true, false })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedFinalExamSummaryWithBonusExam(boolean asStudent) throws Exception {
        StudentExam finalStudentExam = createStudentExamWithResultsAndAssessments(false, 1);
        jenkinsRequestMockProvider.reset();
        StudentExam bonusStudentExam = createStudentExamWithResultsAndAssessments(false, 1);

        BonusStrategy bonusStrategy = BonusStrategy.GRADES_CONTINUOUS;

        Exam finalExam = configureFinalExamWithBonusExam(finalStudentExam, bonusStudentExam, bonusStrategy);

        String queryParam = "";
        if (asStudent) {
            // users tries to access exam summary after results are published
            userUtilService.changeUser(finalStudentExam.getUser().getLogin());
        }
        else {
            queryParam = "?userId=" + finalStudentExam.getUser().getId();
        }

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + finalExam.getCourse().getId() + "/exams/" + finalExam.getId() + "/student-exams/" + finalStudentExam.getId() + "/grade-summary" + queryParam,
                HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(24.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("3.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().mostSeverePlagiarismVerdict()).isNull();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusStrategy()).isEqualTo(bonusStrategy);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusFromTitle()).isEqualTo("Real exam 1");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusGrade()).isEqualTo("0.3");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalGrade()).isEqualTo("2.7");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().mostSeverePlagiarismVerdict()).isNull();
    }

    @NotNull
    private Exam configureFinalExamWithBonusExam(StudentExam finalStudentExam, StudentExam bonusStudentExam, BonusStrategy bonusStrategy) {
        var finalExam = examRepository.findById(finalStudentExam.getExam().getId()).orElseThrow();
        var bonusExam = examRepository.findById(bonusStudentExam.getExam().getId()).orElseThrow();
        assertThat(finalExam.getId()).isNotEqualTo(bonusExam.getId());

        GradingScale finalExamGradingScale = createGradeScale(false);
        finalExamGradingScale.setExam(finalExam);
        finalExamGradingScale.setBonusStrategy(bonusStrategy);
        gradingScaleRepository.save(finalExamGradingScale);

        GradingScale bonusGradingScale = createGradeScale(true);
        bonusGradingScale.setExam(bonusExam);
        gradingScaleRepository.save(bonusGradingScale);

        double weight = bonusStrategy == BonusStrategy.POINTS ? 1.0 : -1.0;
        var bonus = BonusFactory.generateBonus(bonusStrategy, weight, bonusGradingScale.getId(), finalExamGradingScale.getId());
        bonusRepository.save(bonus);

        StudentParticipation participationWithLatestResult = studentParticipationRepository
                .findByExerciseIdAndStudentIdAndTestRunWithLatestResult(finalStudentExam.getExercises().getFirst().getId(), finalStudentExam.getUser().getId(), false)
                .orElseThrow();
        Result result = participationUtilService.getResultsForParticipation(participationWithLatestResult).iterator().next();
        result.setScore(0.0); // To reduce grade to a grade lower than the max grade.
        resultRepository.save(result);
        return finalExam;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedFinalExamSummaryWithBonusExamAndPlagiarismAsStudent() throws Exception {
        StudentExam finalStudentExam = createStudentExamWithResultsAndAssessments(false, 1);
        jenkinsRequestMockProvider.reset();
        StudentExam bonusStudentExam = createStudentExamWithResultsAndAssessments(false, 1);

        BonusStrategy bonusStrategy = BonusStrategy.POINTS;

        Exam finalExam = configureFinalExamWithBonusExam(finalStudentExam, bonusStudentExam, bonusStrategy);

        User student = finalStudentExam.getUser();

        var finalPlagiarismCase = new PlagiarismCase();
        finalPlagiarismCase.setStudent(student);
        Exercise exerciseWithPointDeduction = finalStudentExam.getExercises().get(1); // We get the second exercise because the first one has already 0 points.
        finalPlagiarismCase.setExercise(exerciseWithPointDeduction);
        finalPlagiarismCase.setVerdict(PlagiarismVerdict.POINT_DEDUCTION);
        finalPlagiarismCase.setVerdictPointDeduction(50);
        plagiarismCaseRepository.save(finalPlagiarismCase);

        var bonusPlagiarismCase = new PlagiarismCase();
        bonusPlagiarismCase.setStudent(student);
        bonusPlagiarismCase.setExercise(bonusStudentExam.getExercises().getFirst());
        bonusPlagiarismCase.setVerdict(PlagiarismVerdict.PLAGIARISM);
        plagiarismCaseRepository.save(bonusPlagiarismCase);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(student.getLogin());

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + finalExam.getCourse().getId() + "/exams/" + finalExam.getId() + "/student-exams/" + finalStudentExam.getId() + "/grade-summary",
                HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.maxPoints()).isEqualTo(29.0);
        assertThat(studentExamGradeInfoFromServer.maxBonusPoints()).isEqualTo(5.0);
        assertThat(studentExamGradeInfoFromServer.gradeType()).isEqualTo(GradeType.GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(22.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("3.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().hasPassed()).isTrue();
        assertThat(studentExamGradeInfoFromServer.studentResult().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.POINT_DEDUCTION);
        assertThat(studentExamGradeInfoFromServer.studentResult().exerciseGroupIdToExerciseResult().get(exerciseWithPointDeduction.getExerciseGroup().getId()).achievedPoints())
                .isEqualTo(2.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusStrategy()).isEqualTo(bonusStrategy);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusFromTitle()).isEqualTo("Real exam 1");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusGrade()).isEqualTo(GradingScale.DEFAULT_PLAGIARISM_GRADE);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalPoints()).isEqualTo(22.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalGrade()).isEqualTo("3.0");
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().mostSeverePlagiarismVerdict()).isEqualTo(PlagiarismVerdict.PLAGIARISM);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGradedFinalExamSummaryWithPlagiarismAndNotParticipatedBonusExamAsStudent() throws Exception {
        StudentExam finalStudentExam = createStudentExamWithResultsAndAssessments(false, 1);
        jenkinsRequestMockProvider.reset();

        User student = finalStudentExam.getUser();
        final String noParticipationGrade = "NoParticipation";
        studentExam1.setSubmitted(false);
        studentExam1.setUser(student);
        studentExam1 = studentExamRepository.save(studentExam1);

        StudentExam bonusStudentExam = studentExam1;

        BonusStrategy bonusStrategy = BonusStrategy.POINTS;

        Exam finalExam = configureFinalExamWithBonusExam(finalStudentExam, bonusStudentExam, bonusStrategy);
        var bonusGradingScale = bonusRepository.findAllByBonusToExamId(finalExam.getId()).iterator().next().getSourceGradingScale();
        bonusGradingScale.setNoParticipationGrade(noParticipationGrade);
        gradingScaleRepository.save(bonusGradingScale);

        // users tries to access exam summary after results are published
        userUtilService.changeUser(student.getLogin());

        var studentExams = studentExamRepository.findAllWithExercisesByUserIdAndExamId(student.getId(), bonusGradingScale.getExam().getId());
        log.debug("Found {} student exams for student {} {} and exam {}", studentExams.size(), student.getId(), student.getLogin(), finalExam.getId());
        assertThat(studentExams).as("Found too many student exams" + studentExams).hasSize(1);

        var studentExamGradeInfoFromServer = request.get(
                "/api/exam/courses/" + finalExam.getCourse().getId() + "/exams/" + finalExam.getId() + "/student-exams/" + finalStudentExam.getId() + "/grade-summary",
                HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.studentResult().overallPointsAchieved()).isEqualTo(24.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().overallGrade()).isEqualTo("3.0");

        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().studentPointsOfBonusSource()).isZero();
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().bonusGrade()).isEqualTo(noParticipationGrade);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalPoints()).isEqualTo(24.0);
        assertThat(studentExamGradeInfoFromServer.studentResult().gradeWithBonus().finalGrade()).isEqualTo("3.0");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteExamWithStudentExamsAfterConductionAndEvaluation() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        final StudentExam studentExamWithSubmissions = addExamExerciseSubmissionsForUser(exam2, studentExam.getUser().getLogin(), studentExam);

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(3));
        exam2 = examRepository.save(exam2);

        // submitExam
        request.postWithoutResponseBody("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/submit", studentExamWithSubmissions, HttpStatus.OK);
        var studentExamFinished = request.get(
                "/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExamWithSubmissions.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);

        exam2.setEndDate(ZonedDateTime.now());
        exam2 = examRepository.save(exam2);

        // Add results to all exercise submissions (evaluation)
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        for (var exercise : studentExamFinished.getExercises()) {
            if (exercise instanceof QuizExercise) {
                continue;
            }

            Participation participation = exercise.getStudentParticipations().iterator().next();
            participation.setExercise(exercise);
            Optional<Submission> latestSubmission = participation.findLatestSubmission();

            participationUtilService.addResultToSubmission(participation, latestSubmission.orElseThrow());
        }
        exam2.setPublishResultsDate(ZonedDateTime.now());
        exam2 = examRepository.save(exam2);
        exam2 = examRepository.findByIdWithExamUsersExerciseGroupsAndExercisesElseThrow(exam2.getId());

        // evaluate quizzes
        request.postWithoutLocation("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId() + "/student-exams/evaluate-quiz-exercises", null, HttpStatus.OK,
                new HttpHeaders());

        jenkinsRequestMockProvider.reset();
        final ProgrammingExercise programmingExercise = (ProgrammingExercise) exam2.getExerciseGroups().get(6).getExercises().iterator().next();

        SecurityContextHolder.setContext(TestSecurityContextHolder.getContext());

        Set<User> users = exam2.getRegisteredUsers();
        mockDeleteProgrammingExercise(programmingExercise, users);

        request.delete("/api/exam/courses/" + exam2.getCourse().getId() + "/exams/" + exam2.getId(), HttpStatus.OK);
        assertThat(examRepository.findById(exam2.getId())).as("Exam was deleted").isEmpty();

        deleteExamWithInstructor(exam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRun() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRunWithReferencedParticipationsDeleteOneParticipation() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun1 = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var testRun2 = new StudentExam();
        testRun2.setTestRun(true);
        testRun2.setExam(testRun1.getExam());
        testRun2.setUser(instructor);
        testRun2.setExercises(List.of(testRun1.getExercises().getFirst()));
        testRun2.setWorkingTime(testRun1.getWorkingTime());
        studentExamRepository.save(testRun2);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun1.getId(), HttpStatus.OK);
        var testRunList = studentExamRepository.findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(exam.getId());
        assertThat(testRunList).hasSize(1);
        testRunList.getFirst().getExercises().forEach(exercise -> assertThat(exercise.getStudentParticipations()).isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRunWithReferencedParticipationsDeleteNoParticipation() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun1 = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var testRun2 = new StudentExam();
        testRun2.setTestRun(true);
        testRun2.setExam(testRun1.getExam());
        testRun2.setUser(instructor);
        testRun2.setExercises(List.of(testRun1.getExercises().getFirst()));
        testRun2.setWorkingTime(testRun1.getWorkingTime());
        studentExamRepository.save(testRun2);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun2.getId(), HttpStatus.OK);
        var testRunList = studentExamRepository.findAllTestRunsWithExercisesParticipationsSubmissionsResultsByExamId(exam.getId());
        assertThat(testRunList).hasSize(1);
        testRunList.getFirst().getExercises().forEach(exercise -> assertThat(exercise.getStudentParticipations()).isNotEmpty());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeleteTestRunWithMissingParticipation() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var exam = examUtilService.addExam(course1);
        exam = examUtilService.addTextModelingProgrammingExercisesToExam(exam, false, false);
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam, instructor, exam.getExerciseGroups());
        var participations = studentParticipationRepository.findByExerciseIdAndStudentIdWithEagerSubmissions(testRun.getExercises().getFirst().getId(), instructor.getId());
        assertThat(participations).isNotEmpty();
        participationDeletionService.delete(participations.getFirst().getId(), true);
        request.delete("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run/" + testRun.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeleteTestRunAsTutor() throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");
        var testRun = examUtilService.setupTestRunForExamWithExerciseGroupsForInstructor(exam1, instructor, exam1.getExerciseGroups());
        request.delete("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/test-run/" + testRun.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testCreateTestRun() throws Exception {
        createTestRun();
    }

    /**
     * the server invokes SecurityUtils.setAuthorizationObject() so after invoking this method you need to "login" the user again
     *
     * @return the created test run
     * @throws Exception if errors occur
     */
    private StudentExam createTestRun() throws Exception {
        testRunExam = examUtilService.addExam(course1);
        testRunExam = examUtilService.addTextModelingProgrammingExercisesToExam(testRunExam, false, true);
        return createTestRun(testRunExam);
    }

    private StudentExam createTestRun(Exam exam) throws Exception {
        var instructor = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        StudentExam testRun = new StudentExam();
        testRun.setExercises(new ArrayList<>());

        exam.getExerciseGroups().forEach(exerciseGroup -> testRun.getExercises().add(exerciseGroup.getExercises().iterator().next()));
        testRun.setExam(exam);
        testRun.setWorkingTime(6000);
        testRun.setUser(instructor);

        var testRunsInDbBefore = studentExamRepository.findAllByExamId_AndTestRunIsTrue(exam.getId());
        var newTestRun = request.postWithResponseBody("/api/exam/courses/" + exam.getCourse().getId() + "/exams/" + exam.getId() + "/test-run", testRun, StudentExam.class,
                HttpStatus.OK);
        var testRunsInDbAfter = studentExamRepository.findAllByExamId_AndTestRunIsTrue(exam.getId());
        assertThat(testRunsInDbAfter).hasSize(testRunsInDbBefore.size() + 1);
        assertThat(newTestRun.isTestRun()).isTrue();
        assertThat(newTestRun.getWorkingTime()).isEqualTo(6000);
        assertThat(newTestRun.getUser()).isEqualTo(instructor);
        return newTestRun;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitTestRun() throws Exception {
        var testRun = createTestRun();
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        var testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/test-run/" + testRun.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        QuizExercise quizExercise = null;
        QuizSubmission quizSubmission = null;

        for (var exercise : testRunResponse.getExercises()) {
            var participation = exercise.getStudentParticipations().iterator().next();
            var submission = participation.getSubmissions().iterator().next();
            if (exercise instanceof QuizExercise) {
                quizExercise = (QuizExercise) exercise;
                quizSubmission = (QuizSubmission) submission;
                submitQuizInExam(quizExercise, quizSubmission);
            }
        }

        assertThat(quizExercise).isNotNull();
        request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/submit", testRunResponse, HttpStatus.OK, null);
        testRunResponse = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/" + testRunResponse.getId() + "/summary",
                HttpStatus.OK, StudentExam.class);

        checkQuizSubmission(quizExercise.getId(), quizSubmission.getId());

        // reconnect references so that the following method works
        testRunResponse.getExercises().forEach(exercise -> exercise.getStudentParticipations().forEach(studentParticipation -> studentParticipation.setExercise(exercise)));
        // invoke a second time to test the else case in this method
        SecurityUtils.setAuthorizationObject();
        examQuizService.evaluateQuizParticipationsForTestRunAndTestExam(testRunResponse);
        // make sure that no second result is created
        checkQuizSubmission(quizExercise.getId(), quizSubmission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testTestRunGradeSummaryDoesNotReturn404() throws Exception {
        StudentExam testRun = createTestRun();
        testRun.setSubmitted(true);
        studentExamRepository.save(testRun);

        Exam exam = testRun.getExam();
        exam.setPublishResultsDate(ZonedDateTime.now());
        exam.setExampleSolutionPublicationDate(ZonedDateTime.now().plusDays(2));

        List<ExerciseGroup> exerciseGroups = new ArrayList<>();
        testRun.getExercises().forEach((exercise -> exerciseGroups.add(exercise.getExerciseGroup())));

        exam.setExerciseGroups(exerciseGroups);
        examRepository.save(exam);

        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        User instructor1 = userUtilService.getUserByLogin(TEST_PREFIX + "instructor1");

        StudentExamWithGradeDTO studentExamGradeInfoFromServer = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testRunExam.getId() + "/student-exams/"
                + testRun.getId() + "/grade-summary?userId=" + instructor1.getId() + "&isTestRun=true", HttpStatus.OK, StudentExamWithGradeDTO.class);

        assertThat(studentExamGradeInfoFromServer.achievedPointsPerExercise().size()).isEqualTo(testRunExam.getExerciseGroups().size());
    }

    private void checkQuizSubmission(long quizExerciseId, long quizSubmissionId) {

        assertThat(quizSubmissionTestRepository.findByParticipation_Exercise_Id(quizExerciseId)).hasSize(1);

        List<Result> results = resultRepository.findBySubmissionParticipationExerciseIdOrderByCompletionDateAsc(quizExerciseId);
        assertThat(results).hasSize(1);
        var result = results.getFirst();
        assertThat(result.getSubmission().getId()).isEqualTo(quizSubmissionId);

        assertThat(result.getScore()).isEqualTo(44.4);
        var resultQuizSubmission = (QuizSubmission) result.getSubmission();
        resultQuizSubmission = quizSubmissionTestRepository.findWithEagerResultAndFeedbackById(resultQuizSubmission.getId()).orElseThrow();
        assertThat(resultQuizSubmission.getScoreInPoints()).isEqualTo(4D);
        var submittedAnswers = resultQuizSubmission.getSubmittedAnswers();
        for (SubmittedAnswer submittedAnswer : submittedAnswers) {
            // MC submitted answers 0 points as one correct and one false -> ALL_OR_NOTHING
            if (submittedAnswer instanceof MultipleChoiceSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isEqualTo(4D);
            } // DND submitted answers 0 points as one correct and two false -> PROPORTIONAL_WITH_PENALTY
              // or
              // SA submitted answers 0 points as one correct and one false -> PROPORTIONAL_WITHOUT_PENALTY
            else if (submittedAnswer instanceof DragAndDropSubmittedAnswer || submittedAnswer instanceof ShortAnswerSubmittedAnswer) {
                assertThat(submittedAnswer.getScoreInPoints()).isZero();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSubmitAndUnSubmitStudentExamAfterExamIsOver() throws Exception {
        StudentExam studentExam = prepareStudentExamsForConduction(false, true, 1).getFirst();

        // now we change to the point of time when the student exam needs to be submitted
        // IMPORTANT NOTE: this needs to be configured in a way that the individual student exam ended, but we are still in the grace period time
        exam2.setStartDate(ZonedDateTime.now().minusMinutes(10));
        studentExam.setStartedAndStartDate(ZonedDateTime.now().minusMinutes(8));
        exam2.setEndDate(ZonedDateTime.now().minusMinutes(5));
        exam2 = examRepository.save(exam2);
        studentExam = studentExamRepository.save(studentExam);
        assertThat(studentExam.isSubmitted()).isFalse();
        assertThat(studentExam.getSubmissionDate()).isNull();

        // submitting the exam, although the endDate is over
        userUtilService.changeUser(TEST_PREFIX + "student1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null,
                HttpStatus.CONFLICT);
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-submitted", null, HttpStatus.OK);
        studentExam = studentExamRepository.findById(studentExam.getId()).orElseThrow();
        assertThat(studentExam.isSubmitted()).isTrue();
        assertThat(studentExam.getSubmissionDate()).isNotNull();

        // setting the exam to unsubmitted again
        userUtilService.changeUser(TEST_PREFIX + "student1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null,
                HttpStatus.FORBIDDEN);
        userUtilService.changeUser(TEST_PREFIX + "instructor1");
        request.put("/api/exam/courses/" + course1.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null,
                HttpStatus.CONFLICT);
        request.put("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/student-exams/" + studentExam.getId() + "/toggle-to-unsubmitted", null, HttpStatus.OK);
        studentExam = studentExamRepository.findById(studentExam.getId()).orElseThrow();
        assertThat(studentExam.isSubmitted()).isFalse();
        assertThat(studentExam.getSubmissionDate()).isNull();
    }

    // StudentExamResource - getStudentExamForTestExamForConduction
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_NoStudentExamFound() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + 5555L + "/conduction", HttpStatus.NOT_FOUND, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_NoExamAccess() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam2, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_NotVisible() throws Exception {
        Exam exam = examUtilService.addTestExam(course1);
        exam.setVisibleDate(ZonedDateTime.now().plusMinutes(60));
        examRepository.save(exam);
        StudentExam studentExam = examUtilService.addStudentExamForTestExam(exam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetStudentExamForTestExamForConduction_UserIdMismatch() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam1.getId() + "/conduction",
                HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetStudentExamForTestExamForConduction_realExam() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForConduction_success() throws Exception {
        StudentExam studentExamReceived = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam1.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        assertThat(studentExamReceived).isEqualTo(studentExamForTestExam1);
    }

    // StudentExamResource - getStudentExamsForCoursePerUser

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetStudentExamsForCoursePerUser_NoCourseAccess() throws Exception {
        examUtilService.addStudentExamForTestExam(testExam1, userUtilService.getUserByLogin(TEST_PREFIX + "student42"));
        request.getList("/api/exam/courses/" + course1.getId() + "/test-exams-per-user", HttpStatus.FORBIDDEN, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamsForCoursePerUser_success() throws Exception {
        examUtilService.addStudentExamForTestExam(exam2, userUtilService.getUserByLogin(TEST_PREFIX + "student2"));
        List<StudentExam> studentExamListReceived = request.getList("/api/exam/courses/" + course1.getId() + "/test-exams-per-user", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamListReceived).hasSizeGreaterThanOrEqualTo(2);
        assertThat(studentExamListReceived).contains(studentExamForTestExam1, studentExamForTestExam2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamsForCoursePerUser_success_noStudentExams() throws Exception {
        course2 = courseUtilService.addEmptyCourse();
        List<StudentExam> studentExamListReceived = request.getList("/api/exam/courses/" + course2.getId() + "/test-exams-per-user", HttpStatus.OK, StudentExam.class);
        assertThat(studentExamListReceived).isEmpty();
    }

    // StudentExamResource - getStudentExamForTestExamForSummary

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_NoStudentExamFound() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + 5555L + "/summary", HttpStatus.NOT_FOUND, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetStudentExamForTestExamForSummary_NoCourseAccess() throws Exception {
        StudentExam studentExam = examUtilService.addStudentExamForTestExam(testExam1, userUtilService.getUserByLogin(TEST_PREFIX + "student42"));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetStudentExamForTestExamForSummary_NoExamAccess() throws Exception {
        Exam exam99 = examUtilService.addTestExam(course1);
        StudentExam studentExam99 = examUtilService.addStudentExamForTestExam(exam99, userUtilService.getUserByLogin(TEST_PREFIX + "student42"));
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam99.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_NotVisible() throws Exception {
        Exam exam = examUtilService.addTestExam(course1);
        exam.setVisibleDate(ZonedDateTime.now().plusMinutes(60));
        examRepository.save(exam);
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam.getId() + "/student-exams/" + studentExamForTestExam2.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetStudentExamForTestExamForSummary_UserIdMismatch() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam2.getId() + "/summary", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_realExam() throws Exception {
        studentExam1.setSubmitted(true);
        studentExamRepository.save(studentExam1);
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/summary", HttpStatus.OK, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForTestExamForSummary_success() throws Exception {
        StudentExam studentExamReceived = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExam2.getId() + "/student-exams/" + studentExamForTestExam2.getId() + "/summary", HttpStatus.OK,
                StudentExam.class);
        assertThat(studentExamReceived).isEqualTo(studentExamForTestExam2);
    }

    // StudentExamRessource - GetStudentExamForConduction
    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetStudentExamForConduction_notRegisteredInCourse() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_studentExamNotExistent() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + 5555L + "/conduction", HttpStatus.NOT_FOUND, StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_examIdNotMatching() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + 2 + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.CONFLICT,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_realExam() throws Exception {
        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam1.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_successful() throws Exception {
        StudentExam studentExamRetrieved = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExam1.getId() + "/student-exams/" + studentExamForTestExam1.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);
        assertThat(studentExamRetrieved).isEqualTo(studentExamForTestExam1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetStudentExamForConduction_notVisible() throws Exception {
        Exam testExam = examUtilService.addTestExam(course1);
        testExam.setVisibleDate(ZonedDateTime.now().plusMinutes(60));
        testExam = examRepository.save(testExam);
        StudentExam studentExam = examUtilService.addStudentExamWithUser(testExam, userUtilService.getUserByLogin(TEST_PREFIX + "student1"));

        request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExam.getId() + "/student-exams/" + studentExam.getId() + "/conduction", HttpStatus.FORBIDDEN,
                StudentExam.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testConductionOfTestExam_successful() throws Exception {
        Exam testExamWithExercises = examUtilService.addTestExam(course1);
        testExamWithExercises = examUtilService.addTextModelingProgrammingExercisesToExam(testExamWithExercises, false, true);
        testExamWithExercises.setExamMaxPoints(19);
        testExamWithExercises.setVisibleDate(ZonedDateTime.now().minusHours(1));
        testExamWithExercises.setStartDate(ZonedDateTime.now().minusMinutes(30));
        testExamWithExercises.setWorkingTime(6000);
        var examUser5 = new ExamUser();
        examUser5.setExam(testExamWithExercises);
        examUser5.setUser(student1);
        examUser5 = examUserRepository.save(examUser5);
        testExamWithExercises.addExamUser(examUser5);
        testExamWithExercises = examRepository.save(testExamWithExercises);

        // Step 1: Call /start
        StudentExam studentExamForStart = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + testExamWithExercises.getId() + "/own-student-exam", HttpStatus.OK,
                StudentExam.class);

        assertThat(studentExamForStart.getUser()).isEqualTo(student1);
        assertThat(studentExamForStart.getExam().getId()).isEqualTo(testExamWithExercises.getId());
        assertThat(studentExamForStart.isStarted()).isNull();
        assertThat(studentExamForStart.isSubmitted()).isFalse();
        assertThat(studentExamForStart.getStartedDate()).isNull();
        assertThat(studentExamForStart.getSubmissionDate()).isNull();
        assertThat(studentExamForStart.getExercises()).hasSize(0);

        // Step 2: Call /conduction to get the exam with exercises and started date set
        StudentExam studentExamForConduction = request.get(
                "/api/exam/courses/" + course1.getId() + "/exams/" + testExamWithExercises.getId() + "/student-exams/" + studentExamForStart.getId() + "/conduction", HttpStatus.OK,
                StudentExam.class);

        assertThat(studentExamForConduction.getId()).isEqualTo(studentExamForStart.getId());
        assertThat(studentExamForConduction.getUser()).isEqualTo(student1);
        assertThat(studentExamForConduction.getExam().getId()).isEqualTo(testExamWithExercises.getId());
        assertThat(studentExamForConduction.isStarted()).isTrue();
        assertThat(studentExamForConduction.isSubmitted()).isFalse();
        // Acceptance range, startedDate is to be set to now()
        assertThat(ZonedDateTime.now().minusSeconds(10).isBefore(studentExamForConduction.getStartedDate())).isTrue();
        assertThat(ZonedDateTime.now().plusSeconds(10).isAfter(studentExamForConduction.getStartedDate())).isTrue();
        assertThat(studentExamForConduction.getSubmissionDate()).isNull();
        assertThat(studentExamForConduction.getExercises()).hasSize(3);
        QuizExercise quizExercise = (QuizExercise) studentExamForConduction.getExercises().get(2);
        assertThat(quizExercise.getQuizQuestions()).hasSize(3);

        Map<User, List<Exercise>> exercisesOfUser = studentExamService.getExercisesOfUserMap(Set.of(studentExamForConduction));
        final var studentParticipations = studentParticipationRepository.findByStudentIdAndIndividualExercisesWithEagerLatestSubmissionsResultIgnoreTestRuns(student1.getId(),
                exercisesOfUser.get(student1));
        for (StudentParticipation studentParticipation : studentParticipations) {
            // Acceptance range, initialization Date is to be set to now()
            assertThat(ZonedDateTime.now().minusSeconds(10).isBefore(studentParticipation.getInitializationDate())).isTrue();
            assertThat(ZonedDateTime.now().plusSeconds(10).isAfter(studentParticipation.getInitializationDate())).isTrue();
            // Compare started date and initialization Date
            studentExamForConduction
                    .setStartedAndStartDate(ZonedDateTime.ofInstant(studentExamForConduction.getStartedDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
            studentParticipation
                    .setInitializationDate(ZonedDateTime.ofInstant(studentParticipation.getInitializationDate().truncatedTo(ChronoUnit.MILLIS).toInstant(), ZoneId.of("UTC")));
            assertThat(studentParticipation.getInitializationDate()).isCloseTo(studentExamForConduction.getStartedDate(), within(1, ChronoUnit.SECONDS));
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLongestWorkingTimeForExam() throws Exception {
        // Step 1: Create mock student exams
        List<StudentExam> studentExams = prepareStudentExamsForConduction(false, true, NUMBER_OF_STUDENTS);

        // Step 2: Get the maximum working time among the exams to find the longest time
        final int longestWorkingTime = studentExams.stream().mapToInt(StudentExam::getWorkingTime).max().orElse(0);

        // When
        final int response = request.get("/api/exam/courses/" + course2.getId() + "/exams/" + exam2.getId() + "/longest-working-time", HttpStatus.OK, Integer.class);

        // Then
        assertThat(response).isEqualTo(longestWorkingTime);
    }

    @Nested
    class ChangedAndUnchangedSubmissionsIntegrationTest {

        // find User With Groups And Authorities + find Student Exam ById With Exercises + find Exam Session By Student Exam Id
        // + update Student Exam + find Student Participations By Student Exam With Submissions Result
        private final int BASE_QUERY_COUNT = 5;

        private TextExercise textExercise;

        private ModelingExercise modeExercise;

        private QuizExercise quizExercise;

        private TextSubmission textSubmission;

        private ModelingSubmission modeSubmission;

        private QuizSubmission quizSubmission;

        private DragAndDropQuestion dragAndDropQuestion;

        private MultipleChoiceQuestion multipleChoiceQuestion;

        private ShortAnswerQuestion shortAnswerQuestion;

        private StudentExam studentExamForConduction;

        @BeforeEach
        void setUpStudentExamWithExercises() throws Exception {
            userUtilService.changeUser(TEST_PREFIX + "instructor1");

            // Add exercises to active exam
            exam1.setExamMaxPoints(19);
            exam1 = examUtilService.addExerciseGroupsAndExercisesToExam(exam1, false);

            // Generate student exam
            List<StudentExam> studentExams = request.postListWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/generate-student-exams",
                    Optional.empty(), StudentExam.class, HttpStatus.OK);
            assertThat(studentExams).hasSize(exam1.getExamUsers().size());
            assertThat(studentExamRepository.findByExamId(exam1.getId())).hasSize(1);

            // Prepare student exam
            ExamPrepareExercisesTestUtil.prepareExerciseStart(request, exam1, course1);
            StudentExam studentExam = studentExams.getFirst();
            userUtilService.changeUser(studentExam.getUser().getLogin());
            studentExamForConduction = request.get("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExam.getId() + "/conduction",
                    HttpStatus.OK, StudentExam.class);
            assertThat(studentExamForConduction.isStarted()).isTrue();

            // Get exercises for testing
            textExercise = ExerciseUtilService.getFirstExerciseWithType(studentExamForConduction, TextExercise.class);
            modeExercise = ExerciseUtilService.getFirstExerciseWithType(studentExamForConduction, ModelingExercise.class);
            quizExercise = ExerciseUtilService.getFirstExerciseWithType(studentExamForConduction, QuizExercise.class);

            // Get quiz questions for testing
            for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
                if (quizQuestion instanceof DragAndDropQuestion && dragAndDropQuestion == null) {
                    dragAndDropQuestion = (DragAndDropQuestion) quizQuestion;
                }
                else if (quizQuestion instanceof MultipleChoiceQuestion && multipleChoiceQuestion == null) {
                    multipleChoiceQuestion = (MultipleChoiceQuestion) quizQuestion;
                }
                else if (quizQuestion instanceof ShortAnswerQuestion && shortAnswerQuestion == null) {
                    shortAnswerQuestion = (ShortAnswerQuestion) quizQuestion;
                }
            }
            assertThat(dragAndDropQuestion).isNotNull();
            assertThat(multipleChoiceQuestion).isNotNull();
            assertThat(shortAnswerQuestion).isNotNull();

            textSubmission = (TextSubmission) textExercise.getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow();
            modeSubmission = (ModelingSubmission) modeExercise.getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow();
            quizSubmission = (QuizSubmission) quizExercise.getStudentParticipations().iterator().next().findLatestSubmission().orElseThrow();
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testUnchangedSubmissionsDoNotChangeQueryCount() throws Exception {
            assertThatDb(() -> request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction,
                    StudentExam.class, HttpStatus.OK)).hasBeenCalledAtMostTimes(BASE_QUERY_COUNT);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndSubmittedDoesNotChangeQueryCount() throws Exception {
            // Given
            final String changedAnswer = "This is a changed and submitted answer";
            textSubmission.setText(changedAnswer);
            request.put("/api/text/exercises/" + textExercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);

            final String changedModel = "This is a changed and submitted model";
            final String changedExplanation = "This is a changed and submitted explanation";
            changeModelingSubmission(changedModel, changedExplanation);
            request.put("/api/modeling/exercises/" + modeExercise.getId() + "/modeling-submissions", modeSubmission, HttpStatus.OK);

            DragAndDropMapping changedMapping = getchangedDragAndDropMapping(1, 0);

            final String text = "Changed and submitted short answer text";
            final int spotIndex = 0;
            ShortAnswerSubmittedText changedText = getChangedShortAnswerSubmittedText(text, spotIndex);

            final List<Integer> selectedOptionIndices = List.of(0, 1);
            List<AnswerOption> changedAnswerOptions = getChangedAnswerOptions(selectedOptionIndices);

            request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);

            // load Quiz Submissions Submitted Answers (for comparison) * 3
            final int quizQueryCount = 3;

            // When
            assertThatDb(() -> request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction,
                    StudentExam.class, HttpStatus.OK)).hasBeenCalledAtMostTimes(BASE_QUERY_COUNT + quizQueryCount);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);

            // Then
            TextExercise textExerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, TextExercise.class);
            TextSubmission textSubmissionAfterExamSubmission = (TextSubmission) textExerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();
            assertThat(textSubmissionAfterExamSubmission).isEqualTo(textSubmission);
            assertThat(textSubmissionAfterExamSubmission.getText()).isEqualTo(changedAnswer);
            assertVersionedSubmission(textSubmission);
            assertVersionedSubmission(textSubmissionAfterExamSubmission);

            ModelingExercise modeExerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, ModelingExercise.class);
            ModelingSubmission modeSubmissionAfterExamSubmission = (ModelingSubmission) modeExerciseAfterExamSubmission.getStudentParticipations().iterator().next()
                    .findLatestSubmission().orElseThrow();
            assertThat(modeSubmissionAfterExamSubmission).isEqualTo(modeSubmission);
            assertThat(modeSubmissionAfterExamSubmission.getModel()).isEqualTo(changedModel);
            assertThat(modeSubmissionAfterExamSubmission.getExplanationText()).isEqualTo(changedExplanation);
            assertVersionedSubmission(modeSubmission);
            assertVersionedSubmission(modeSubmissionAfterExamSubmission);

            QuizExercise quizExerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission quizSubmissionAfterExamSubmission = (QuizSubmission) quizExerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();
            assertThat(quizSubmissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(quizSubmissionAfterExamSubmission);

            verifyDragAndDropSubmission(changedMapping, quizSubmissionAfterExamSubmission);
            verifyShortAnswerSubmission(changedText, quizSubmissionAfterExamSubmission);
            verifyMultipleChoiceSubmission(changedAnswerOptions, quizSubmissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedTextSubmission() throws Exception {
            // Given
            final String changedAnswer = "This is a changed answer";
            textSubmission.setText(changedAnswer);

            // When
            request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, StudentExam.class,
                    HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            TextExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, TextExercise.class);
            TextSubmission submissionAfterExamSubmission = (TextSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(textSubmission);
            assertThat(submissionAfterExamSubmission.getText()).isEqualTo(changedAnswer);
            assertVersionedSubmission(textSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedModelingSubmission() throws Exception {
            // Given
            final String changedModel = "This is a changed model";
            final String changedExplanation = "This is a changed explanation";
            changeModelingSubmission(changedModel, changedExplanation);

            // When
            request.postWithResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, StudentExam.class,
                    HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            ModelingExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, ModelingExercise.class);
            ModelingSubmission submissionAfterExamSubmission = (ModelingSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(modeSubmission);
            assertThat(submissionAfterExamSubmission.getModel()).isEqualTo(changedModel);
            assertThat(submissionAfterExamSubmission.getExplanationText()).isEqualTo(changedExplanation);
            assertVersionedSubmission(modeSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedDragAndDropQuestionSubmission() throws Exception {
            // Given
            DragAndDropMapping changedMapping = getchangedDragAndDropMapping(0, 1);

            // When
            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            QuizExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission submissionAfterExamSubmission = (QuizSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);

            verifyDragAndDropSubmission(changedMapping, submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedShortAnswerQuestionSubmission() throws Exception {
            // Given
            getChangedShortAnswerSubmittedText("First changed and submitted answer", 0);
            request.put("/api/quiz/exercises/" + quizExercise.getId() + "/submissions/exam", quizSubmission, HttpStatus.OK);
            quizSubmission.removeSubmittedAnswers(quizSubmission.getSubmittedAnswers().iterator().next());

            final String text = "Changed short answer text";
            final int spotIndex = 1;
            ShortAnswerSubmittedText changedText = getChangedShortAnswerSubmittedText(text, spotIndex);

            // When
            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            QuizExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission submissionAfterExamSubmission = (QuizSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);

            verifyShortAnswerSubmission(changedText, submissionAfterExamSubmission);
        }

        @Test
        @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
        void testChangedAndNotSubmittedMultipleChoiceQuestionSubmission() throws Exception {
            // Given
            final List<Integer> selectedOptionIndices = List.of(1);
            List<AnswerOption> changedAnswerOptions = getChangedAnswerOptions(selectedOptionIndices);

            // When
            request.postWithoutResponseBody("/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/submit", studentExamForConduction, HttpStatus.OK);
            StudentExam submittedExam = request.get(
                    "/api/exam/courses/" + course1.getId() + "/exams/" + exam1.getId() + "/student-exams/" + studentExamForConduction.getId() + "/summary", HttpStatus.OK,
                    StudentExam.class);
            QuizExercise exerciseAfterExamSubmission = ExerciseUtilService.getFirstExerciseWithType(submittedExam, QuizExercise.class);
            QuizSubmission submissionAfterExamSubmission = (QuizSubmission) exerciseAfterExamSubmission.getStudentParticipations().iterator().next().findLatestSubmission()
                    .orElseThrow();

            // Then
            assertThat(submissionAfterExamSubmission).isEqualTo(quizSubmission);
            assertVersionedSubmission(quizSubmission);
            assertVersionedSubmission(submissionAfterExamSubmission);

            verifyMultipleChoiceSubmission(changedAnswerOptions, submissionAfterExamSubmission);
        }

        private void changeModelingSubmission(String changedModel, String changedExplanation) {
            modeSubmission.setModel(changedModel);
            modeSubmission.setExplanationText(changedExplanation);
        }

        private DragAndDropMapping getchangedDragAndDropMapping(int dndDragItemIndex, int dndDropLocationIndex) {
            DragAndDropMapping changedMapping = new DragAndDropMapping();

            changedMapping.setDragItemIndex(dndDragItemIndex);
            changedMapping.setDragItem(dragAndDropQuestion.getDragItems().get(dndDragItemIndex));

            changedMapping.setDropLocationIndex(dndDropLocationIndex);
            changedMapping.setDropLocation(dragAndDropQuestion.getDropLocations().get(dndDropLocationIndex));

            DragAndDropSubmittedAnswer changedAnswer = new DragAndDropSubmittedAnswer();
            changedAnswer.getMappings().add(changedMapping);
            changedAnswer.setQuizQuestion(dragAndDropQuestion);

            quizSubmission.getSubmittedAnswers().add(changedAnswer);
            return changedMapping;
        }

        private ShortAnswerSubmittedText getChangedShortAnswerSubmittedText(String text, int spotIndex) {
            ShortAnswerSubmittedText changedText = new ShortAnswerSubmittedText();
            changedText.setText(text);
            changedText.setSpot(shortAnswerQuestion.getSpots().get(spotIndex));

            ShortAnswerSubmittedAnswer changedAnswer = new ShortAnswerSubmittedAnswer();
            changedAnswer.getSubmittedTexts().add(changedText);
            changedAnswer.setQuizQuestion(shortAnswerQuestion);

            quizSubmission.getSubmittedAnswers().add(changedAnswer);
            return changedText;
        }

        private List<AnswerOption> getChangedAnswerOptions(List<Integer> selectedOptionIndices) {
            List<AnswerOption> answerOptions = multipleChoiceQuestion.getAnswerOptions();

            MultipleChoiceSubmittedAnswer changedAnswer = new MultipleChoiceSubmittedAnswer();
            selectedOptionIndices.forEach(selectedOptionIndex -> changedAnswer.addSelectedOptions(answerOptions.get(selectedOptionIndex)));
            changedAnswer.setQuizQuestion(multipleChoiceQuestion);

            quizSubmission.getSubmittedAnswers().add(changedAnswer);
            return selectedOptionIndices.stream().map(answerOptions::get).toList();
        }

        private void verifyDragAndDropSubmission(DragAndDropMapping changedMapping, QuizSubmission submissionAfterExamSubmission) {
            DragAndDropSubmittedAnswer answerAfterSubmission = (DragAndDropSubmittedAnswer) submissionAfterExamSubmission.getSubmittedAnswerForQuestion(dragAndDropQuestion);
            Comparator<DragAndDropMapping> dndMappingComparator = Comparator.comparing(DragAndDropMapping::getDragItemIndex)
                    .thenComparing(DragAndDropMapping::getDropLocationIndex);

            assertThat(answerAfterSubmission.getMappings()).hasSize(1);
            assertThat(answerAfterSubmission.getMappings().iterator().next()).usingComparator(dndMappingComparator).isEqualTo(changedMapping);
        }

        private void verifyShortAnswerSubmission(ShortAnswerSubmittedText changedText, QuizSubmission submissionAfterExamSubmission) {
            ShortAnswerSubmittedAnswer answerAfterSubmission = (ShortAnswerSubmittedAnswer) submissionAfterExamSubmission.getSubmittedAnswerForQuestion(shortAnswerQuestion);
            Comparator<ShortAnswerSubmittedText> saMappingComparator = Comparator.comparing(ShortAnswerSubmittedText::getText).thenComparing(saText -> saText.getSpot().getId());

            assertThat(answerAfterSubmission.getSubmittedTexts()).hasSize(1);
            assertThat(answerAfterSubmission.getSubmittedTexts().iterator().next()).usingComparator(saMappingComparator).isEqualTo(changedText);
        }

        private void verifyMultipleChoiceSubmission(List<AnswerOption> changedAnswerOption, QuizSubmission submissionAfterExamSubmission) {
            MultipleChoiceSubmittedAnswer answerAfterSubmission = (MultipleChoiceSubmittedAnswer) submissionAfterExamSubmission
                    .getSubmittedAnswerForQuestion(multipleChoiceQuestion);

            assertThat(answerAfterSubmission.toSelectedIds()).containsAll(changedAnswerOption.stream().map(AnswerOption::getId).collect(Collectors.toSet()));
        }
    }
}
