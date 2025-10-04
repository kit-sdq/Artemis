package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.CourseRepository;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgress;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestionProgressData;
import de.tum.cit.aet.artemis.quiz.domain.QuizTrainingLeaderboard;
import de.tum.cit.aet.artemis.quiz.dto.LeaderboardEntryDTO;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionProgressRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizQuestionRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizTrainingLeaderboardRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizTrainingLeaderboardService {

    private final QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository;

    private final CourseRepository courseRepository;

    private final UserRepository userRepository;

    private final QuizQuestionRepository quizQuestionRepository;

    private final QuizQuestionProgressRepository quizQuestionProgressRepository;

    private static final int BRONZE_LEAGUE = 5;

    public QuizTrainingLeaderboardService(QuizTrainingLeaderboardRepository quizTrainingLeaderboardRepository, CourseRepository courseRepository, UserRepository userRepository,
            QuizQuestionRepository quizQuestionRepository, QuizQuestionProgressRepository quizQuestionProgressRepository) {
        this.quizTrainingLeaderboardRepository = quizTrainingLeaderboardRepository;
        this.courseRepository = courseRepository;
        this.userRepository = userRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizQuestionProgressRepository = quizQuestionProgressRepository;
    }

    /**
     * Retrieves the leaderboard entries for a given user and course.
     *
     * @param userId   the id of the user
     * @param courseId the id of the course
     * @return a list of leaderboard entry DTOs
     */
    public List<LeaderboardEntryDTO> getLeaderboard(long userId, long courseId) {
        long totalQuestions = quizQuestionRepository.countAllPracticeQuizQuestionsByCourseId(courseId);
        int league;
        league = quizTrainingLeaderboardRepository.findByUserIdAndCourseId(userId, courseId).map(QuizTrainingLeaderboard::getLeague).orElse(BRONZE_LEAGUE);

        List<QuizTrainingLeaderboard> leaderboardEntries = quizTrainingLeaderboardRepository.findByLeagueAndCourseIdAndShowInLeaderboardTrueOrderByScoreDescUserAscId(league,
                courseId);
        return getLeaderboardEntryDTOS(leaderboardEntries, league, totalQuestions);
    }

    /**
     * Converts a list of leaderboard entities to DTOs, including rank and league information.
     *
     * @param leaderboardEntries the list of leaderboard entities
     * @param league             the league ID to use for the entries
     * @param totalQuestions     the number of total questions available for practice
     * @return a list of leaderboard entry DTOs
     */
    private static List<LeaderboardEntryDTO> getLeaderboardEntryDTOS(List<QuizTrainingLeaderboard> leaderboardEntries, int league, long totalQuestions) {
        List<LeaderboardEntryDTO> leaderboard = new ArrayList<>();
        int rank = 1;
        for (QuizTrainingLeaderboard leaderboardEntry : leaderboardEntries) {
            leaderboard.add(LeaderboardEntryDTO.of(leaderboardEntry, rank++, league, totalQuestions));
        }
        return leaderboard;
    }

    /**
     * Creates and saves a new leaderboard entry for the specified user and course.
     *
     * <p>
     * Initializes a new QuizTrainingLeaderboard entry with default values (score, league, streak, etc.)
     * and sets the visibility in the leaderboard according to the provided parameter.
     * </p>
     *
     * @param userId             the ID of the user for whom the leaderboard entry is created
     * @param courseId           the ID of the course for which the leaderboard entry is created
     * @param shownInLeaderboard whether the user should be shown in the leaderboard
     */
    public void setInitialLeaderboardEntry(long userId, long courseId, boolean shownInLeaderboard) {
        Course course = courseRepository.findByIdElseThrow(courseId);
        User user = userRepository.findByIdElseThrow(userId);
        QuizTrainingLeaderboard leaderboardEntry = new QuizTrainingLeaderboard();
        leaderboardEntry.setUser(user);
        leaderboardEntry.setCourse(course);
        leaderboardEntry.setLeague(BRONZE_LEAGUE);
        leaderboardEntry.setScore(0);
        leaderboardEntry.setAnsweredCorrectly(0);
        leaderboardEntry.setAnsweredWrong(0);
        leaderboardEntry.setDueDate(ZonedDateTime.now());
        leaderboardEntry.setStreak(0);
        leaderboardEntry.setShowInLeaderboard(shownInLeaderboard);
        quizTrainingLeaderboardRepository.save(leaderboardEntry);
    }

    /**
     * Updates the leaderboard score for a user in a course based on answered questions.
     *
     * @param userId           the id of the user
     * @param courseId         the id of the course
     * @param answeredQuestion the set of answered question progress data
     * @throws IllegalArgumentException if the user or course is not found
     */
    public void updateLeaderboardScore(long userId, long courseId, QuizQuestionProgressData answeredQuestion) {
        int scoreDelta = calculateScoreDelta(answeredQuestion);
        int correctAnswers = answeredQuestion.getLastScore() == 1.0 ? 1 : 0;
        int wrongAnswers = answeredQuestion.getLastScore() < 1.0 ? 1 : 0;

        ZonedDateTime dueDate = findEarliestDueDate(userId, courseId);

        quizTrainingLeaderboardRepository.updateLeaderboardEntry(userId, courseId, scoreDelta, correctAnswers, wrongAnswers, dueDate);
    }

    /**
     * Finds the earliest due date from a set of quiz question progress data.
     * If no due dates are available, return the current time.
     *
     * @return the earliest due date found or the current time if none exists
     */
    private ZonedDateTime findEarliestDueDate(long userId, long courseId) {
        return quizQuestionProgressRepository.findAllByUserIdAndCourseId(userId, courseId).stream().map(QuizQuestionProgress::getProgressJson)
                .map(QuizQuestionProgressData::getDueDate).filter(Objects::nonNull).min(ZonedDateTime::compareTo).orElse(ZonedDateTime.now());
    }

    /**
     * Calculates the score delta based on the answered question.
     *
     * @param answeredQuestion the answered question progress data
     * @return the calculated score delta
     */
    private int calculateScoreDelta(QuizQuestionProgressData answeredQuestion) {
        int delta = 0;
        double lastScore = answeredQuestion.getLastScore();
        int box = answeredQuestion.getBox();

        // Preliminary formula for score calculation
        double questionDelta = 2 * lastScore + box * lastScore;

        delta += (int) Math.round(questionDelta);
        return delta;
    }

    public void updateShownInLeaderboard(long userId, boolean shownInLeaderboard) {
        quizTrainingLeaderboardRepository.updateShownInLeaderboard(userId, shownInLeaderboard);
    }
}
