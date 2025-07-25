package de.tum.cit.aet.artemis.plagiarism;

import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict.NO_PLAGIARISM;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict.POINT_DEDUCTION;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict.WARNING;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismVerdict;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismCaseInfoDTO;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismVerdictDTO;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class PlagiarismCaseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "plagiarismcaseintegration";

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    private Course course;

    private TextExercise textExercise;

    private TextExercise examTextExercise;

    private PlagiarismCase plagiarismCase1;

    private List<PlagiarismCase> coursePlagiarismCases;

    private List<PlagiarismCase> examPlagiarismCases;

    @BeforeEach
    void initTestCase() {
        // Per case, we have always 2 students
        int numberOfPlagiarismCases = 5;
        userUtilService.addUsers(TEST_PREFIX, numberOfPlagiarismCases * 2, 1, 1, 1);
        course = textExerciseUtilService.addCourseWithOneFinishedTextExercise();

        // We need at least 3 cases
        textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        coursePlagiarismCases = createPlagiarismCases(numberOfPlagiarismCases, textExercise);
        plagiarismCase1 = coursePlagiarismCases.getFirst();

        examTextExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        examPlagiarismCases = createPlagiarismCases(2, examTextExercise);
    }

    /***
     * Create a given amount of plagiarism cases
     *
     * @param numberOfPlagiarismCases The required number of cases
     * @return The list of generated plagiarism cases
     */
    private List<PlagiarismCase> createPlagiarismCases(int numberOfPlagiarismCases, Exercise exercise) {
        var plagiarismCasesList = new ArrayList<PlagiarismCase>();

        for (int i = 0; i < numberOfPlagiarismCases; i++) {
            PlagiarismCase plagiarismCase = new PlagiarismCase();
            User student = userUtilService.getUserByLogin(TEST_PREFIX + "student" + (i + 1));
            PlagiarismResult textPlagiarismResult = textExerciseUtilService.createPlagiarismResultForExercise(exercise);
            var plagiarismComparison = new PlagiarismComparison();

            PlagiarismSubmission plagiarismSubmission1 = new PlagiarismSubmission();
            PlagiarismSubmission plagiarismSubmission2 = new PlagiarismSubmission();

            plagiarismCase.setExercise(exercise);
            plagiarismCase.setStudent(student);
            plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);

            plagiarismComparison.setPlagiarismResult(textPlagiarismResult);
            plagiarismSubmission1.setStudentLogin(TEST_PREFIX + "student" + (i + 1));
            plagiarismSubmission1.setPlagiarismCase(plagiarismCase);
            plagiarismSubmission2.setStudentLogin(TEST_PREFIX + "student" + (i + 2));
            plagiarismSubmission2.setPlagiarismCase(plagiarismCase);
            plagiarismComparison.setSubmissionA(plagiarismSubmission1);
            plagiarismComparison.setSubmissionB(plagiarismSubmission2);
            plagiarismComparisonRepository.save(plagiarismComparison);
            plagiarismCasesList.add(plagiarismCase);
        }

        return plagiarismCasesList;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismCasesForCourseForInstructor_forbidden_student() throws Exception {
        request.getList("/api/plagiarism/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetPlagiarismCasesForCourseForInstructor_forbidden_tutor() throws Exception {
        request.getList("/api/plagiarism/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetPlagiarismCasesForCourseForInstructor_forbidden_editor() throws Exception {
        request.getList("/api/plagiarism/courses/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismCasesForCourseForInstructor() throws Exception {
        var plagiarismCasesResponse = request.getList("/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases/for-instructor", HttpStatus.OK, PlagiarismCase.class);
        assertThat(plagiarismCasesResponse).as("should get course plagiarism cases for instructor").containsExactlyInAnyOrderElementsOf(coursePlagiarismCases);
        for (var submission : plagiarismCasesResponse.getFirst().getPlagiarismSubmissions()) {
            assertThat(submission.getPlagiarismComparison().getPlagiarismResult().getExercise()).as("should prepare plagiarism case response entity").isNull();
            assertThat(submission.getPlagiarismComparison().getSubmissionA()).as("should prepare plagiarism case response entity").isNull();
            assertThat(submission.getPlagiarismComparison().getSubmissionB()).as("should prepare plagiarism case response entity").isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismCaseForInstructor_forbidden_student() throws Exception {
        request.get("/api/plagiarism/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetPlagiarismCaseForInstructor_forbidden_tutor() throws Exception {
        request.get("/api/plagiarism/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR ")
    void testGetPlagiarismCaseForInstructor_forbidden_editor() throws Exception {
        request.get("/api/plagiarism/courses/1/plagiarism-cases/1/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismCaseForInstructor() throws Exception {
        var plagiarismCase = request.get("/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-instructor", HttpStatus.OK,
                PlagiarismCase.class);
        assertThat(plagiarismCase).as("should get plagiarism case for instructor").isEqualTo(plagiarismCase1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismCasesForExamForInstructor_forbidden_student() throws Exception {
        request.getList("/api/plagiarism/courses/1/exams/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetPlagiarismCasesForExamForInstructor_forbidden_tutor() throws Exception {
        request.getList("/api/plagiarism/courses/1/exams/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetPlagiarismCasesForExamForInstructor_forbidden_editor() throws Exception {
        request.getList("/api/plagiarism/courses/1/exams/1/plagiarism-cases/for-instructor", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismCasesForExamForInstructor() throws Exception {
        Course examCourse = examTextExercise.getCourseViaExerciseGroupOrCourseMember();
        Exam exam = examTextExercise.getExerciseGroup().getExam();
        var plagiarismCasesResponse = request.getList("/api/plagiarism/courses/" + examCourse.getId() + "/exams/" + exam.getId() + "/plagiarism-cases/for-instructor",
                HttpStatus.OK, PlagiarismCase.class);
        assertThat(plagiarismCasesResponse).as("should get exam plagiarism cases for instructor").containsExactlyInAnyOrderElementsOf(examPlagiarismCases);
        for (var submission : plagiarismCasesResponse.getFirst().getPlagiarismSubmissions()) {
            assertThat(submission.getPlagiarismComparison().getPlagiarismResult().getExercise()).as("should remove unneeded elements from the response").isNull();
            assertThat(submission.getPlagiarismComparison().getSubmissionA()).as("should filter out submission A").isNull();
            assertThat(submission.getPlagiarismComparison().getSubmissionB()).as("should filter out submission B").isNull();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetPlagiarismCasesForExamForInstructor_wrongCourse() throws Exception {
        Course examCourse = examTextExercise.getCourseViaExerciseGroupOrCourseMember();
        Exam exam = examTextExercise.getExerciseGroup().getExam();

        assertThat(examCourse.getId()).isNotEqualTo(course.getId());

        request.getList("/api/plagiarism/courses/" + course.getId() + "/exams/" + exam.getId() + "/plagiarism-cases/for-instructor", HttpStatus.CONFLICT, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testSavePlagiarismCaseVerdict_forbidden_student() throws Exception {
        request.put("/api/plagiarism/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(NO_PLAGIARISM, "", 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testSavePlagiarismCaseVerdict_forbidden_tutor() throws Exception {
        request.put("/api/plagiarism/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(NO_PLAGIARISM, "", 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testSavePlagiarismCaseVerdict_forbidden_editor() throws Exception {
        request.put("/api/plagiarism/courses/1/plagiarism-cases/1/verdict", new PlagiarismVerdictDTO(NO_PLAGIARISM, "", 0), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSavePlagiarismCaseVerdict_warning() throws Exception {
        var plagiarismVerdictDTO = new PlagiarismVerdictDTO(WARNING, "This is a warning!", 0);
        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/verdict", plagiarismVerdictDTO, HttpStatus.OK);
        var updatedPlagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCase1.getId());
        assertThat(updatedPlagiarismCase.getVerdict()).as("should update plagiarism case verdict warning").isEqualTo(PlagiarismVerdict.WARNING);
        assertThat(updatedPlagiarismCase.getVerdictMessage()).as("should update plagiarism case verdict message").isEqualTo("This is a warning!");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSavePlagiarismCaseVerdict_pointDeduction() throws Exception {
        var plagiarismVerdictDTO = new PlagiarismVerdictDTO(POINT_DEDUCTION, "", 90);
        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/verdict", plagiarismVerdictDTO, HttpStatus.OK);
        var updatedPlagiarismCase = plagiarismCaseRepository.findByIdWithPlagiarismSubmissionsElseThrow(plagiarismCase1.getId());
        assertThat(updatedPlagiarismCase.getVerdict()).as("should update plagiarism case verdict point deduction").isEqualTo(PlagiarismVerdict.POINT_DEDUCTION);
        assertThat(updatedPlagiarismCase.getVerdictPointDeduction()).as("should update plagiarism case verdict point deduction").isEqualTo(90);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismCaseInfoReturnsEmptyWithoutPostForStudent() throws Exception {
        var plagiarismCaseInfo = request.get("/api/plagiarism/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-case", HttpStatus.OK, String.class);
        assertThat(plagiarismCaseInfo).as("should not get plagiarism case for exercise for student if there is no notification post yet").isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismCaseInfoWithoutVerdictForExerciseForStudent() throws Exception {
        addPost();

        var plagiarismCaseInfo = request.get("/api/plagiarism/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-case", HttpStatus.OK,
                PlagiarismCaseInfoDTO.class);
        assertThat(plagiarismCaseInfo.id()).as("should get plagiarism case for exercise for student").isEqualTo(plagiarismCase1.getId());
        assertThat(plagiarismCaseInfo.verdict()).as("should get null verdict before it is set").isNull();
    }

    private void addPost() {
        Post post = new Post();
        post.setAuthor(userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase1);
        post = postRepository.save(post);
        plagiarismCase1.setPost(post);
        plagiarismCaseRepository.save(plagiarismCase1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismCaseInfoWithVerdictForExerciseForStudent() throws Exception {
        var verdict = NO_PLAGIARISM;
        plagiarismCase1.setVerdict(verdict);

        addPost();

        var plagiarismCaseInfo = request.get("/api/plagiarism/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-case", HttpStatus.OK,
                PlagiarismCaseInfoDTO.class);
        assertThat(plagiarismCaseInfo.id()).as("should get plagiarism case for exercise for student").isEqualTo(plagiarismCase1.getId());
        assertThat(plagiarismCaseInfo.verdict()).as("should get the verdict after it is set").isEqualTo(verdict);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetMultiplePlagiarismCaseInfosForStudent() throws Exception {
        var emptyPlagiarismCaseInfosResponse = request.getMap(
                "/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases?exerciseId=" + textExercise.getId() + "&exerciseId=" + examTextExercise.getId(), HttpStatus.OK,
                Long.class, PlagiarismCaseInfoDTO.class);
        assertThat(emptyPlagiarismCaseInfosResponse).as("should return empty list when no post is sent").isEmpty();

        addPost();

        // It should give error when no exercise id is specified
        request.get("/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases", HttpStatus.BAD_REQUEST, String.class);

        var plagiarismCaseInfosResponse = request.getMap(
                "/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases?exerciseId=" + textExercise.getId() + "&exerciseId=" + examTextExercise.getId(), HttpStatus.OK,
                Long.class, PlagiarismCaseInfoDTO.class);

        assertThat(plagiarismCaseInfosResponse).hasSize(1);
        assertThat(plagiarismCaseInfosResponse.get(textExercise.getId()).id()).as("should only return plagiarism cases with post").isEqualTo(plagiarismCase1.getId());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetMultiplePlagiarismCaseInfosForStudent_conflict() throws Exception {
        long wrongCourseId = course.getId() + 1;
        var emptyPlagiarismCaseInfosResponse = request.getMap(
                "/api/plagiarism/courses/" + wrongCourseId + "/plagiarism-cases?exerciseId=" + textExercise.getId() + "&exerciseId=" + examTextExercise.getId(), HttpStatus.OK,
                Long.class, PlagiarismCaseInfoDTO.class);

        assertThat(emptyPlagiarismCaseInfosResponse).as("should return empty list when no post is sent").isEmpty();

        addPost();

        request.getMap("/api/plagiarism/courses/" + wrongCourseId + "/plagiarism-cases?exerciseId=" + textExercise.getId() + "&exerciseId=" + examTextExercise.getId(),
                HttpStatus.CONFLICT, Long.class, PlagiarismCaseInfoDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student2", roles = "USER")
    void testGetPlagiarismCaseForStudent_forbidden() throws Exception {
        request.get("/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-student", HttpStatus.FORBIDDEN, PlagiarismCase.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismCaseForStudent() throws Exception {
        var plagiarismCase = request.get("/api/plagiarism/courses/" + course.getId() + "/plagiarism-cases/" + plagiarismCase1.getId() + "/for-student", HttpStatus.OK,
                PlagiarismCase.class);
        assertThat(plagiarismCase).as("should get plagiarism case for student").isEqualTo(plagiarismCase1);
        for (var submission : plagiarismCase.getPlagiarismSubmissions()) {
            assertThat(submission.getPlagiarismComparison().getPlagiarismResult().getExercise()).as("should prepare plagiarism case response entity").isNull();
            assertThat(submission.getPlagiarismComparison().getSubmissionA()).as("should prepare plagiarism case response entity").isNull();
            assertThat(submission.getPlagiarismComparison().getSubmissionB()).as("should prepare plagiarism case response entity").isNull();
        }
    }

    @Test
    void testPlagiarismCase_getStudents() {

        var individualPlagiarismCase = new PlagiarismCase();
        assertThat(individualPlagiarismCase.getStudents()).as("should return empty set if neither student or team has been set").isEmpty();

        User student1 = new User();
        student1.setId(1L);
        individualPlagiarismCase.setStudent(student1);

        assertThat(individualPlagiarismCase.getStudents()).as("should return the student in a set if it is an individual plagiarism case").isEqualTo(Set.of(student1));

        User student2 = new User();
        student2.setId(2L);

        Team team = new Team();
        Set<User> teamStudents = Set.of(student1, student2);
        team.setStudents(teamStudents);

        var teamPlagiarismCase = new PlagiarismCase();
        teamPlagiarismCase.setTeam(team);

        assertThat(teamPlagiarismCase.getStudents()).as("should get the set of all students in the team if it is a team plagiarism case").isEqualTo(teamStudents);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNumberOfPlagiarismCasesForExercise_instructor_correct() throws Exception {
        var cases = request.get("/api/plagiarism/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-cases-count", HttpStatus.OK, Long.class);
        assertThat(cases).isEqualTo(5);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNumberOfPlagiarismResultsForExercise_tutor_forbidden() throws Exception {
        request.get("/api/plagiarism/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-cases-count", HttpStatus.FORBIDDEN, Long.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNumberOfPlagiarismResultsForExercise_instructorNotInCourse_forbidden() throws Exception {
        courseUtilService.updateCourseGroups("abc", course, "");
        request.get("/api/plagiarism/courses/" + course.getId() + "/exercises/" + textExercise.getId() + "/plagiarism-cases-count", HttpStatus.FORBIDDEN, Long.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }
}
