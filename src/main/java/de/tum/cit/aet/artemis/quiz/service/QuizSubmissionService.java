package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.exception.QuizSubmissionException;
import de.tum.cit.aet.artemis.core.util.TimeLogUtil;
import de.tum.cit.aet.artemis.exercise.domain.InitializationState;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.exercise.service.ParticipationService;
import de.tum.cit.aet.artemis.exercise.service.SubmissionVersionService;
import de.tum.cit.aet.artemis.quiz.domain.AbstractQuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.dto.participation.StudentQuizParticipationWithSolutionsDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizSubmissionService extends AbstractQuizSubmissionService<QuizSubmission> {

    private static final Logger log = LoggerFactory.getLogger(QuizSubmissionService.class);

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final ResultRepository resultRepository;

    private final QuizExerciseRepository quizExerciseRepository;

    private final ParticipationService participationService;

    private final QuizBatchService quizBatchService;

    private final QuizStatisticService quizStatisticService;

    private final StudentParticipationRepository studentParticipationRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final QuizQuestionProgressService quizQuestionProgressService;

    public QuizSubmissionService(QuizSubmissionRepository quizSubmissionRepository, ResultRepository resultRepository, SubmissionVersionService submissionVersionService,
            QuizExerciseRepository quizExerciseRepository, ParticipationService participationService, QuizBatchService quizBatchService, QuizStatisticService quizStatisticService,
            StudentParticipationRepository studentParticipationRepository, WebsocketMessagingService websocketMessagingService,
            QuizQuestionProgressService quizQuestionProgressService) {
        super(submissionVersionService);
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.resultRepository = resultRepository;
        this.quizExerciseRepository = quizExerciseRepository;
        this.participationService = participationService;
        this.quizBatchService = quizBatchService;
        this.quizStatisticService = quizStatisticService;
        this.studentParticipationRepository = studentParticipationRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.quizQuestionProgressService = quizQuestionProgressService;
    }

    /**
     * Submits a quiz submission for practice mode, calculates scores, and creates a result.
     * This method performs several steps to process a quiz submission in practice mode, including updating
     * the submission properties, calculating scores, creating a result, and updating statistics.
     * <p>
     * The process includes:
     * <p>
     * <ol>
     * <li><b>Updating Submission Properties:</b> Sets the submission as submitted, marks it as a manual submission,
     * and records the current date and time as the submission date.</li>
     * <li><b>Calculating Scores:</b> Computes the scores based on the quiz questions and updates the submission.</li>
     * <li><b>Saving Submission:</b> Saves the updated submission in the repository.</li>
     * <li><b>Creating Result:</b> Initializes a new result, associates it with the participation, sets it as unrated
     * and automatic, and records the current date and time as the completion date.</li>
     * <li><b>Saving Result:</b> Saves the newly created result in the repository.</li>
     * <li><b>Setting Result-Submission Relation:</b> Links the result to the submission and recalculates the score.</li>
     * <li><b>Updating Submission with Result:</b> Adds the result to the submission and saves it again to set the result index column.</li>
     * <li><b>Re-saving Result:</b> Saves the result again to store the calculated score.</li>
     * <li><b>Fixing Proxy Objects:</b> Reassigns the participation to the result to avoid proxy issues.</li>
     * <li><b>Recalculating Statistics:</b> Updates the quiz statistics based on the new result.</li>
     * <li><b>Saving Question Progress</b>Updates the question progress based on the result and submission.</li>
     * </ol>
     *
     * @param quizSubmission The quiz submission to be processed.
     * @param quizExercise   The quiz exercise related to the submission.
     * @param participation  The participation object associated with the quiz submission.
     * @return The created {@link Result} after processing the quiz submission.
     */
    public Result submitForPractice(QuizSubmission quizSubmission, QuizExercise quizExercise, Participation participation) {
        // update submission properties
        quizSubmission.setSubmitted(true);
        quizSubmission.setType(SubmissionType.MANUAL);
        quizSubmission.setSubmissionDate(ZonedDateTime.now());
        // calculate scores
        quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
        // save parent submission object
        quizSubmission = quizSubmissionRepository.save(quizSubmission);

        // create result
        Result result = new Result();
        result.setRated(false);
        result.setAssessmentType(AssessmentType.AUTOMATIC);
        result.setCompletionDate(ZonedDateTime.now());
        // save result
        result = resultRepository.save(result);

        // setup result - submission relation
        result.setSubmission(quizSubmission);
        // calculate score and update result accordingly
        result.evaluateQuizSubmission(quizExercise);
        quizSubmission.addResult(result);
        quizSubmission.setParticipation(participation);

        // save submission to set result index column
        quizSubmissionRepository.save(quizSubmission);

        // save result to store score
        resultRepository.save(result);

        // add result to statistics
        quizStatisticService.recalculateStatistics(quizExercise);

        // save the question progress
        if (participation instanceof StudentParticipation studentParticipation) {
            User user = studentParticipation.getStudent().orElseThrow();
            quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission, user.getId());
        }

        log.debug("submit practice quiz finished: {}", quizSubmission);
        return result;
    }

    /**
     * Calculate the results for all participations of the given quiz exercise
     *
     * @param quizExerciseId the id of the quiz exercise for which the results should be calculated
     */
    public void calculateAllResults(long quizExerciseId) {
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(quizExerciseId);
        log.info("Calculating results for quiz {}", quizExercise.getId());
        Set<StudentParticipation> participations = studentParticipationRepository.findByExerciseId(quizExercise.getId());
        associateQuizSubmissionsWithStudentParticipations(participations);

        participations.forEach(participation -> {
            participation.setExercise(quizExercise);
            Optional<Submission> quizSubmissionOptional = participation.getSubmissions().stream().findFirst();

            if (quizSubmissionOptional.isEmpty()) {
                return;
            }
            QuizSubmission quizSubmission = (QuizSubmission) quizSubmissionOptional.get();
            quizSubmission.setParticipation(participation);

            if (quizSubmission.isSubmitted()) {
                if (quizSubmission.getType() == null) {
                    quizSubmission.setType(SubmissionType.MANUAL);
                }
            }
            else if (quizExercise.isQuizEnded()) {
                quizSubmission.setSubmitted(true);
                quizSubmission.setType(SubmissionType.TIMEOUT);
                quizSubmission.setSubmissionDate(ZonedDateTime.now());
            }

            participation.setInitializationState(InitializationState.FINISHED);

            Result result = new Result();
            result.setRated(true);
            result.setAssessmentType(AssessmentType.AUTOMATIC);
            result.setCompletionDate(quizSubmission.getSubmissionDate());
            result.setSubmission(quizSubmission);

            quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
            result.evaluateQuizSubmission(quizExercise);

            quizSubmissionRepository.save(quizSubmission);
            resultRepository.save(result);
            studentParticipationRepository.save(participation);
            quizSubmission.setResults(List.of(result));

            sendQuizResultToUser(quizExerciseId, participation);

            // save the question progress
            User user = participation.getStudent().orElseThrow();
            quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission, user.getId());
        });
        quizStatisticService.recalculateStatistics(quizExercise);
        // notify users via websocket about new results for the statistics, filter out solution information
        quizExercise.filterForStatisticWebsocket();
        websocketMessagingService.sendMessage("/topic/statistic/" + quizExercise.getId(), quizExercise);
    }

    /**
     * Fetches quiz submissions for the given student participations and associates them with the participations.
     *
     * @param participations The set of student participations to associate with quiz submissions.
     */
    private void associateQuizSubmissionsWithStudentParticipations(Set<StudentParticipation> participations) {
        Set<Long> participationIds = participations.stream().map(StudentParticipation::getId).collect(Collectors.toSet());

        List<QuizSubmission> submissions = quizSubmissionRepository.findWithEagerSubmittedAnswersByParticipationIds(participationIds);

        Map<Long, Set<QuizSubmission>> submissionsByParticipationId = submissions.stream().collect(Collectors.groupingBy(s -> s.getParticipation().getId(), Collectors.toSet()));

        for (StudentParticipation participation : participations) {
            Set<QuizSubmission> quizSubmissions = submissionsByParticipationId.getOrDefault(participation.getId(), Set.of());
            Set<Submission> submissionSet = new HashSet<>(quizSubmissions);
            participation.setSubmissions(submissionSet);
        }
    }

    private void sendQuizResultToUser(long quizExerciseId, StudentParticipation participation) {
        var user = participation.getParticipantIdentifier();
        StudentQuizParticipationWithSolutionsDTO participationDTO = StudentQuizParticipationWithSolutionsDTO.of(participation);
        websocketMessagingService.sendMessageToUser(user, "/topic/exercise/" + quizExerciseId + "/participation", participationDTO);
    }

    /**
     * Saves or submits a quiz submission for a live quiz mode based on the specified parameters.
     * This method handles both saving interim quiz data and submitting final quiz responses
     * depending on the `submitted` flag. The method performs the following steps:
     * <p>
     * 1. Logs the start of the operation and determines the log message based on the `submitted` flag.
     * 2. Retrieves the quiz exercise by its ID and sets its quiz batches to null.
     * 3. Finds the existing quiz submission for the specified user and exercise.
     * 4. Checks if the existing submission is valid for live mode and throws an exception if not.
     * 5. Updates the submission references in each submitted answer to point to the new submission.
     * 6. Ensures the new submission retains critical identifiers from the existing submission.
     * 7. Sets the submission date to the current date and time.
     * 8. Finds the corresponding participation for the user and links it to the new submission.
     * 9. Saves the updated submission in the repository.
     * 10. Logs the completion of the operation with details on the duration.
     *
     * @param exerciseId     The ID of the quiz exercise.
     * @param quizSubmission The quiz submission to be saved or submitted.
     * @param userLogin      The login of the user submitting the quiz.
     * @param submitted      A boolean indicating whether the quiz is being submitted (true) or saved (false).
     * @return The saved or submitted {@link QuizSubmission}.
     * @throws QuizSubmissionException If there is an error during the quiz submission process.
     * @throws EntityNotFoundException If the quiz exercise or submission cannot be found.
     */
    public QuizSubmission saveSubmissionForLiveMode(Long exerciseId, QuizSubmission quizSubmission, String userLogin, boolean submitted) throws QuizSubmissionException {

        String logText = submitted ? "submit quiz in live mode:" : "save quiz in live mode:";

        long start = System.nanoTime();
        var quizExercise = quizExerciseRepository.findByIdElseThrow(exerciseId);
        quizExercise.setQuizBatches(null);
        // A submission always exists because the user has to start the participation before submitting, which creates a submission
        var existingSubmission = quizSubmissionRepository.findByExerciseIdAndStudentLogin(quizExercise.getId(), userLogin)
                .orElseThrow(() -> new EntityNotFoundException("Cannot find quiz submission for exercise " + exerciseId + " and user " + userLogin));
        checkSubmissionForLiveModeOrThrow(quizExercise, existingSubmission, userLogin, logText, start);

        // TODO: ideally we only save if something has changed, we can use "if (!isContentEqualTo(existingSubmission, quizSubmission))"

        // recreate pointers back to submission in each submitted answer
        for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
            submittedAnswer.setSubmission(quizSubmission);
        }
        // make sure certain values are not overridden wrongly
        quizSubmission.setId(existingSubmission.getId());
        quizSubmission.setQuizBatch(existingSubmission.getQuizBatch());

        // set submission date and link to participation
        quizSubmission.setSubmissionDate(ZonedDateTime.now());

        // make sure the participation is not overridden wrongly
        var participation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, userLogin).orElseThrow();
        quizSubmission.setParticipation(participation);
        quizSubmission = quizSubmissionRepository.save(quizSubmission);
        quizSubmission.filterForStudentsDuringQuiz();
        log.info("{} Saved quiz submission for user {} in quiz {} after {} ", logText, userLogin, exerciseId, TimeLogUtil.formatDurationFrom(start));

        return quizSubmission;
    }

    /**
     * Checks the validity of a quiz submission for live mode and throws an exception if any condition is not met.
     * This method performs several validation steps to ensure that the quiz submission process adheres to the rules
     * of the live quiz mode. The validations include:
     * <p>
     * 1. **Logging the Received Exercise**: Logs detailed information about the received quiz exercise, user, and processing time.
     * 2. **Quiz Active Status Check**: Verifies that the quiz has started and has not ended.
     * 3. **Existing Submission Check**: Ensures that the existing submission has not already been submitted.
     * 4. **Quiz Mode Check**: Differentiates checks based on the quiz mode (synchronized or other modes):
     * - For synchronized mode, ensures that the current batch allows submissions.
     * - For other modes, verifies the student's batch association and its submission status.
     * <p>
     * Additionally, there is a placeholder for potential future checks to enhance security and validation.
     *
     * @param quizExercise       The quiz exercise being validated.
     * @param existingSubmission The existing submission of the user for the quiz.
     * @param userLogin          The login of the user attempting to submit the quiz.
     * @param logText            The log text for debugging purposes.
     * @param start              The start time of the submission process for logging duration.
     * @throws QuizSubmissionException If any validation fails during the submission process.
     */
    private void checkSubmissionForLiveModeOrThrow(QuizExercise quizExercise, QuizSubmission existingSubmission, String userLogin, String logText, long start)
            throws QuizSubmissionException {
        // check if submission is still allowed
        log.debug("{}: Received quiz exercise for user {} in quiz {} in {} µs.", logText, userLogin, quizExercise.getId(), (System.nanoTime() - start) / 1000);
        if (!quizExercise.isQuizStarted() || quizExercise.isQuizEnded()) {
            throw new QuizSubmissionException("The quiz is not active");
        }

        if (existingSubmission.isSubmitted()) {
            // the old submission has not yet been processed, so don't allow a new one yet
            throw new QuizSubmissionException("You have already submitted the quiz");
        }

        if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
            // the batch exists if the quiz is active, otherwise a new inactive batch is returned
            if (!quizBatchService.getOrCreateSynchronizedQuizBatch(quizExercise).isSubmissionAllowed()) {
                throw new QuizSubmissionException("The quiz is not active");
            }

        }
        else {
            // in the other modes the resubmission checks are done at join time and the student-batch association is removed when processing a submission
            var batch = quizBatchService.getQuizBatchForStudentByLogin(quizExercise, userLogin);

            // there is no way of distinguishing these two error cases without an extra db query
            if (batch.isEmpty()) {
                throw new QuizSubmissionException("You did not join or have already submitted the quiz");
            }

            if (!batch.get().isSubmissionAllowed()) {
                throw new QuizSubmissionException("The quiz is not active");
            }
        }

        // TODO: add additional checks that may be beneficial
        // for example it is possible for students that are not members of the course to submit the quiz
        // but for performance reasons the checks may have to be done in the quiz submission service where no feedback for the students can be generated
    }

    /**
     * Returns true if student has submitted at least once for the given quiz batch
     *
     * @param quizBatch the quiz batch of interest to check if submission exists
     * @param login     the student of interest to check if submission exists
     * @return boolean the submission status of student for the given quiz batch
     */
    public boolean hasUserSubmitted(QuizBatch quizBatch, String login) {
        Set<QuizSubmission> submissions = quizSubmissionRepository.findAllByQuizBatchAndStudentLogin(quizBatch.getId(), login);
        Optional<QuizSubmission> submission = submissions.stream().findFirst();
        return submission.map(QuizSubmission::isSubmitted).orElse(false);
    }

    /**
     * Find StudentParticipation of the given quizExercise that was done by the given user
     *
     * @param quizExercise   the QuizExercise of which the StudentParticipation belongs to
     * @param quizSubmission the AbstractQuizSubmission of which the participation to be set to
     * @param user           the User of the StudentParticipation
     * @return StudentParticipation the participation if exists, otherwise throw entity not found exception
     */
    protected StudentParticipation getParticipation(QuizExercise quizExercise, AbstractQuizSubmission quizSubmission, User user) {
        Optional<StudentParticipation> optionalParticipation = participationService.findOneByExerciseAndStudentLoginAnyState(quizExercise, user.getLogin());

        if (optionalParticipation.isEmpty()) {
            log.warn("The participation for quiz exercise {}, quiz submission {} and user {} was not found", quizExercise.getId(), quizSubmission.getId(), user.getLogin());
            // TODO: think of better way to handle failure
            throw new EntityNotFoundException("Participation for quiz exercise " + quizExercise.getId() + " and quiz submission " + quizSubmission.getId() + " for user "
                    + user.getLogin() + " was not found!");
        }
        return optionalParticipation.get();
    }

    /**
     * Set the participation of the quiz submission and then save the quiz submission to the database
     *
     * @param quizExercise   the QuizExercise of the participation of which the given quizSubmission belongs to
     * @param quizSubmission the QuizSubmission to be saved
     * @param user           the User of the participation of which the given quizSubmission belongs to
     * @return saved QuizSubmission
     */
    @Override
    protected QuizSubmission save(QuizExercise quizExercise, QuizSubmission quizSubmission, User user) {
        quizSubmission.setParticipation(this.getParticipation(quizExercise, quizSubmission, user));
        var savedQuizSubmission = quizSubmissionRepository.save(quizSubmission);
        savedQuizSubmission.filterForStudentsDuringQuiz();
        return savedQuizSubmission;
    }

    /**
     * Submits a quiz question for training mode, calculates scores and creates a result.
     *
     * @param quizSubmission the quiz submission to be submitted for training
     * @param quizExercise   the quiz exercise associated with the submission
     * @param user           the user who is submitting the quiz
     * @return result the result of the quiz submission
     */
    public Result submitForTraining(QuizSubmission quizSubmission, QuizExercise quizExercise, User user) {
        // update submission properties
        quizSubmission.setSubmitted(true);
        quizSubmission.setSubmissionDate(ZonedDateTime.now());
        // calculate scores
        quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());

        // create result
        Result result = new Result();
        result.setSubmission(quizSubmission);

        // save the question progress
        quizQuestionProgressService.retrieveProgressFromResultAndSubmission(quizExercise, quizSubmission, user.getId());

        log.debug("submit training quiz finished: {}", quizSubmission);
        return result;
    }
}
