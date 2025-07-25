package de.tum.cit.aet.artemis.exercise.web;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.net.URI;
import java.net.URISyntaxException;
import java.security.Principal;
import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.eclipse.jgit.errors.LargeObjectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.audit.AuditEvent;
import org.springframework.boot.actuate.audit.AuditEventRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.json.MappingJacksonValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingScale;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.assessment.service.GradingScaleService;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenAlertException;
import de.tum.cit.aet.artemis.core.exception.AccessForbiddenException;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.exception.ConflictException;
import de.tum.cit.aet.artemis.core.exception.InternalServerErrorException;
import de.tum.cit.aet.artemis.core.exception.VersionControlException;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.core.security.Role;
import de.tum.cit.aet.artemis.core.security.allowedTools.AllowedTools;
import de.tum.cit.aet.artemis.core.security.allowedTools.ToolTokenType;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastInstructor;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastStudent;
import de.tum.cit.aet.artemis.core.security.annotations.EnforceAtLeastTutor;
import de.tum.cit.aet.artemis.core.security.annotations.enforceRoleInExercise.EnforceAtLeastStudentInExercise;
import de.tum.cit.aet.artemis.core.service.AuthorizationCheckService;
import de.tum.cit.aet.artemis.core.service.feature.Feature;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggle;
import de.tum.cit.aet.artemis.core.service.feature.FeatureToggleService;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.HeaderUtil;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participant;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseRepository;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionRepository;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.service.ExerciseDateService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationAuthorizationCheckService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.service.ModelingExerciseFeedbackService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseCodeReviewFeedbackService;
import de.tum.cit.aet.artemis.programming.service.ProgrammingExerciseParticipationService;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithSolutionsDTO;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithoutQuestionsDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.SubmittedAnswerRepository;
import de.tum.cit.aet.artemis.quiz.service.QuizBatchService;
import de.tum.cit.aet.artemis.quiz.service.QuizSubmissionService;
import de.tum.cit.aet.artemis.text.api.TextFeedbackApi;
import de.tum.cit.aet.artemis.text.config.TextApiNotPresentException;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * REST controller for managing Participation.
 */
@Profile(PROFILE_CORE)
@Lazy
@RestController
@RequestMapping("api/exercise/")
public class ParticipationResource {

    private static final Logger log = LoggerFactory.getLogger(ParticipationResource.class);

    private static final String ENTITY_NAME = "participation";

    private final ParticipationService participationService;

    private final ParticipationDeletionService participationDeletionService;

    private final ProgrammingExerciseParticipationService programmingExerciseParticipationService;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ExerciseRepository exerciseRepository;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final AuthorizationCheckService authCheckService;

    private final ParticipationAuthorizationCheckService participationAuthCheckService;

    private final UserRepository userRepository;

    private final AuditEventRepository auditEventRepository;

    private final TeamRepository teamRepository;

    private final FeatureToggleService featureToggleService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final SubmissionRepository submissionRepository;

    private final ResultRepository resultRepository;

    private final ExerciseDateService exerciseDateService;

    private final InstanceMessageSendService instanceMessageSendService;

    private final QuizBatchService quizBatchService;

    private final SubmittedAnswerRepository submittedAnswerRepository;

    private final QuizSubmissionService quizSubmissionService;

    private final GradingScaleService gradingScaleService;

    private final ProgrammingExerciseCodeReviewFeedbackService programmingExerciseCodeReviewFeedbackService;

    private final Optional<TextFeedbackApi> textFeedbackApi;

    private final ModelingExerciseFeedbackService modelingExerciseFeedbackService;

    private final Optional<StudentExamApi> studentExamApi;

    @Value("${jhipster.clientApp.name}")
    private String applicationName;

    public ParticipationResource(ParticipationService participationService, ParticipationDeletionService participationDeletionService,
            ProgrammingExerciseParticipationService programmingExerciseParticipationService, QuizExerciseRepository quizExerciseRepository, ExerciseRepository exerciseRepository,
            ProgrammingExerciseRepository programmingExerciseRepository, AuthorizationCheckService authCheckService,
            ParticipationAuthorizationCheckService participationAuthCheckService, UserRepository userRepository, StudentParticipationRepository studentParticipationRepository,
            AuditEventRepository auditEventRepository, TeamRepository teamRepository, FeatureToggleService featureToggleService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, SubmissionRepository submissionRepository,
            ResultRepository resultRepository, ExerciseDateService exerciseDateService, InstanceMessageSendService instanceMessageSendService, QuizBatchService quizBatchService,
            SubmittedAnswerRepository submittedAnswerRepository, QuizSubmissionService quizSubmissionService, GradingScaleService gradingScaleService,
            ProgrammingExerciseCodeReviewFeedbackService programmingExerciseCodeReviewFeedbackService, Optional<TextFeedbackApi> textFeedbackApi,
            ModelingExerciseFeedbackService modelingExerciseFeedbackService, Optional<StudentExamApi> studentExamApi) {
        this.participationService = participationService;
        this.participationDeletionService = participationDeletionService;
        this.programmingExerciseParticipationService = programmingExerciseParticipationService;
        this.quizExerciseRepository = quizExerciseRepository;
        this.exerciseRepository = exerciseRepository;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.authCheckService = authCheckService;
        this.participationAuthCheckService = participationAuthCheckService;
        this.userRepository = userRepository;
        this.auditEventRepository = auditEventRepository;
        this.teamRepository = teamRepository;
        this.featureToggleService = featureToggleService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.submissionRepository = submissionRepository;
        this.resultRepository = resultRepository;
        this.exerciseDateService = exerciseDateService;
        this.instanceMessageSendService = instanceMessageSendService;
        this.quizBatchService = quizBatchService;
        this.submittedAnswerRepository = submittedAnswerRepository;
        this.quizSubmissionService = quizSubmissionService;
        this.gradingScaleService = gradingScaleService;
        this.programmingExerciseCodeReviewFeedbackService = programmingExerciseCodeReviewFeedbackService;
        this.textFeedbackApi = textFeedbackApi;
        this.modelingExerciseFeedbackService = modelingExerciseFeedbackService;
        this.studentExamApi = studentExamApi;
    }

