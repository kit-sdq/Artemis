package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.communication.service.WebsocketMessagingService;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.StudentParticipationRepository;
import de.tum.cit.aet.artemis.lti.api.LtiApi;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionStatistic;
import de.tum.cit.aet.artemis.quiz.repository.QuizPointStatisticRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionStatisticRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizStatisticService {

    private static final Logger log = LoggerFactory.getLogger(QuizStatisticService.class);

    private final StudentParticipationRepository studentParticipationRepository;

    private final ResultRepository resultRepository;

    private final QuizPointStatisticRepository quizPointStatisticRepository;

    private final QuizQuestionStatisticRepository quizQuestionStatisticRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final WebsocketMessagingService websocketMessagingService;

    private final Optional<LtiApi> ltiApi;

    public QuizStatisticService(StudentParticipationRepository studentParticipationRepository, ResultRepository resultRepository,
            WebsocketMessagingService websocketMessagingService, QuizPointStatisticRepository quizPointStatisticRepository,
            QuizQuestionStatisticRepository quizQuestionStatisticRepository, QuizSubmissionRepository quizSubmissionRepository, Optional<LtiApi> ltiApi) {
        this.studentParticipationRepository = studentParticipationRepository;
        this.resultRepository = resultRepository;
        this.quizPointStatisticRepository = quizPointStatisticRepository;
        this.quizQuestionStatisticRepository = quizQuestionStatisticRepository;
        this.websocketMessagingService = websocketMessagingService;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.ltiApi = ltiApi;
    }

    /**
     * 1. Go through all Results in the Participation and recalculate the score
     * 2. Recalculate the statistics of the given quizExercise
     *
     * @param quizExercise the changed QuizExercise object which will be used to recalculate the existing Results and Statistics
     */
    public void recalculateStatistics(QuizExercise quizExercise) {
        // reset all statistics
        if (quizExercise.getQuizPointStatistic() != null) {
            quizExercise.getQuizPointStatistic().resetStatistic();
        }
        else {
            var quizPointStatistic = new QuizPointStatistic();
            quizExercise.setQuizPointStatistic(quizPointStatistic);
            quizPointStatistic.setQuiz(quizExercise);
            quizExercise.recalculatePointCounters();
        }
        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion.getQuizQuestionStatistic() != null) {
                quizQuestion.getQuizQuestionStatistic().resetStatistic();
            }
        }

        // add the Results in every participation of the given quizExercise to the statistics
        for (StudentParticipation participation : studentParticipationRepository.findByExerciseId(quizExercise.getId())) {
            Result latestRatedResult = null;
            Result latestUnratedResult = null;

            var results = resultRepository.findAllBySubmissionParticipationIdOrderByCompletionDateDesc(participation.getId());
            // update all Results of a participation
            for (Result result : results) {
                // find the latest rated Result
                if (Boolean.TRUE.equals(result.isRated()) && (latestRatedResult == null || latestRatedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestRatedResult = result;
                }
                // find latest unrated Result
                if (Boolean.FALSE.equals(result.isRated()) && (latestUnratedResult == null || latestUnratedResult.getCompletionDate().isBefore(result.getCompletionDate()))) {
                    latestUnratedResult = result;
                }
            }
            // update statistics with the latest rated und unrated Result
            if (latestRatedResult != null && latestRatedResult.getSubmission() != null) {
                var latestRatedSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(latestRatedResult.getSubmission().getId());
                quizExercise.addResultToAllStatistics(latestRatedResult, latestRatedSubmission);
            }
            if (latestUnratedResult != null && latestUnratedResult.getSubmission() != null) {
                var latestUnratedSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(latestUnratedResult.getSubmission().getId());
                quizExercise.addResultToAllStatistics(latestUnratedResult, latestUnratedSubmission);
            }

            ltiApi.ifPresent(api -> api.onNewResult(participation));
        }

        // save changed Statistics
        quizPointStatisticRepository.save(quizExercise.getQuizPointStatistic());
        quizPointStatisticRepository.flush();
        for (QuizQuestion quizQuestion : quizExercise.getQuizQuestions()) {
            if (quizQuestion.getQuizQuestionStatistic() != null) {
                quizQuestionStatisticRepository.save(quizQuestion.getQuizQuestionStatistic());
                quizQuestionStatisticRepository.flush();
            }
        }
    }

    /**
     * 1. check for each result if it's rated -> true: check if there is an old Result -> true: remove the old Result from the statistics 2. add new Result to the
     * quiz-point-statistic and all question-statistics
     *
     * @param results the results, which will be added to the statistics
     * @param quiz    the quizExercise with Questions where the results should contain to
     */
    public void updateStatistics(Set<Result> results, QuizExercise quiz) {

        if (results != null && quiz != null && quiz.getQuizQuestions() != null) {
            log.debug("update statistics with {} new results", results.size());

            for (Result result : results) {
                // check if the result is rated
                // NOTE: there is never an old Result if the new result is rated
                if (Boolean.FALSE.equals(result.isRated())) {
                    quiz.removeResultFromAllStatistics(getPreviousResult(result));
                }
                var quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(result.getSubmission().getId());
                quiz.addResultToAllStatistics(result, quizSubmission);
            }
            // save statistics
            quizPointStatisticRepository.save(quiz.getQuizPointStatistic());
            List<QuizQuestionStatistic> quizQuestionStatistics = new ArrayList<>();
            for (QuizQuestion quizQuestion : quiz.getQuizQuestions()) {
                if (quizQuestion.getQuizQuestionStatistic() != null) {
                    quizQuestionStatistics.add(quizQuestion.getQuizQuestionStatistic());
                }
            }
            quizQuestionStatisticRepository.saveAll(quizQuestionStatistics);
            // notify users via websocket about new results for the statistics.
            // filters out solution information
            quiz.filterForStatisticWebsocket();
            websocketMessagingService.sendMessage("/topic/statistic/" + quiz.getId(), quiz);
        }
    }

    /**
     * Go through all Results in the Participation and return the latest one before the new Result,
     *
     * @param newResult the new result object which will replace the old Result in the Statistics
     * @return the previous Result, which is presented in the Statistics (null if where is no previous Result)
     */
    private Result getPreviousResult(Result newResult) {
        Result oldResult = null;

        List<Result> allResultsForParticipation = resultRepository
                .findAllBySubmissionParticipationIdOrderByCompletionDateDesc(newResult.getSubmission().getParticipation().getId());
        for (Result result : allResultsForParticipation) {
            // find the latest Result, which is presented in the Statistics
            if (result.isRated() == newResult.isRated() && result.getCompletionDate().isBefore(newResult.getCompletionDate()) && !result.equals(newResult)
                    && (oldResult == null || result.getCompletionDate().isAfter(oldResult.getCompletionDate()))) {
                oldResult = result;
            }
        }
        return oldResult;
    }
}
