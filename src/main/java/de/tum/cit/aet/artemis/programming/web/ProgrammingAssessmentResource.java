package de.tum.cit.aet.artemis.programming.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.Comparator;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.tum.cit.aet.artemis.assessment.domain.FeedbackType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.AssessmentUpdateDTO;
import de.tum.cit.aet.artemis.assessment.repository.ExampleSubmissionRepository;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.web.AssessmentResource;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingSubmissionRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingAssessmentService;

/**
 * REST controller for managing ProgrammingAssessment.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/programming/")
public class ProgrammingAssessmentResource extends AssessmentResource {

    private static final String ENTITY_NAME = "programmingAssessment";

    private static final Logger log = LoggerFactory.getLogger(ProgrammingAssessmentResource.class);

    private final ProgrammingAssessmentService programmingAssessmentService;

    private final ProgrammingSubmissionRepository programmingSubmissionRepository;

    private final StudentParticipationRepository studentParticipationRepository;

    public ProgrammingAssessmentResource(AuthorizationCheckService authCheckService, UserRepository userRepository, ProgrammingAssessmentService programmingAssessmentService,
            ProgrammingSubmissionRepository programmingSubmissionRepository, ExerciseRepository exerciseRepository, ResultRepository resultRepository,
            StudentParticipationRepository studentParticipationRepository, ExampleSubmissionRepository exampleSubmissionRepository, SubmissionRepository submissionRepository) {
        super(authCheckService, userRepository, exerciseRepository, programmingAssessmentService, resultRepository, exampleSubmissionRepository, submissionRepository);
        this.programmingAssessmentService = programmingAssessmentService;
        this.programmingSubmissionRepository = programmingSubmissionRepository;
        this.studentParticipationRepository = studentParticipationRepository;
    }

    /**
     * Update an assessment after a complaint was accepted.
     *
     * @param submissionId     the id of the submission for which the assessment should be updated
     * @param assessmentUpdate the programing assessment update
     * @return the updated result
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("programming-submissions/{submissionId}/assessment-after-complaint")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> updateProgrammingManualResultAfterComplaint(@RequestBody AssessmentUpdateDTO assessmentUpdate, @PathVariable long submissionId) {
        log.debug("REST request to update the assessment of manual result for submission {} after complaint.", submissionId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        ProgrammingSubmission programmingSubmission = programmingSubmissionRepository.findByIdWithResultsFeedbacksAssessorTestCases(submissionId);
        ProgrammingExercise programmingExercise = (ProgrammingExercise) programmingSubmission.getParticipation().getExercise();
        checkAuthorization(programmingExercise, user);
        if (!programmingExercise.areManualResultsAllowed()) {
            throw new AccessForbiddenException();
        }

        Result result = programmingAssessmentService.updateAssessmentAfterComplaint(programmingSubmission.getLatestResult(), programmingExercise, assessmentUpdate);
        // make sure the submission is reconnected with the result to prevent problems when the object is used for other calls in the client
        result.setSubmission(programmingSubmission);

        if (result.getSubmission().getParticipation() != null && result.getSubmission().getParticipation() instanceof StudentParticipation studentParticipation
                && !authCheckService.isAtLeastInstructorForExercise(programmingExercise, user)) {
            studentParticipation.filterSensitiveInformation();
        }

        return ResponseEntity.ok(result);
    }

    /**
     * Cancel an assessment of a given submission for the current user, i.e. delete the corresponding result / release the lock. Then the submission is available for assessment
     * again.
     *
     * @param submissionId the id of the submission for which the current assessment should be canceled
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not the assessor of the submission
     */
    @PutMapping("programming-submissions/{submissionId}/cancel-assessment")
    @EnforceAtLeastTutor
    public ResponseEntity<Void> cancelAssessment(@PathVariable Long submissionId) {
        return super.cancelAssessment(submissionId);
    }

    /**
     * Save or submit feedback for programming exercise.
     *
     * @param participationId the id of the participation that should be sent to the client
     * @param submit          defines if assessment is submitted or saved
     * @param newManualResult result with list of feedbacks to be saved to the database
     * @return the result saved to the database
     */
    @ResponseStatus(HttpStatus.OK)
    @PutMapping("participations/{participationId}/manual-results")
    @EnforceAtLeastTutor
    public ResponseEntity<Result> saveProgrammingAssessment(@PathVariable Long participationId, @RequestParam(value = "submit", defaultValue = "false") boolean submit,
            @RequestBody Result newManualResult) {
        log.debug("REST request to save a new result : {}", newManualResult);
        final var participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);

        User user = userRepository.getUserWithGroupsAndAuthorities();

        // based on the locking mechanism we take the most recent manual result
        Result existingManualResult = participation.getSubmissions().stream()
                .flatMap(submission -> submission.getResults().stream().filter(Objects::nonNull).filter(Result::isManual)).max(Comparator.comparing(Result::getId))
                .orElseThrow(() -> new EntityNotFoundException("Manual result for participation with id " + participationId + " does not exist"));

        // prevent that tutors create multiple manual results
        newManualResult.setId(existingManualResult.getId());
        // load assessor
        existingManualResult = resultRepository.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(existingManualResult.getId());

        newManualResult.setSubmission(existingManualResult.getSubmission());

        var programmingExercise = (ProgrammingExercise) participation.getExercise();
        checkAuthorization(programmingExercise, user);

        final boolean isAtLeastInstructor = authCheckService.isAtLeastInstructorForExercise(programmingExercise, user);
        if (!assessmentService.isAllowedToCreateOrOverrideResult(existingManualResult, programmingExercise, participation, user, isAtLeastInstructor)) {
            log.debug("The user {} is not allowed to override the assessment for the participation {} for User {}", user.getLogin(), participation.getId(), user.getLogin());
            throw new AccessForbiddenException("The user is not allowed to override the assessment");
        }

        if (!programmingExercise.areManualResultsAllowed()) {
            throw new AccessForbiddenException("Creating manual results is disabled for this exercise!");
        }
        if (Boolean.FALSE.equals(newManualResult.isRated())) {
            throw new BadRequestAlertException("Result is not rated", ENTITY_NAME, "resultNotRated");
        }
        if (newManualResult.getScore() == null) {
            throw new BadRequestAlertException("Score is required.", ENTITY_NAME, "scoreNull");
        }
        if (newManualResult.getScore() < 100 && newManualResult.isSuccessful()) {
            throw new BadRequestAlertException("Only result with score 100% can be successful.", ENTITY_NAME, "scoreAndSuccessfulNotMatching");
        }
        // All not automatically generated result must have a detail text
        if (!newManualResult.getFeedbacks().isEmpty() && newManualResult.getFeedbacks().stream()
                .anyMatch(feedback -> feedback.getType() == FeedbackType.MANUAL_UNREFERENCED && feedback.getGradingInstruction() == null && feedback.getDetailText() == null)) {
            // if unreferenced feedback is associated with grading instruction, detail text is not needed
            throw new BadRequestAlertException("In case tutor feedback is present, a feedback detail text is mandatory.", ENTITY_NAME, "feedbackDetailTextNull");
        }
        if (!newManualResult.getFeedbacks().isEmpty() && newManualResult.getFeedbacks().stream().anyMatch(feedback -> feedback.getCredits() == null)) {
            throw new BadRequestAlertException("In case feedback is present, a feedback must contain points.", ENTITY_NAME, "feedbackCreditsNull");
        }

        newManualResult = programmingAssessmentService.saveAndSubmitManualAssessment(participation, newManualResult, existingManualResult, user, submit);
        // remove information about the student for tutors to ensure double-blind assessment
        if (!isAtLeastInstructor) {
            newManualResult.getSubmission().getParticipation().filterSensitiveInformation();
        }

        return ResponseEntity.ok(newManualResult);
    }

    /**
     * Delete an assessment of a given submission.
     *
     * @param participationId - the id of the participation to the submission
     * @param submissionId    - the id of the submission for which the current assessment should be deleted
     * @param resultId        - the id of the result which should get deleted
     * @return 200 Ok response if canceling was successful, 403 Forbidden if current user is not an instructor of the course or an admin
     */
    @Override
    @DeleteMapping("participations/{participationId}/programming-submissions/{submissionId}/results/{resultId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteAssessment(@PathVariable Long participationId, @PathVariable Long submissionId, @PathVariable Long resultId) {
        return super.deleteAssessment(participationId, submissionId, resultId);
    }

    @Override
    protected String getEntityName() {
        return ENTITY_NAME;
    }
}