    /**
     * POST /exercises/:exerciseId/participations : start the "participationId" exercise for the current user.
     *
     * @param exerciseId the participationId of the exercise for which to init a participation
     * @return the ResponseEntity with status 201 (Created) and the participation within the body, or with status 404 (Not Found)
     * @throws URISyntaxException If the URI for the created participation could not be created
     */
    @PostMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastStudentInExercise
    @AllowedTools(ToolTokenType.SCORPIO)
    public ResponseEntity<Participation> startParticipation(@PathVariable Long exerciseId) throws URISyntaxException {
        log.debug("REST request to start Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();

        checkIfParticipationCanBeStartedElseThrow(exercise, user);

        // if this is a team-based exercise, set the participant to the team that the user belongs to
        Participant participant = user;
        if (exercise.isTeamMode()) {
            participant = teamRepository.findOneByExerciseIdAndUserId(exercise.getId(), user.getId())
                    .orElseThrow(() -> new BadRequestAlertException("Team exercise cannot be started without assigned team.", "participation", "teamExercise.cannotStart"));
        }
        StudentParticipation participation = null;
        try {
            participation = participationService.startExercise(exercise, participant, true);
        }
        catch (Exception e) {
            if (e instanceof VersionControlException && e.getCause() instanceof LargeObjectException) {
                throw new InternalServerErrorException("Failed to start exercise because repository contains files that are too large. Please contact your instructor.");
            }
            else {
                log.error("Failed to start exercise participation for exercise {} and user {}", exerciseId, user.getLogin(), e);
                throw new InternalServerErrorException("Failed to start exercise participation.");
            }
        }

        // remove sensitive information before sending participation to the client
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.created(new URI("/api/exercise/participations/" + participation.getId())).body(participation);
    }

    /**
     * POST /exercises/:exerciseId/participations : start the "participationId" exercise for the current user.
     *
     * @param exerciseId             the participationId of the exercise for which to init a participation
     * @param useGradedParticipation a flag that indicates that the student wants to use their graded participation as baseline for the new repo
     * @return the ResponseEntity with status 201 (Created) and the participation within the body, or with status 404 (Not Found)
     * @throws URISyntaxException If the URI for the created participation could not be created
     */
    @PostMapping("exercises/{exerciseId}/participations/practice")
    @EnforceAtLeastStudent
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Participation> startPracticeParticipation(@PathVariable Long exerciseId,
            @RequestParam(value = "useGradedParticipation", defaultValue = "false") boolean useGradedParticipation) throws URISyntaxException {
        log.debug("REST request to practice Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        Optional<StudentParticipation> optionalGradedStudentParticipation = participationService.findOneByExerciseAndParticipantAnyStateAndTestRun(exercise, user, false);

        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.STUDENT, exercise, user);
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("The practice mode cannot be used in an exam", ENTITY_NAME, "noPracticeModeInExam");
        }
        if (exercise.isTeamMode()) {
            throw new BadRequestAlertException("The practice mode is not yet supported for team exercises", ENTITY_NAME, "noPracticeModeForTeams");
        }
        // TODO: we should allow the practice mode for all other exercise types as well
        if (!(exercise instanceof ProgrammingExercise)) {
            throw new BadRequestAlertException("The practice can only be used for programming exercises", ENTITY_NAME, "practiceModeOnlyForProgramming");
        }
        if (exercise.getDueDate() == null || now().isBefore(exercise.getDueDate())
                || (optionalGradedStudentParticipation.isPresent() && exerciseDateService.isBeforeDueDate(optionalGradedStudentParticipation.get()))) {
            throw new AccessForbiddenException("The practice mode can only be started after the due date");
        }
        if (useGradedParticipation && optionalGradedStudentParticipation.isEmpty()) {
            throw new BadRequestAlertException("Tried to start the practice mode based on the graded participation, but there is no graded participation", ENTITY_NAME,
                    "practiceModeNoGradedParticipation");
        }

        StudentParticipation participation = participationService.startPracticeMode(exercise, user, optionalGradedStudentParticipation, useGradedParticipation);

        // remove sensitive information before sending participation to the client
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.created(new URI("/api/participations/" + participation.getId())).body(participation);
    }

