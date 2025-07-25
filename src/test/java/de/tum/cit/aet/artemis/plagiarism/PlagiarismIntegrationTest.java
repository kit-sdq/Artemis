package de.tum.cit.aet.artemis.plagiarism;

import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.CONFIRMED;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.DENIED;
import static de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus.NONE;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismComparisonStatusDTO;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class PlagiarismIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "plagiarismintegration";

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PlagiarismResultRepository plagiarismResultRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private Course course;

    private TextExercise textExercise;

    private PlagiarismResult textPlagiarismResult;

    private PlagiarismComparison plagiarismComparison1;

    private PlagiarismComparison plagiarismComparison2;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 3, 1, 1, 1);
        course = textExerciseUtilService.addCourseWithOneFinishedTextExercise();
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course.getId()).getFirst();
        textPlagiarismResult = textExerciseUtilService.createPlagiarismResultForExercise(textExercise);
        var textSubmission = ParticipationFactory.generateTextSubmission("", Language.GERMAN, true);

        var submission1 = participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student1");
        var submission2 = participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student2");
        var submission3 = participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student3");
        plagiarismComparison1 = new PlagiarismComparison();
        plagiarismComparison1.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison1.setStatus(CONFIRMED);
        var plagiarismSubmissionA1 = new PlagiarismSubmission();
        plagiarismSubmissionA1.setStudentLogin(TEST_PREFIX + "student1");
        plagiarismSubmissionA1.setSubmissionId(submission1.getId());
        var plagiarismSubmissionB1 = new PlagiarismSubmission();
        plagiarismSubmissionB1.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionB1.setSubmissionId(submission2.getId());
        plagiarismComparison1.setSubmissionA(plagiarismSubmissionA1);
        plagiarismComparison1.setSubmissionB(plagiarismSubmissionB1);
        plagiarismComparison1 = plagiarismComparisonRepository.save(plagiarismComparison1);

        plagiarismComparison2 = new PlagiarismComparison();
        plagiarismComparison2.setPlagiarismResult(textPlagiarismResult);
        plagiarismComparison2.setStatus(NONE);
        var plagiarismSubmissionA2 = new PlagiarismSubmission();
        plagiarismSubmissionA2.setStudentLogin(TEST_PREFIX + "student2");
        plagiarismSubmissionA2.setSubmissionId(submission2.getId());
        var plagiarismSubmissionB2 = new PlagiarismSubmission();
        plagiarismSubmissionB2.setStudentLogin(TEST_PREFIX + "student3");
        plagiarismSubmissionB2.setSubmissionId(submission3.getId());
        plagiarismComparison2.setSubmissionA(plagiarismSubmissionA2);
        plagiarismComparison2.setSubmissionB(plagiarismSubmissionB2);
        plagiarismComparison2 = plagiarismComparisonRepository.save(plagiarismComparison2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testUpdatePlagiarismComparisonStatus_forbidden_student() throws Exception {
        request.put("/api/plagiarism/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(NONE), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testUpdatePlagiarismComparisonStatus_forbidden_tutor() throws Exception {
        request.put("/api/plagiarism/courses/1/plagiarism-comparisons/1/status", new PlagiarismComparisonStatusDTO(NONE), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdatePlagiarismComparisonStatus_badRequest_teamExercise() throws Exception {
        textExercise.setMode(ExerciseMode.TEAM);
        textExercise = textExerciseRepository.save(textExercise);

        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", new PlagiarismComparisonStatusDTO(NONE),
                HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testUpdatePlagiarismComparisonStatus() throws Exception {
        var plagiarismComparisonStatusDTOConfirmed1 = new PlagiarismComparisonStatusDTO(CONFIRMED);
        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", plagiarismComparisonStatusDTOConfirmed1,
                HttpStatus.OK);
        var updatedComparisonConfirmed = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison1.getId());
        assertThat(updatedComparisonConfirmed.getStatus()).as("should update plagiarism comparison status").isEqualTo(CONFIRMED);
        Optional<PlagiarismCase> plagiarismCaseOptionalPresent = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalPresent).as("should create new plagiarism case").isPresent();

        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison2.getId() + "/status",
                new PlagiarismComparisonStatusDTO(CONFIRMED), HttpStatus.OK);
        var updatedComparisonConfirmed2 = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison2.getId());
        assertThat(updatedComparisonConfirmed2.getStatus()).as("should update plagiarism comparison status").isEqualTo(CONFIRMED);
        Optional<PlagiarismCase> plagiarismCaseOptionalPresent2 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalPresent2).as("should add to existing plagiarism case").isPresent();

        request.put("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/status", new PlagiarismComparisonStatusDTO(DENIED),
                HttpStatus.OK);
        var updatedComparisonDenied = plagiarismComparisonRepository.findByIdWithSubmissionsStudentsElseThrow(plagiarismComparison1.getId());
        assertThat(updatedComparisonDenied.getStatus()).as("should update plagiarism comparison status").isEqualTo(DENIED);

        Optional<PlagiarismCase> plagiarismCaseOptionalEmpty1 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student1",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalEmpty1).as("should remove plagiarism case for student 1").isEmpty();

        Optional<PlagiarismCase> plagiarismCaseOptionalEmpty2 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student2",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalEmpty2).as("should NOT remove plagiarism case for student2").isPresent();

        Optional<PlagiarismCase> plagiarismCaseOptionalEmpty3 = plagiarismCaseRepository.findByStudentLoginAndExerciseIdWithPlagiarismSubmissions(TEST_PREFIX + "student3",
                textExercise.getId());
        assertThat(plagiarismCaseOptionalEmpty3).as("should NOT remove plagiarism case for student 3").isPresent();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetPlagiarismComparisonsForSplitView_student() throws Exception {
        var comparison = request.get("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view", HttpStatus.OK,
                plagiarismComparison1.getClass());
        assertThat(comparison.getSubmissionA().getStudentLogin()).as("should anonymize plagiarism comparison").isIn("Your submission", "Other submission");
        assertThat(comparison.getSubmissionB().getStudentLogin()).as("should anonymize plagiarism comparison").isIn("Your submission", "Other submission");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void testGetPlagiarismComparisonsForSplitView_student_forbidden() throws Exception {
        request.get("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view", HttpStatus.FORBIDDEN,
                plagiarismComparison1.getClass());

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testGetPlagiarismComparisonsForSplitView_editor() throws Exception {
        PlagiarismComparison comparison = request.get("/api/plagiarism/courses/" + course.getId() + "/plagiarism-comparisons/" + plagiarismComparison1.getId() + "/for-split-view",
                HttpStatus.OK, plagiarismComparison1.getClass());
        assertThat(comparison).isEqualTo(plagiarismComparison1);
        assertThat(comparison.getPlagiarismResult()).isNull();
        assertThat(comparison.getSubmissionA()).isEqualTo(plagiarismComparison1.getSubmissionA());
        assertThat(comparison.getSubmissionB()).isEqualTo(plagiarismComparison1.getSubmissionB());
        assertThat(comparison.getSimilarity()).isEqualTo(plagiarismComparison1.getSimilarity());
        assertThat(comparison.getStatus()).isEqualTo(plagiarismComparison1.getStatus());
        assertThat(comparison.getMatches()).isEqualTo(plagiarismComparison1.getMatches());

        // Important: make sure those additional information is hidden
        assertThat(comparison.getSubmissionA().getPlagiarismCase()).isNull();
        assertThat(comparison.getSubmissionB().getPlagiarismCase()).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testDeletePlagiarismComparisons_student() throws Exception {
        request.delete("/api/plagiarism/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testDeletePlagiarismComparisons_tutor() throws Exception {
        request.delete("/api/plagiarism/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "editor1", roles = "EDITOR")
    void testDeletePlagiarismComparisons_editor() throws Exception {
        request.delete("/api/plagiarism/exercises/1/plagiarism-results/1/plagiarism-comparisons?deleteAll=false", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeletePlagiarismComparisons_instructor() throws Exception {
        request.delete("/api/plagiarism/exercises/" + textExercise.getId() + "/plagiarism-results/" + textPlagiarismResult.getId() + "/plagiarism-comparisons?deleteAll=false",
                HttpStatus.OK);
        var result = plagiarismResultRepository.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        assertThat(result).isNotNull();
        assertThat(result.getComparisons()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testDeletePlagiarismComparisons_instructor_deleteAll() throws Exception {
        request.delete("/api/plagiarism/exercises/" + textExercise.getId() + "/plagiarism-results/" + textPlagiarismResult.getId() + "/plagiarism-comparisons?deleteAll=true",
                HttpStatus.OK);
        var result = plagiarismResultRepository.findFirstWithComparisonsByExerciseIdOrderByLastModifiedDateDescOrNull(textExercise.getId());
        assertThat(result).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNumberOfPlagiarismResultsForExercise_instructor_correct() throws Exception {
        var results = request.get("/api/plagiarism/exercises/" + textExercise.getId() + "/potential-plagiarism-count", HttpStatus.OK, Long.class);
        assertThat(results).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testNumberOfPlagiarismResultsForExercise_tutor_forbidden() throws Exception {
        request.get("/api/plagiarism/exercises/" + textExercise.getId() + "/potential-plagiarism-count", HttpStatus.FORBIDDEN, Long.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testNumberOfPlagiarismResultsForExercise_instructorNotInCourse_forbidden() throws Exception {
        courseUtilService.updateCourseGroups("abc", course, "");
        request.get("/api/plagiarism/exercises/" + textExercise.getId() + "/potential-plagiarism-count", HttpStatus.FORBIDDEN, Long.class);
        courseUtilService.updateCourseGroups(TEST_PREFIX, course, "");
    }
}
