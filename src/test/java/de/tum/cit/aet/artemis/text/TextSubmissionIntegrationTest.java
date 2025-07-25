package de.tum.cit.aet.artemis.text;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.test_repository.PostTestRepository;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.Language;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismCase;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismSubmission;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismCaseRepository;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismComparisonRepository;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.test_repository.TextSubmissionTestRepository;
import de.tum.cit.aet.artemis.text.util.TextExerciseFactory;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

class TextSubmissionIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "textsubmissionintegration";

    @Autowired
    private TextSubmissionTestRepository testSubmissionTestRepository;

    @Autowired
    private SubmissionVersionRepository submissionVersionRepository;

    @Autowired
    private StudentParticipationTestRepository participationRepository;

    @Autowired
    private PlagiarismComparisonRepository plagiarismComparisonRepository;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private PlagiarismCaseRepository plagiarismCaseRepository;

    @Autowired
    private PostTestRepository postRepository;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    private TextExercise finishedTextExercise;

    private TextExercise releasedTextExercise;

    private TextSubmission textSubmission;

    private TextSubmission lateTextSubmission;

    private TextSubmission notSubmittedTextSubmission;

    private StudentParticipation lateParticipation;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        Course course1 = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Course course2 = textExerciseUtilService.addCourseWithOneFinishedTextExercise();
        releasedTextExercise = ExerciseUtilService.findTextExerciseWithTitle(course1.getExercises(), "Text");
        finishedTextExercise = ExerciseUtilService.findTextExerciseWithTitle(course2.getExercises(), "Finished");
        lateParticipation = participationUtilService.createAndSaveParticipationForExercise(finishedTextExercise, TEST_PREFIX + "student1");
        lateParticipation.setInitializationDate(ZonedDateTime.now().minusDays(2));
        participationRepository.save(lateParticipation);
        participationUtilService.createAndSaveParticipationForExercise(releasedTextExercise, TEST_PREFIX + "student1");

        textSubmission = ParticipationFactory.generateTextSubmission("example text", Language.ENGLISH, true);
        lateTextSubmission = ParticipationFactory.generateLateTextSubmission("example text 2", Language.ENGLISH);
        notSubmittedTextSubmission = ParticipationFactory.generateTextSubmission("example text 2", Language.ENGLISH, false);

        // Add users that are not in exercise/course
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor2");
        userUtilService.createAndSaveUser(TEST_PREFIX + "student3");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3")
    void testRepositoryMethods() {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> testSubmissionTestRepository.findByIdWithParticipationExerciseResultAssessorElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> testSubmissionTestRepository.findByIdWithEagerResultsAndFeedbackAndTextBlocksElseThrow(Long.MAX_VALUE));

        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> testSubmissionTestRepository.getTextSubmissionWithResultAndTextBlocksAndFeedbackByResultIdElseThrow(Long.MAX_VALUE));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextSubmissionWithResult() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        participationUtilService.addResultToSubmission(textSubmission, AssessmentType.MANUAL);

        TextSubmission textSubmission = request.get("/api/text/text-submissions/" + this.textSubmission.getId(), HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmission).as("text submission without assessment was found").isNotNull();
        assertThat(textSubmission.getId()).as("correct text submission was found").isEqualTo(this.textSubmission.getId());
        assertThat(textSubmission.getText()).as("text of text submission is correct").isEqualTo(this.textSubmission.getText());
        assertThat(textSubmission.getResults()).as("results are not loaded properly").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTextSubmissionWithResult_involved_allowed() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        PlagiarismComparison plagiarismComparison = new PlagiarismComparison();
        PlagiarismSubmission submissionA = new PlagiarismSubmission();
        submissionA.setStudentLogin(TEST_PREFIX + "student1");
        submissionA.setSubmissionId(this.textSubmission.getId());
        plagiarismComparison.setSubmissionA(submissionA);
        PlagiarismCase plagiarismCase = new PlagiarismCase();
        plagiarismCase.setExercise(finishedTextExercise);
        plagiarismCase = plagiarismCaseRepository.save(plagiarismCase);
        Post post = new Post();
        post.setAuthor(userTestRepository.getUserByLoginElseThrow(TEST_PREFIX + "instructor1"));
        post.setTitle("Title Plagiarism Case Post");
        post.setContent("Content Plagiarism Case Post");
        post.setVisibleForStudents(true);
        post.setPlagiarismCase(plagiarismCase);
        postRepository.save(post);
        submissionA.setPlagiarismCase(plagiarismCase);
        plagiarismComparisonRepository.save(plagiarismComparison);

        var submission = request.get("/api/text/text-submissions/" + this.textSubmission.getId(), HttpStatus.OK, TextSubmission.class);

        assertThat(submission.getParticipation()).as("Should anonymize participation").isNull();
        assertThat(submission.getResults()).as("Should anonymize results").isEmpty();
        assertThat(submission.getSubmissionDate()).as("Should anonymize submission date").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getTextSubmissionWithResult_notInvolved_notAllowed() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        request.get("/api/text/text-submissions/" + this.textSubmission.getId(), HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllTextSubmissions_studentHiddenForTutor() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        List<TextSubmission> textSubmissions = request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissions).as("one text submission was found").hasSize(1);
        assertThat(textSubmissions.getFirst().getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissions.getFirst().getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getAllTextSubmissions_studentVisibleForInstructor() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");

        List<TextSubmission> textSubmissions = request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions", HttpStatus.OK, TextSubmission.class);

        assertThat(textSubmissions).as("one text submission was found").hasSize(1);
        assertThat(textSubmissions.getFirst().getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissions.getFirst().getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isNotEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getAllTextSubmissions_assessedByTutorForStudent() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getAllTextSubmissions_notAssessedByTutorForTutor() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getAllTextSubmission_notTutorInExercise() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        request.getList("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions?assessedByTutor=true", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_studentHidden() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");

        TextSubmission textSubmissionWithoutAssessment = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(textSubmissionWithoutAssessment).as("text submission without assessment was found").isNotNull();
        assertThat(textSubmissionWithoutAssessment.getId()).as("correct text submission was found").isEqualTo(textSubmission.getId());
        assertThat(((StudentParticipation) textSubmissionWithoutAssessment.getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_lockSubmission() throws Exception {
        User user = userUtilService.getUserByLogin(TEST_PREFIX + "tutor1");
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");

        TextSubmission storedSubmission = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment?lock=true", HttpStatus.OK,
                TextSubmission.class);

        final String[] ignoringFields = { "results", "submissionDate", "blocks", "participation" };
        assertThat(storedSubmission).as("submission was found").usingRecursiveComparison().ignoringFields(ignoringFields).isEqualTo(textSubmission);
        assertThat(storedSubmission.getSubmissionDate()).as("submission date is correct").isCloseTo(textSubmission.getSubmissionDate(), HalfSecond());
        assertThat(storedSubmission.getLatestResult()).as("result is set").isNotNull();
        assertThat(storedSubmission.getLatestResult().getAssessor()).as("assessor is tutor1").isEqualTo(user);
        checkDetailsHidden(storedSubmission, false);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_selectInTime() throws Exception {

        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        lateTextSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, lateTextSubmission, TEST_PREFIX + "student2");

        assertThat(textSubmission.getSubmissionDate()).as("first submission is in-time").isBefore(finishedTextExercise.getDueDate());
        assertThat(lateTextSubmission.getSubmissionDate()).as("second submission is late").isAfter(finishedTextExercise.getDueDate());

        TextSubmission storedSubmission = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK,
                TextSubmission.class);

        assertThat(storedSubmission).as("text submission without assessment was found").isNotNull();
        assertThat(storedSubmission.getId()).as("in-time text submission was found").isEqualTo(textSubmission.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_noSubmittedSubmission_null() throws Exception {
        TextSubmission submission = ParticipationFactory.generateTextSubmission("text", Language.ENGLISH, false);
        textExerciseUtilService.saveTextSubmission(finishedTextExercise, submission, TEST_PREFIX + "student1");

        var response = request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.OK, TextSubmission.class);
        assertThat(response).isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor2", roles = "TA")
    void getTextSubmissionWithoutAssessment_notTutorInExercise() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");
        request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void getTextSubmissionWithoutAssessment_dueDateNotOver() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(releasedTextExercise, textSubmission, TEST_PREFIX + "student1");

        request.get("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getTextSubmissionWithoutAssessment_asStudent_forbidden() throws Exception {
        textSubmission = textExerciseUtilService.saveTextSubmission(finishedTextExercise, textSubmission, TEST_PREFIX + "student1");

        request.get("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submission-without-assessment", HttpStatus.FORBIDDEN, TextSubmission.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1")
    void getResultsForCurrentStudent_assessorHiddenForStudent() throws Exception {
        textSubmission = ParticipationFactory.generateTextSubmission("Some text", Language.ENGLISH, true);
        textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, textSubmission, TEST_PREFIX + "student1", TEST_PREFIX + "tutor1");

        ExerciseDetailsDTO returnedExerciseDetails = request.get("/api/exercise/exercises/" + finishedTextExercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
        StudentParticipation studentParticipation = returnedExerciseDetails.exercise().getStudentParticipations().iterator().next();
        assertThat(participationUtilService.getResultsForParticipation(studentParticipation).iterator().next().getAssessor()).as("assessor is null").isNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getDataForTextEditorWithResult() throws Exception {
        TextSubmission textSubmission = textExerciseUtilService.saveTextSubmissionWithResultAndAssessor(finishedTextExercise, this.textSubmission, TEST_PREFIX + "student1",
                TEST_PREFIX + "tutor1");
        Long participationId = textSubmission.getParticipation().getId();

        StudentParticipation participation = request.get("/api/text/text-editor/" + participationId, HttpStatus.OK, StudentParticipation.class);

        Set<Result> results = participationUtilService.getResultsForParticipation(participation);
        assertThat(results).isNotNull();
        assertThat(results).hasSize(1);

        assertThat(participation.getSubmissions()).isNotNull();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_afterDueDate_forbidden() throws Exception {
        request.put("/api/text/exercises/" + finishedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_beforeDueDate_isTeamMode() throws Exception {
        releasedTextExercise.setMode(ExerciseMode.TEAM);
        exerciseRepository.save(releasedTextExercise);
        Team team = new Team();
        team.setName("Team");
        team.setShortName("team");
        team.setExercise(releasedTextExercise);
        team.addStudents(userTestRepository.findOneByLogin(TEST_PREFIX + "student1").orElseThrow());
        team.addStudents(userTestRepository.findOneByLogin(TEST_PREFIX + "student2").orElseThrow());
        teamRepository.save(releasedTextExercise, team);

        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(releasedTextExercise, team.getId());
        releasedTextExercise.setStudentParticipations(Set.of(participation));

        TextSubmission submission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.OK);

        userUtilService.changeUser(TEST_PREFIX + "student1");
        Optional<SubmissionVersion> version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student1");
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        userUtilService.changeUser(TEST_PREFIX + "student2");
        submission.setText(submission.getText() + " Extra contribution.");
        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", submission, HttpStatus.OK);

        // create new submission to simulate other teams working at the same time
        request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.OK);

        userUtilService.changeUser(TEST_PREFIX + "student2");
        version = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(version).as("submission version was created").isNotEmpty();
        assertThat(version.orElseThrow().getAuthor().getLogin()).as("submission version has correct author").isEqualTo(TEST_PREFIX + "student2");
        assertThat(version.get().getContent()).as("submission version has correct content").isEqualTo(submission.getText());

        submission.setText(submission.getText() + " Even more.");
        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", submission, HttpStatus.OK);
        userUtilService.changeUser(TEST_PREFIX + "student2");
        Optional<SubmissionVersion> newVersion = submissionVersionRepository.findLatestVersion(submission.getId());
        assertThat(newVersion.orElseThrow().getId()).as("submission version was not created").isEqualTo(version.get().getId());

        exerciseRepository.save(releasedTextExercise.participations(Set.of()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_beforeDueDate_allowed() throws Exception {
        TextSubmission submission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class,
                HttpStatus.OK);

        assertThat(submission.getSubmissionDate()).isCloseTo(ZonedDateTime.now(), within(500, ChronoUnit.MILLIS));
        assertThat(submission.getParticipation().getInitializationState()).isEqualTo(InitializationState.FINISHED);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void saveAndSubmitTextSubmission_tooLarge() throws Exception {
        // should be ok
        char[] chars = new char[(int) (Constants.MAX_SUBMISSION_TEXT_LENGTH)];
        Arrays.fill(chars, 'a');
        textSubmission.setText(new String(chars));
        request.postWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, TextSubmission.class, HttpStatus.OK);

        // should be too large
        char[] charsTooLarge = new char[(int) (Constants.MAX_SUBMISSION_TEXT_LENGTH + 1)];
        Arrays.fill(charsTooLarge, 'a');
        textSubmission.setText(new String(charsTooLarge));
        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_beforeDueDateWithTwoSubmissions_allowed() throws Exception {
        final var submitPath = "/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions";
        final var newSubmissionText = "Some other test text";
        textSubmission = request.putWithResponseBody(submitPath, textSubmission, TextSubmission.class, HttpStatus.OK);
        textSubmission.setText(newSubmissionText);
        request.put(submitPath, textSubmission, HttpStatus.OK);

        final var submissionInDb = testSubmissionTestRepository.findById(textSubmission.getId());
        assertThat(submissionInDb).isPresent();
        assertThat(submissionInDb.get().getText()).isEqualTo(newSubmissionText);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_afterDueDateWithParticipationStartAfterDueDate_allowed() throws Exception {
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        request.put("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void saveExercise_beforeDueDate() throws Exception {
        TextSubmission storedSubmission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", notSubmittedTextSubmission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void saveExercise_afterDueDateWithParticipationStartAfterDueDate() throws Exception {
        exerciseUtilService.updateExerciseDueDate(releasedTextExercise.getId(), ZonedDateTime.now().minusHours(1));
        lateParticipation.setInitializationDate(ZonedDateTime.now());
        participationRepository.save(lateParticipation);

        TextSubmission storedSubmission = request.putWithResponseBody("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", notSubmittedTextSubmission,
                TextSubmission.class, HttpStatus.OK);
        assertThat(storedSubmission.isSubmitted()).isFalse();

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student3", roles = "USER")
    void submitExercise_notStudentInCourse() throws Exception {
        request.post("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_submissionIsAlreadyCreated_badRequest() throws Exception {
        textSubmission = testSubmissionTestRepository.save(textSubmission);
        request.post("/api/text/exercises/" + releasedTextExercise.getId() + "/text-submissions", textSubmission, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void submitExercise_noExercise_badRequest() throws Exception {
        var fakeExerciseId = releasedTextExercise.getId() + 100L;
        request.post("/api/text/exercises/" + fakeExerciseId + "/text-submissions", textSubmission, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteTextSubmissionWithTextBlocks() throws Exception {
        textSubmission.setText("Lorem Ipsum dolor sit amet");
        textSubmission = textExerciseUtilService.saveTextSubmission(releasedTextExercise, textSubmission, TEST_PREFIX + "student1");
        final var blocks = Set.of(TextExerciseFactory.generateTextBlock(0, 11), TextExerciseFactory.generateTextBlock(12, 21), TextExerciseFactory.generateTextBlock(22, 26));
        textExerciseUtilService.addAndSaveTextBlocksToTextSubmission(blocks, textSubmission);

        request.delete("/api/exercise/submissions/" + textSubmission.getId(), HttpStatus.OK);
    }

    private void checkDetailsHidden(TextSubmission submission, boolean isStudent) {
        assertThat(participationUtilService.getResultsForParticipation(submission.getParticipation())).as("results are hidden in participation").isNullOrEmpty();
        if (isStudent) {
            assertThat(submission.getLatestResult()).as("result is hidden").isNull();
        }
        else {
            assertThat(((StudentParticipation) submission.getParticipation()).getStudent()).as(TEST_PREFIX + "student of participation is hidden").isEmpty();
        }
    }
}