    /**
     * PUT exercises/:exerciseId/resume-programming-participation: resume the participation of the current user in the given programming exercise
     *
     * @param exerciseId      of the exercise for which to resume participation
     * @param participationId of the participation that should be resumed
     * @return ResponseEntity with status 200 (OK) and with updated participation as a body, or with status 500 (Internal Server Error)
     */
    @PutMapping("exercises/{exerciseId}/resume-programming-participation/{participationId}")
    @EnforceAtLeastStudent
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<ProgrammingExerciseStudentParticipation> resumeParticipation(@PathVariable Long exerciseId, @PathVariable Long participationId) {
        log.debug("REST request to resume Exercise : {}", exerciseId);
        var programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(exerciseId);
        var participation = programmingExerciseStudentParticipationRepository.findWithTeamStudentsByIdElseThrow(participationId);
        // explicitly set the exercise here to make sure that the templateParticipation and solutionParticipation are initialized in case they should be used again
        participation.setProgrammingExercise(programmingExercise);

        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);
        if (!isAllowedToParticipateInProgrammingExercise(programmingExercise, participation)) {
            throw new AccessForbiddenException("You are not allowed to resume that participation.");
        }

        // There is a second participation of that student in the exercise that is inactive/finished now
        Optional<StudentParticipation> optionalOtherStudentParticipation = participationService.findOneByExerciseAndParticipantAnyStateAndTestRun(programmingExercise, user,
                !participation.isPracticeMode());
        if (optionalOtherStudentParticipation.isPresent()) {
            StudentParticipation otherParticipation = optionalOtherStudentParticipation.get();
            if (participation.getInitializationState() == InitializationState.INACTIVE) {
                otherParticipation.setInitializationState(InitializationState.FINISHED);
            }
            else {
                otherParticipation.setInitializationState(InitializationState.INACTIVE);
            }
            studentParticipationRepository.saveAndFlush(otherParticipation);
        }

        participation = participationService.resumeProgrammingExercise(participation);
        participation.getExercise().filterSensitiveInformation();
        return ResponseEntity.ok().body(participation);
    }

    /**
     * PUT exercises/:exerciseId/request-feedback: Requests feedback for the latest participation
     *
     * @param exerciseId of the exercise for which to resume participation
     * @param principal  current user principal
     * @return ResponseEntity with status 200 (OK)
     */
    @PutMapping("exercises/{exerciseId}/request-feedback")
    @EnforceAtLeastStudent
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<StudentParticipation> requestFeedback(@PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request for feedback request: {}", exerciseId);

        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);

        if (exercise instanceof QuizExercise || exercise instanceof FileUploadExercise) {
            throw new BadRequestAlertException("Unsupported exercise type", "participation", "unsupported type");
        }

        return handleExerciseFeedbackRequest(exercise, principal);
    }

    private ResponseEntity<StudentParticipation> handleExerciseFeedbackRequest(Exercise exercise, Principal principal) {
        // Validate exercise and timing
        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Not intended for the use in exams", "participation", "preconditions not met");
        }
        if (exercise.getDueDate() != null && now().isAfter(exercise.getDueDate())) {
            throw new BadRequestAlertException("The due date is over", "participation", "feedbackRequestAfterDueDate", true);
        }
        if (exercise instanceof ProgrammingExercise) {
            ((ProgrammingExercise) exercise).validateSettingsForFeedbackRequest();
        }

        // Get and validate participation
        User user = userRepository.getUserWithGroupsAndAuthorities();
        StudentParticipation participation = (exercise instanceof ProgrammingExercise)
                ? programmingExerciseParticipationService.findStudentParticipationByExerciseAndStudentId(exercise, principal.getName())
                : studentParticipationRepository.findByExerciseIdAndStudentLogin(exercise.getId(), principal.getName())
                        .orElseThrow(() -> new BadRequestAlertException("Submission not found", "participation", "noSubmissionExists", true));

        checkAccessPermissionOwner(participation, user);
        participation = studentParticipationRepository.findByIdWithResultsElseThrow(participation.getId());

        // Check submission requirements
        if (exercise instanceof TextExercise || exercise instanceof ModelingExercise) {
            boolean hasSubmittedOnce = submissionRepository.findAllByParticipationId(participation.getId()).stream().anyMatch(Submission::isSubmitted);
            if (!hasSubmittedOnce) {
                throw new BadRequestAlertException("You need to submit at least once", "participation", "noSubmissionExists", true);
            }
        }
        else if (exercise instanceof ProgrammingExercise) {
            if (participation.findLatestResult() == null) {
                throw new BadRequestAlertException("You need to submit at least once and have the build results", "participation", "noSubmissionExists", true);
            }
        }

        // Check if feedback has already been requested
        var latestResult = participation.findLatestResult();
        if (latestResult != null && latestResult.getAssessmentType() == AssessmentType.AUTOMATIC_ATHENA && latestResult.getCompletionDate().isAfter(now())) {
            throw new BadRequestAlertException("Request has already been sent", "participation", "feedbackRequestAlreadySent", true);
        }

        // Process feedback request
        StudentParticipation updatedParticipation = null;
        switch (exercise) {
            case TextExercise textExercise -> {
                TextFeedbackApi api = textFeedbackApi.orElseThrow(() -> new TextApiNotPresentException(TextFeedbackApi.class));
                updatedParticipation = api.handleNonGradedFeedbackRequest(participation, textExercise);
            }
            case ModelingExercise modelingExercise -> updatedParticipation = modelingExerciseFeedbackService.handleNonGradedFeedbackRequest(participation, modelingExercise);
            case ProgrammingExercise programmingExercise -> updatedParticipation = programmingExerciseCodeReviewFeedbackService.handleNonGradedFeedbackRequest(exercise.getId(),
                    (ProgrammingExerciseStudentParticipation) participation, programmingExercise);
            default -> {
            }
        }

        return ResponseEntity.ok().body(updatedParticipation);
    }

    /**
     * <p>
     * Checks if a participation can be started for the given exercise and user.
     * </p>
     * This method verifies if the participation can be started based on the due date.
     * <ul>
     * <li>Checks if the due date has passed (allows starting participations for non-programming exercises if the user might have an individual working time)</li>
     * <li>Additionally, for programming exercises, checks if the programming exercise feature is enabled</li>
     * </ul>
     *
     * @param exercise for which the participation is to be started
     * @param user     attempting to start the participation
     * @throws AccessForbiddenAlertException if the participation cannot be started due to feature restrictions or due date constraints
     */
    private void checkIfParticipationCanBeStartedElseThrow(Exercise exercise, User user) {
        // 1) Don't allow student to start before the start and release date
        ZonedDateTime releaseOrStartDate = exercise.getParticipationStartDate();
        if (releaseOrStartDate != null && releaseOrStartDate.isAfter(now())) {
            if (authCheckService.isOnlyStudentInCourse(exercise.getCourseViaExerciseGroupOrCourseMember(), user)) {
                throw new AccessForbiddenException("Students cannot start an exercise before the release date");
            }
        }
        // 2) Don't allow participations if the feature is disabled
        if (exercise instanceof ProgrammingExercise && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new AccessForbiddenException("Programming Exercise Feature is disabled.");
        }
        // 3) Don't allow to start after the (individual) end date
        ZonedDateTime exerciseDueDate = exercise.getDueDate();
        // NOTE: course exercises can only have an individual due date when they already have started
        if (exercise.isExamExercise()) {
            // NOTE: this is an absolute edge case because exam participations are generated before the exam starts and should not be started by the user
            exerciseDueDate = exercise.getExam().getEndDate();
            var studentExam = studentExamApi.orElseThrow().findByExamIdAndUserId(exercise.getExam().getId(), user.getId());
            if (studentExam.isPresent() && studentExam.get().getIndividualEndDate() != null) {
                exerciseDueDate = studentExam.get().getIndividualEndDate();
            }
        }
        boolean isDueDateInPast = exerciseDueDate != null && now().isAfter(exerciseDueDate);
        if (isDueDateInPast) {
            if (exercise instanceof ProgrammingExercise) {
                // at the moment, only programming exercises offer a dedicated practice mode
                throw new AccessForbiddenAlertException("Not allowed", ENTITY_NAME, "dueDateOver.participationInPracticeMode");
            }
            else {
                // all other exercise types are not allowed to be started after the due date
                throw new AccessForbiddenAlertException("The exercise due date is already over, you can no longer participate in this exercise.", ENTITY_NAME,
                        "dueDateOver.noParticipationPossible");
            }
        }

    }

    /**
     * Checks if the student is currently allowed to participate in the course exercise using this participation
     *
     * @param programmingExercise the exercise where the user wants to participate
     * @param participation       the participation, may be null in case there is none
     * @return a boolean indicating if the user may participate
     */
    private boolean isAllowedToParticipateInProgrammingExercise(ProgrammingExercise programmingExercise, @Nullable StudentParticipation participation) {
        if (participation != null) {
            // only regular participation before the due date; only practice run afterwards
            return participation.isPracticeMode() == exerciseDateService.isAfterDueDate(participation);
        }
        else {
            return programmingExercise.getDueDate() == null || now().isBefore(programmingExercise.getDueDate());
        }
    }

    /**
     * PUT /participations : Updates an existing participation.
     *
     * @param exerciseId    the id of the exercise, the participation belongs to
     * @param participation the participation to update
     * @return the ResponseEntity with status 200 (OK) and with body the updated participation, or with status 400 (Bad Request) if the participation is not valid, or with status
     *         500 (Internal Server Error) if the participation couldn't be updated
     */
    @PutMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastTutor
    public ResponseEntity<Participation> updateParticipation(@PathVariable long exerciseId, @RequestBody StudentParticipation participation) {
        log.debug("REST request to update Participation : {}", participation);
        if (participation.getId() == null) {
            throw new BadRequestAlertException("The participation object needs to have an id to be changed", ENTITY_NAME, "idmissing");
        }
        if (participation.getExercise() == null || participation.getExercise().getId() == null) {
            throw new BadRequestAlertException("The participation needs to be connected to an exercise", ENTITY_NAME, "exerciseidmissing");
        }
        if (participation.getExercise().getId() != exerciseId) {
            throw new ConflictException("The exercise of the participation does not match the exercise id in the URL", ENTITY_NAME, "noidmatch");
        }
        var originalParticipation = studentParticipationRepository.findByIdElseThrow(participation.getId());
        var user = userRepository.getUserWithGroupsAndAuthorities();
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, originalParticipation.getExercise(), null);

        Course course = findCourseFromParticipation(participation);
        if (participation.getPresentationScore() != null && participation.getExercise().getPresentationScoreEnabled() != null
                && participation.getExercise().getPresentationScoreEnabled()) {
            Optional<GradingScale> gradingScale = gradingScaleService.findGradingScaleByCourseId(participation.getExercise().getCourseViaExerciseGroupOrCourseMember().getId());

            // Presentation Score is only valid for non practice participations
            if (participation.isPracticeMode()) {
                throw new BadRequestAlertException("Presentation score is not allowed for practice participations", ENTITY_NAME, "presentationScoreInvalid");
            }

            // Validity of presentationScore for basic presentations
            if (course.getPresentationScore() != null && course.getPresentationScore() > 0) {
                if (participation.getPresentationScore() >= 1.) {
                    participation.setPresentationScore(1.);
                }
                else {
                    participation.setPresentationScore(null);
                }
            }
            // Validity of presentationScore for graded presentations
            if (gradingScale.isPresent() && gradingScale.get().getPresentationsNumber() != null) {
                if ((participation.getPresentationScore() > 100. || participation.getPresentationScore() < 0.)) {
                    throw new BadRequestAlertException("The presentation grade must be between 0 and 100", ENTITY_NAME, "presentationGradeInvalid");
                }

                long presentationCountForParticipant = studentParticipationRepository
                        .findByCourseIdAndStudentIdWithRelevantResult(course.getId(), participation.getParticipant().getId()).stream()
                        .filter(studentParticipation -> studentParticipation.getPresentationScore() != null && !Objects.equals(studentParticipation.getId(), participation.getId()))
                        .count();
                if (presentationCountForParticipant >= gradingScale.get().getPresentationsNumber()) {
                    throw new BadRequestAlertException("Participant already gave the maximum number of presentations", ENTITY_NAME,
                            "invalid.presentations.maxNumberOfPresentationsExceeded",
                            Map.of("name", participation.getParticipant().getName(), "presentationsNumber", gradingScale.get().getPresentationsNumber()));
                }
            }
        }
        // Validity of presentationScore for no presentations
        else {
            participation.setPresentationScore(null);
        }

        StudentParticipation currentParticipation = studentParticipationRepository.findByIdElseThrow(participation.getId());
        if (currentParticipation.getPresentationScore() != null && participation.getPresentationScore() == null || course.getPresentationScore() != null
                && currentParticipation.getPresentationScore() != null && currentParticipation.getPresentationScore() > participation.getPresentationScore()) {
            log.info("{} removed the presentation score of {} for exercise with participationId {}", user.getLogin(), originalParticipation.getParticipantIdentifier(),
                    originalParticipation.getExercise().getId());
        }

        Participation updatedParticipation = studentParticipationRepository.saveAndFlush(participation);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityUpdateAlert(applicationName, true, ENTITY_NAME, participation.getParticipant().getName()))
                .body(updatedParticipation);
    }

    /**
     * PUT /participations/update-individual-due-date : Updates the individual due dates for the given already existing participations.
     * <p>
     * If the exercise is a programming exercise, also triggers a scheduling
     * update for the participations where the individual due date has changed.
     *
     * @param exerciseId     of the exercise the participations belong to.
     * @param participations for which the individual due date should be updated.
     * @return all participations where the individual due date actually changed.
     */
    @PutMapping("exercises/{exerciseId}/participations/update-individual-due-date")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<StudentParticipation>> updateParticipationDueDates(@PathVariable long exerciseId, @RequestBody List<StudentParticipation> participations) {
        final boolean anyInvalidExerciseId = participations.stream()
                .anyMatch(participation -> participation.getExercise() == null || participation.getExercise().getId() == null || exerciseId != participation.getExercise().getId());
        if (anyInvalidExerciseId) {
            throw new BadRequestAlertException("The participation needs to be connected to an exercise", ENTITY_NAME, "exerciseidmissing");
        }

        final Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.INSTRUCTOR, exercise, null);

        if (exercise.isExamExercise()) {
            throw new BadRequestAlertException("Cannot set individual due dates for exam exercises", ENTITY_NAME, "examexercise");
        }
        if (exercise instanceof QuizExercise) {
            throw new BadRequestAlertException("Cannot set individual due dates for quiz exercises", ENTITY_NAME, "quizexercise");
        }

        final List<StudentParticipation> changedParticipations = participationService.updateIndividualDueDates(exercise, participations);
        final List<StudentParticipation> updatedParticipations = studentParticipationRepository.saveAllAndFlush(changedParticipations);

        if (!updatedParticipations.isEmpty() && exercise instanceof ProgrammingExercise programmingExercise) {
            log.info("Updating scheduling for exercise {} (id {}) due to changed individual due dates.", exercise.getTitle(), exercise.getId());
            instanceMessageSendService.sendProgrammingExerciseSchedule(programmingExercise.getId());
            List<StudentParticipation> participationsBeforeDueDate = updatedParticipations.stream().filter(exerciseDateService::isBeforeDueDate).toList();
            List<StudentParticipation> participationsAfterDueDate = updatedParticipations.stream().filter(exerciseDateService::isAfterDueDate).toList();

            if (exercise.isTeamMode()) {
                participationService.initializeTeamParticipations(participationsBeforeDueDate);
                participationService.initializeTeamParticipations(participationsAfterDueDate);
            }
        }

        return ResponseEntity.ok().body(updatedParticipations);
    }

    /**
     * GET /exercises/:exerciseId/participations : get all the participations for an exercise
     *
     * @param exerciseId        The participationId of the exercise
     * @param withLatestResults Whether the manual and latest {@link Result results} for the participations should also be fetched
     * @return A list of all participations for the exercise
     */
    @GetMapping("exercises/{exerciseId}/participations")
    @EnforceAtLeastTutor
    public ResponseEntity<Set<StudentParticipation>> getAllParticipationsForExercise(@PathVariable Long exerciseId,
            @RequestParam(defaultValue = "false") boolean withLatestResults) {
        log.debug("REST request to get all Participations for Exercise {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        authCheckService.checkHasAtLeastRoleForExerciseElseThrow(Role.TEACHING_ASSISTANT, exercise, null);
        Set<StudentParticipation> participations;
        if (withLatestResults) {
            participations = participationService.findByExerciseIdWithLatestSubmissionResultAndAssessmentNote(exercise.getId(), exercise.isTeamMode());
        }
        else {
            if (exercise.isTeamMode()) {
                participations = studentParticipationRepository.findWithTeamInformationByExerciseId(exerciseId);
            }
            else {
                participations = studentParticipationRepository.findByExerciseId(exerciseId);
            }

            Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByExerciseIdAsMap(exerciseId);
            participations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));
        }
        Map<Long, Integer> submissionCountMap = studentParticipationRepository.countSubmissionsPerParticipationByExerciseIdAsMap(exerciseId);
        participations.forEach(participation -> participation.setSubmissionCount(submissionCountMap.get(participation.getId())));
        participations = participations.stream().filter(participation -> participation.getParticipant() != null).peek(participation -> {
            // remove unnecessary data to reduce response size
            participation.setExercise(null);
        }).collect(Collectors.toSet());

        return ResponseEntity.ok(participations);
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId" including its latest result.
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}/with-latest-result")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getParticipationWithLatestResult(@PathVariable Long participationId) {
        log.debug("REST request to get Participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithResultsElseThrow(participationId);
        participationAuthCheckService.checkCanAccessParticipationElseThrow(participation);

        return new ResponseEntity<>(participation, HttpStatus.OK);
    }

    /**
     * GET /participations/:participationId : get the participation for the given "participationId".
     *
     * @param participationId the participationId of the participation to retrieve
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("participations/{participationId}")
    @EnforceAtLeastStudent
    public ResponseEntity<StudentParticipation> getParticipationForCurrentUser(@PathVariable Long participationId) {
        log.debug("REST request to get participation : {}", participationId);
        StudentParticipation participation = studentParticipationRepository.findByIdWithEagerTeamStudentsElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionOwner(participation, user);
        return new ResponseEntity<>(participation, HttpStatus.OK);
    }

    /**
     * GET /exercises/:exerciseId/participation: get the user's participation for a specific exercise. Please note: 'courseId' is only included in the call for
     * API consistency, it is not actually used
     *
     * @param exerciseId the participationId of the exercise for which to retrieve the participation
     * @param principal  The principal in form of the user's identity
     * @return the ResponseEntity with status 200 (OK) and with body the participation, or with status 404 (Not Found)
     */
    @GetMapping("exercises/{exerciseId}/participation")
    @EnforceAtLeastStudent
    // TODO: use a proper DTO (or interface here for the return type and avoid MappingJacksonValue)
    public ResponseEntity<MappingJacksonValue> getParticipationForCurrentUser(@PathVariable Long exerciseId, Principal principal) {
        log.debug("REST request to get Participation for Exercise : {}", exerciseId);
        Exercise exercise = exerciseRepository.findByIdElseThrow(exerciseId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        // if exercise is not yet released to the students they should not have any access to it
        // Exam exercise
        if (exercise.isExamExercise()) {
            // NOTE: we disable access to exam exercises over this endpoint for now, in the future we should check if there is a way to enable this
            // e.g. by checking if there is a visible exam attached and a student exam exists
            throw new AccessForbiddenException("You are not allowed to access this exam exercise");
        }
        // Course exercise
        else {
            if (!authCheckService.isAllowedToSeeCourseExercise(exercise, user)) {
                throw new AccessForbiddenException();
            }
        }
        MappingJacksonValue response;
        if (exercise instanceof QuizExercise quizExercise) {
            response = participationForQuizExercise(quizExercise, user);
        }
        else {
            Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyStateWithEagerResults(exercise, principal.getName());
            if (optionalParticipation.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.FAILED_DEPENDENCY, "No participation found for " + principal.getName() + " in exercise " + exerciseId);
            }
            Participation participation = optionalParticipation.get();
            response = new MappingJacksonValue(participation);
        }
        if (response == null) {
            return new ResponseEntity<>(HttpStatus.NO_CONTENT);
        }
        else {
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
    }

    @Nullable
    // TODO: use a proper DTO (or interface here for the return type and avoid MappingJacksonValue)
    private MappingJacksonValue participationForQuizExercise(QuizExercise quizExercise, User user) {
        // 1st case the quiz has already ended
        if (quizExercise.isQuizEnded()) {
            // quiz has ended => get participation from database and add full quizExercise
            quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
            StudentParticipation participation = participationForQuizWithSubmissionAndResult(quizExercise, user.getLogin(), null);
            if (participation == null) {
                return null;
            }

            return new MappingJacksonValue(participation);
        }
        quizExercise.setQuizBatches(null); // not available here
        var quizBatch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, user.getLogin());

        if (quizBatch.isPresent() && quizBatch.get().isStarted()) {
            // Quiz is active => construct Participation from
            // filtered quizExercise and submission from HashMap
            quizExercise = quizExerciseRepository.findByIdWithQuestionsElseThrow(quizExercise.getId());
            quizExercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));
            quizExercise.filterForStudentsDuringQuiz();
            StudentParticipation participation = participationForQuizWithSubmissionAndResult(quizExercise, user.getLogin(), quizBatch.get());

            // TODO: Duplicate
            Object responseDTO = null;
            if (participation != null) {
                var submissions = submissionRepository.findAllWithResultsByParticipationIdOrderBySubmissionDateAsc(participation.getId());
                participation.setSubmissions(new HashSet<>(submissions));
                if (quizExercise.isQuizEnded()) {
                    responseDTO = StudentQuizParticipationWithSolutionsDTO.of(participation);
                }
                else if (quizBatch.get().isStarted()) {
                    responseDTO = StudentQuizParticipationWithQuestionsDTO.of(participation);
                }
                else {
                    responseDTO = StudentQuizParticipationWithoutQuestionsDTO.of(participation);
                }
            }

            return responseDTO != null ? new MappingJacksonValue(responseDTO) : null;
        }
        else {
            // Quiz hasn't started yet => no Result, only quizExercise without questions
            quizExercise.filterSensitiveInformation();
            quizExercise.setQuizBatches(quizBatch.stream().collect(Collectors.toSet()));
            if (quizExercise.getAllowedNumberOfAttempts() != null) {
                var attempts = submissionRepository.countByExerciseIdAndStudentLogin(quizExercise.getId(), user.getLogin());
                quizExercise.setRemainingNumberOfAttempts(quizExercise.getAllowedNumberOfAttempts() - attempts);
            }
            StudentParticipation participation = new StudentParticipation().exercise(quizExercise);
            return new MappingJacksonValue(participation);
        }

    }

    /**
     * DELETE /participations/:participationId : delete the "participationId" participation. This only works for student participations - other participations should not be deleted
     * here!
     *
     * @param participationId the participationId of the participation to delete
     * @return the ResponseEntity with status 200 (OK)
     */
    @DeleteMapping("participations/{participationId}")
    @EnforceAtLeastInstructor
    public ResponseEntity<Void> deleteParticipation(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        if (participation instanceof ProgrammingExerciseParticipation && !featureToggleService.isFeatureEnabled(Feature.ProgrammingExercises)) {
            throw new AccessForbiddenException("Programming Exercise Feature is disabled.");
        }
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        return deleteParticipation(participation, user);
    }

    /**
     * delete the participation, potentially including build plan and repository and log the event in the database audit
     *
     * @param participation the participation to be deleted
     * @param user          the currently logged-in user who initiated the delete operation
     * @return the response to the client
     */
    @NotNull
    private ResponseEntity<Void> deleteParticipation(StudentParticipation participation, User user) {
        String name = participation.getParticipantName();
        var logMessage = "Delete Participation " + participation.getId() + " of exercise " + participation.getExercise().getTitle() + " for " + name + " by " + user.getLogin();
        var auditEvent = new AuditEvent(user.getLogin(), Constants.DELETE_PARTICIPATION, logMessage);
        auditEventRepository.add(auditEvent);
        log.info(logMessage);
        participationDeletionService.delete(participation.getId(), true);
        return ResponseEntity.ok().headers(HeaderUtil.createEntityDeletionAlert(applicationName, true, "participation", name)).build();
    }

    /**
     * DELETE /participations/:participationId/cleanup-build-plan : remove the build plan of the ProgrammingExerciseStudentParticipation of the "participationId".
     * This only works for programming exercises.
     *
     * @param participationId the participationId of the ProgrammingExerciseStudentParticipation for which the build plan should be removed
     * @param principal       The identity of the user accessing this resource
     * @return the ResponseEntity with status 200 (OK)
     */
    @PutMapping("participations/{participationId}/cleanup-build-plan")
    @EnforceAtLeastInstructor
    @FeatureToggle(Feature.ProgrammingExercises)
    public ResponseEntity<Participation> cleanupBuildPlan(@PathVariable Long participationId, Principal principal) {
        ProgrammingExerciseStudentParticipation participation = (ProgrammingExerciseStudentParticipation) studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        log.info("Clean up participation with build plan {} by {}", participation.getBuildPlanId(), principal.getName());
        participationDeletionService.cleanupBuildPlan(participation);
        return ResponseEntity.ok().body(participation);
    }

    private void checkAccessPermissionAtLeastInstructor(StudentParticipation participation, User user) {
        Course course = findCourseFromParticipation(participation);
        authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.INSTRUCTOR, course, user);
    }

    private void checkAccessPermissionOwner(StudentParticipation participation, User user) {
        if (!authCheckService.isOwnerOfParticipation(participation)) {
            Course course = findCourseFromParticipation(participation);
            authCheckService.checkHasAtLeastRoleInCourseElseThrow(Role.TEACHING_ASSISTANT, course, user);
        }
    }

    private Course findCourseFromParticipation(StudentParticipation participation) {
        if (participation.getExercise() != null && participation.getExercise().getCourseViaExerciseGroupOrCourseMember() != null) {
            return participation.getExercise().getCourseViaExerciseGroupOrCourseMember();
        }

        return studentParticipationRepository.findByIdElseThrow(participation.getId()).getExercise().getCourseViaExerciseGroupOrCourseMember();
    }

    /**
     * fetches all submissions of a specific participation
     *
     * @param participationId the id of the participation
     * @return all submissions that belong to the participation
     */
    @GetMapping("participations/{participationId}/submissions")
    @EnforceAtLeastInstructor
    public ResponseEntity<List<Submission>> getSubmissionsOfParticipation(@PathVariable Long participationId) {
        StudentParticipation participation = studentParticipationRepository.findByIdElseThrow(participationId);
        User user = userRepository.getUserWithGroupsAndAuthorities();
        checkAccessPermissionAtLeastInstructor(participation, user);
        List<Submission> submissions = submissionRepository.findAllWithResultsAndAssessorByParticipationId(participationId);
        return ResponseEntity.ok(submissions);
    }

    /**
     * Get a participation for the given quiz and username.
     * If the quiz hasn't ended, participation is constructed from cached submission.
     * If the quiz has ended, we first look in the database for the participation and construct one if none was found
     *
     * @param quizExercise the quiz exercise to attach to the participation
     * @param username     the username of the user that the participation belongs to
     * @param quizBatch    the quiz batch of quiz exercise which user participated in
     * @return the found or created participation with a result
     */
    // TODO: we should move this method (and others related to quizzes) into a QuizParticipationService (or similar) to make this resource independent of specific quiz exercise
    // functionality
    private StudentParticipation participationForQuizWithSubmissionAndResult(QuizExercise quizExercise, String username, QuizBatch quizBatch) {
        // try getting participation from database
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, username);

        if (quizExercise.isQuizEnded() || quizSubmissionService.hasUserSubmitted(quizBatch, username)) {

            if (optionalParticipation.isEmpty()) {
                log.error("Participation in quiz {} not found for user {}", quizExercise.getTitle(), username);
                // TODO properly handle this case
                return null;
            }
            StudentParticipation participation = optionalParticipation.get();
            // add exercise
            participation.setExercise(quizExercise);

            // add the appropriate submission and result
            Result result = resultRepository.findFirstByParticipationIdAndRatedWithSubmissionOrderByCompletionDateDesc(participation.getId(), true).orElse(null);
            if (result != null) {
                // find the submitted answers (they are NOT loaded eagerly anymore)
                var quizSubmission = (QuizSubmission) result.getSubmission();
                quizSubmission.setResults(List.of(result));
                var submittedAnswers = submittedAnswerRepository.findBySubmission(quizSubmission);
                quizSubmission.setSubmittedAnswers(submittedAnswers);
                participation.setSubmissions(Set.of(quizSubmission));
            }
            return participation;
        }

        return optionalParticipation.orElse(null);
    }

}
