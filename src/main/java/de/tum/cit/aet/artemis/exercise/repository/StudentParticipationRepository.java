package de.tum.cit.aet.artemis.exercise.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.util.stream.Collectors.toMap;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackAffectedStudentDTO;
import de.tum.cit.aet.artemis.assessment.dto.FeedbackDetailDTO;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.participation.IdToPresentationScoreSum;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmittedAnswerCount;

/**
 * Spring Data JPA repository for the Participation entity.
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface StudentParticipationRepository extends ArtemisJpaRepository<StudentParticipation, Long> {

    /**
     * Converts List<[participationId, submissionCount]> into Map<participationId -> submissionCount>
     *
     * @param participationIdAndSubmissionCountPairs list of pairs (participationId, submissionCount)
     * @return map of participation id to submission count
     */
    private static Map<Long, Integer> convertListOfCountsIntoMap(List<long[]> participationIdAndSubmissionCountPairs) {
        // @formatter:off
        return participationIdAndSubmissionCountPairs.stream().collect(Collectors
            .toMap(
        participationIdAndSubmissionCountPair -> participationIdAndSubmissionCountPair[0], // participationId
        participationIdAndSubmissionCountPair -> Math.toIntExact(participationIdAndSubmissionCountPair[1]) // submissionCount
            )
        );
        // @formatter:on
    }

    @EntityGraph(type = LOAD, attributePaths = { "team.students" })
    Set<StudentParticipation> findWithTeamInformationByExerciseId(long exerciseId);

    Set<StudentParticipation> findByExerciseId(long exerciseId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.exercise.course.id = :courseId
                AND (r.rated IS NULL
                    OR r.rated = TRUE)
            """)
    List<StudentParticipation> findByCourseIdWithEagerRatedResults(@Param("courseId") long courseId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN p.team.students ts
            WHERE p.exercise.course.id = :courseId
                AND (p.student.id = :studentId
                    OR ts.id = :studentId)
                AND (r.rated IS NULL
                    OR r.rated = TRUE)
            """)
    List<StudentParticipation> findByCourseIdAndStudentIdWithEagerRatedResults(@Param("courseId") long courseId, @Param("studentId") long studentId);

    @Query("""
            SELECT COUNT(p.id) > 0
            FROM StudentParticipation p
                LEFT JOIN p.team.students ts
            WHERE p.exercise.course.id = :courseId
                AND (p.student.id = :studentId
                    OR ts.id = :studentId)
            """)
    boolean existsByCourseIdAndStudentId(@Param("courseId") long courseId, @Param("studentId") long studentId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.testRun = FALSE
                AND p.exercise.exerciseGroup.exam.id = :examId
                AND r.rated = TRUE
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    List<StudentParticipation> findByExamIdWithEagerLegalSubmissionsRatedResults(@Param("examId") long examId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
            WHERE p.exercise.course.id = :courseId
                AND p.team.shortName = :teamShortName
            """)
    List<StudentParticipation> findAllByCourseIdAndTeamShortName(@Param("courseId") long courseId, @Param("teamShortName") String teamShortName);

    List<StudentParticipation> findByTeamId(long teamId);

    @EntityGraph(type = LOAD, attributePaths = "submissions.results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndStudentLoginAndTestRun(long exerciseId, String username, boolean testRun);

    @EntityGraph(type = LOAD, attributePaths = "submissions.results")
    Optional<StudentParticipation> findWithEagerResultsByExerciseIdAndTeamId(long exerciseId, long teamId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
            WHERE p.exercise.id = :exerciseId
                AND p.student.login = :username
            """)
    Optional<StudentParticipation> findByExerciseIdAndStudentLogin(@Param("exerciseId") long exerciseId, @Param("username") String username);

    Optional<StudentParticipation> findFirstByExerciseIdAndStudentLoginOrderByIdDesc(long exerciseId, String username);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND p.student.login = :username
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(@Param("exerciseId") long exerciseId, @Param("username") String username);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.initializationDate = (
                SELECT MAX(p2.initializationDate)
                FROM StudentParticipation p2
                    LEFT JOIN p2.submissions s2
                WHERE p2.exercise.id = :exerciseId
                    AND p2.student.login = :username
                    AND (s2.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s2.type IS NULL)
            )
            """)
    Optional<StudentParticipation> findLatestWithEagerLegalSubmissionsByExerciseIdAndStudentLogin(@Param("exerciseId") long exerciseId, @Param("username") String username);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND p.student.login = :username
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
                AND p.testRun = :testRun
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsByExerciseIdAndStudentLoginAndTestRun(@Param("exerciseId") long exerciseId, @Param("username") String username,
            @Param("testRun") boolean testRun);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
            WHERE p.exercise.id = :exerciseId
                AND p.team.id = :teamId
            """)
    Optional<StudentParticipation> findOneByExerciseIdAndTeamId(@Param("exerciseId") long exerciseId, @Param("teamId") long teamId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.exercise.id = :exerciseId
                AND p.team.id = :teamId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsAndTeamStudentsByExerciseIdAndTeamId(@Param("exerciseId") long exerciseId, @Param("teamId") long teamId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.id = :participationId
            """)
    Optional<StudentParticipation> findByIdWithEagerTeamStudents(@Param("participationId") long participationId);

    @Query("""
            SELECT COUNT(p) > 0
            FROM StudentParticipation p
                LEFT JOIN p.team.students u
                LEFT JOIN p.student s
            WHERE p.id = :participationId
                AND (s.login = :login
                    OR u.login = :login)
            """)
    boolean existsByIdAndParticipatingStudentLogin(@Param("participationId") long participationId, @Param("login") String login);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = :testRun
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    List<StudentParticipation> findByExerciseIdAndTestRunWithEagerLegalSubmissionsResult(@Param("exerciseId") long exerciseId, @Param("testRun") boolean testRun);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = :testRun
            """)
    Set<StudentParticipation> findByExerciseIdAndTestRunWithEagerSubmissionsResultAssessorFeedbacksTestCases(@Param("exerciseId") long exerciseId,
            @Param("testRun") boolean testRun);

    /**
     * Get all participations for an exercise with each manual and latest results (determined by id).
     * If there is no latest result (= no result at all), the participation will still be included in the returned ResultSet, but will have an empty Result array.
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessmentNote
            WHERE p.exercise.id = :exerciseId
                AND (
                    r.id = (
                        SELECT MAX(r2.id)
                        FROM Submission s2 JOIN s2.results r2
                        WHERE s2.participation = p
                    )
                    OR r.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                    OR r IS NULL
                )
            """)
    Set<StudentParticipation> findByExerciseIdWithLatestAndManualResultsAndAssessmentNote(@Param("exerciseId") long exerciseId);

    /**
     * Get all participations for a team exercise with each manual and latest results (determined by id).
     * As the students of a team are lazy loaded, they are explicitly included into the query
     * If there is no latest result (= no result at all), the participation will still be included in the returned ResultSet, but will have an empty Result array.
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.exercise.id = :exerciseId
                AND (
                    r.id = (
                        SELECT MAX(r2.id)
                        FROM Submission s2 JOIN s2.results r2
                        WHERE s2.participation = p
                    )
                    OR r.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                    OR r IS NULL
                )
            """)
    Set<StudentParticipation> findByExerciseIdWithLatestAndManualResultsWithTeamInformation(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = :testRun
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
                AND r.assessmentType <> de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                AND r.id = (
                    SELECT MAX(r2.id)
                    FROM Submission s2 JOIN s2.results r2
                    WHERE s2.participation = p
                      AND r2.completionDate IS NOT NULL
                )
            """)
    Set<StudentParticipation> findByExerciseIdAndTestRunWithEagerLegalSubmissionsAndLatestResultWithCompletionDate(@Param("exerciseId") long exerciseId,
            @Param("testRun") boolean testRun);

    /**
     * Get all participations for an exercise with each latest {@link AssessmentType#AUTOMATIC} result and feedbacks (determined by id).
     *
     * @param exerciseId Exercise id.
     * @return participations for exercise.
     */
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE p.exercise.id = :exerciseId
                AND (r.id = (
                    SELECT MAX(r2.id)
                    FROM Submission s2 JOIN s2.results r2
                    WHERE s2.participation = p
                      AND r2.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                      AND (s2.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s2.type IS NULL)
                ))
            """)
    List<StudentParticipation> findByExerciseIdWithLatestAutomaticResultAndFeedbacksAndTestCases(@Param("exerciseId") long exerciseId);

    /**
     * Get all participations without individual due date for an exercise with each latest {@link AssessmentType#AUTOMATIC} result and feedbacks (determined by id).
     *
     * @param exerciseId Exercise id.
     * @return participations for the exercise.
     */
    default List<StudentParticipation> findByExerciseIdWithLatestAutomaticResultAndFeedbacksAndTestCasesWithoutIndividualDueDate(long exerciseId) {
        return findByExerciseIdWithLatestAutomaticResultAndFeedbacksAndTestCases(exerciseId).stream().filter(participation -> participation.getIndividualDueDate() == null)
                .toList();
    }

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE p.id = :participationId
                AND r.id = (
                    SELECT MAX(r2.id)
                    FROM Submission s2 JOIN s2.results r2
                    WHERE s2.participation = p
                        AND r2.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.AUTOMATIC
                        AND (s2.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s2.type IS NULL)
                )
            """)
    Optional<StudentParticipation> findByIdWithLatestAutomaticResultAndFeedbacksAndTestCases(@Param("participationId") long participationId);

    // Manual result can either be from type MANUAL or SEMI_AUTOMATIC
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE p.exercise.id = :exerciseId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
                AND (r.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL
                    OR r.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC)
            """)
    List<StudentParticipation> findByExerciseIdWithManualResultAndFeedbacksAndTestCases(@Param("exerciseId") long exerciseId);

    default List<StudentParticipation> findByExerciseIdWithManualResultAndFeedbacksAndTestCasesWithoutIndividualDueDate(long exerciseId) {
        return findByExerciseIdWithManualResultAndFeedbacksAndTestCases(exerciseId).stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
    }

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.feedbacks f
                LEFT JOIN FETCH f.testCase
            WHERE p.id = :participationId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
                AND (r.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL
                    OR r.assessmentType = de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC)
            """)
    Optional<StudentParticipation> findByIdWithManualResultAndFeedbacks(@Param("participationId") long participationId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND p.student.id = :studentId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerLegalSubmissions(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
            WHERE p.exercise.id = :exerciseId
                AND p.student.id = :studentId
            """)
    List<StudentParticipation> findByExerciseIdAndStudentId(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results
            WHERE p.exercise.id = :exerciseId
                AND p.student.id = :studentId
            """)
    List<StudentParticipation> findByExerciseIdAndStudentIdWithEagerResultsAndSubmissions(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :exerciseId
                AND p.team.id = :teamId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    List<StudentParticipation> findByExerciseIdAndTeamIdWithEagerLegalSubmissions(@Param("exerciseId") long exerciseId, @Param("teamId") long teamId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students s
                LEFT JOIN FETCH p.submissions sub
                LEFT JOIN FETCH sub.results r
            WHERE p.exercise.id = :exerciseId
                AND s.id = :studentId
            """)
    List<StudentParticipation> findAllWithTeamStudentsByExerciseIdAndTeamStudentIdWithSubmissionsAndResults(@Param("exerciseId") long exerciseId,
            @Param("studentId") long studentId);

    // NOTE: we should not fetch too elements here so we leave out feedback and test cases, otherwise the query will be very slow
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.exercise.id = :exerciseId
                AND p.student.id = :studentId
                AND p.testRun = :testRun
                AND (
                    r.id = (
                        SELECT MAX(r2.id)
                        FROM Submission s2 JOIN s2.results r2
                        WHERE s2.participation = p
                          AND (s2.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s2.type IS NULL)
                    )
                    OR r.id IS NULL
                )
            """)
    Optional<StudentParticipation> findByExerciseIdAndStudentIdAndTestRunWithLatestResult(@Param("exerciseId") long exerciseId, @Param("studentId") long studentId,
            @Param("testRun") boolean testRun);

    /**
     * Find all participations of submissions that are submitted and do not already have a manual result and do not belong to test runs.
     * No manual result means that no user has started an assessment for the corresponding submission yet.
     * <p>
     * If a student can have multiple submissions per exercise type, the latest not {@link SubmissionType#ILLEGAL} ILLEGAL submission (by
     * id) will be returned.
     *
     * @param correctionRound the correction round the fetched results should belong to
     * @param exerciseId      the exercise id the participations should belong to
     * @return a list of participations including their submitted submissions that do not have a manual result
     */
    // NOTE: we should not fetch too elements here so we leave out feedback and test cases, otherwise the query will be very slow
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions submission
                LEFT JOIN FETCH submission.results result
                LEFT JOIN FETCH result.assessor
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND 0L = (
                    SELECT COUNT(r2)
                    FROM Result r2
                    WHERE r2.assessor IS NOT NULL
                        AND (r2.rated IS NULL OR r2.rated = FALSE)
                        AND r2.submission = submission
                )
                AND :correctionRound = (
                    SELECT COUNT(r)
                    FROM Result r
                    WHERE r.assessor IS NOT NULL
                        AND r.rated = TRUE
                        AND r.submission = submission
                        AND r.completionDate IS NOT NULL
                        AND r.assessmentType IN (
                            de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL,
                            de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC
                        )
                        AND (p.exercise.dueDate IS NULL OR r.submission.submissionDate <= p.exercise.dueDate)
                )
                AND :correctionRound = (
                    SELECT COUNT(prs)
                    FROM Submission s2 JOIN s2.results prs
                    WHERE s2.participation = p
                      AND prs.assessmentType IN (
                            de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL,
                            de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC
                        )
                )
                AND submission.submitted = TRUE
                AND submission.id = (
                    SELECT MAX(s3.id)
                    FROM p.submissions s3
                )
            """)
    List<StudentParticipation> findByExerciseIdWithLatestSubmissionWithoutManualResultsAndIgnoreTestRunParticipation(@Param("exerciseId") long exerciseId,
            @Param("correctionRound") long correctionRound);

    Set<StudentParticipation> findDistinctAllByExerciseIdInAndStudentId(Set<Long> exerciseIds, Long studentId);

    // NOTE: we should not fetch too elements here so we leave out feedback and test cases, otherwise the query will be very slow
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.exercise.id = :exerciseId
                AND (p.individualDueDate IS NULL OR p.individualDueDate <= :now)
                AND p.testRun = FALSE
                AND NOT EXISTS (
                    SELECT prs
                    FROM Submission s2 JOIN s2.results prs
                    WHERE s2.participation = p
                      AND prs.assessmentType IN (
                          de.tum.cit.aet.artemis.assessment.domain.AssessmentType.MANUAL,
                          de.tum.cit.aet.artemis.assessment.domain.AssessmentType.SEMI_AUTOMATIC
                      )
                )
                AND s.submitted = TRUE
                AND s.id = (
                    SELECT MAX(s3.id)
                    FROM p.submissions s3
                )
            """)
    List<StudentParticipation> findByExerciseIdWithLatestSubmissionWithoutManualResultsWithPassedIndividualDueDateIgnoreTestRuns(@Param("exerciseId") long exerciseId,
            @Param("now") ZonedDateTime now);

    @Query("""
            SELECT p
            FROM Participation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.id = :participationId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsById(@Param("participationId") long participationId);

    @Query("""
            SELECT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.id = :participationId
            """)
    Optional<StudentParticipation> findWithEagerResultsById(@Param("participationId") long participationId);

    /**
     * Find the participation with the given id. Additionally, load all the submissions and results of the participation from the database.
     * Further, load the exercise and its course. Returns an empty Optional if the participation could not be found.
     * <p>
     * Note: Does NOT load illegal submissions!
     *
     * @param participationId the id of the participation
     * @return the participation with eager submissions, results, exercise and course or an empty Optional
     */
    @Query("""
            SELECT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results sr
                LEFT JOIN FETCH sr.feedbacks
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students
            WHERE p.id = :participationId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    Optional<StudentParticipation> findWithEagerLegalSubmissionsResultsFeedbacksById(@Param("participationId") long participationId);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results", "submissions.results.assessor" })
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseId(long exerciseId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor a
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
            """)
    List<StudentParticipation> findAllWithEagerSubmissionsAndEagerResultsAndEagerAssessorByExerciseIdIgnoreTestRuns(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT DISTINCT p.id
            FROM StudentParticipation p
                JOIN p.submissions s
                JOIN s.results r
            WHERE p.exercise.id = :exerciseId
                AND (p.student.firstName LIKE %:partialStudentName% OR p.student.lastName LIKE %:partialStudentName%)
                AND r.completionDate IS NOT NULL
            """)
    List<Long> findIdsByExerciseIdAndStudentName(@Param("exerciseId") long exerciseId, @Param("partialStudentName") String partialStudentName, Pageable pageable);

    @EntityGraph(type = LOAD, attributePaths = { "submissions", "submissions.results" })
    List<StudentParticipation> findStudentParticipationWithSubmissionsAndResultsByIdIn(List<Long> ids);

    @Query("""
            SELECT COUNT(p)
            FROM StudentParticipation p
                JOIN Result r ON r.submission.participation.id = p.id
            WHERE p.exercise.id = :exerciseId
                AND (p.student.firstName LIKE %:partialStudentName%
                    OR p.student.lastName LIKE %:partialStudentName%)
                AND r.completionDate IS NOT NULL
            """)
    long countByExerciseIdAndStudentName(@Param("exerciseId") long exerciseId, @Param("partialStudentName") String partialStudentName);

    /**
     * Retrieves a paginated list of {@link StudentParticipation} entities associated with a specific exercise,
     * and optionally filtered by a partial student name. The entities are fetched with eager loading of submissions and results.
     *
     * @param exerciseId         the ID of the exercise.
     * @param partialStudentName the partial name of the student to filter by (can be empty or null to include all students).
     * @param pageable           the pagination information.
     * @return a paginated list of {@link StudentParticipation} entities associated with the specified exercise and student name filter.
     *         If no entities are found, returns an empty page.
     */
    default Page<StudentParticipation> findAllWithEagerSubmissionsAndResultsByExerciseId(long exerciseId, String partialStudentName, Pageable pageable) {
        List<Long> ids = findIdsByExerciseIdAndStudentName(exerciseId, partialStudentName, pageable);
        if (ids.isEmpty()) {
            return Page.empty(pageable);
        }
        List<StudentParticipation> result = findStudentParticipationWithSubmissionsAndResultsByIdIn(ids);
        return new PageImpl<>(result, pageable, countByExerciseIdAndStudentName(exerciseId, partialStudentName));
    }

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results sr
            WHERE p.exercise.id = :exerciseId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL OR s.type IS NULL)
            """)
    List<StudentParticipation> findAllWithEagerLegalSubmissionsAndEagerResultsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Retrieves all distinct `StudentParticipation` entities for a specific exercise,
     * including their latest non-illegal submission and the latest rated result for each submission.
     * The method fetches related submissions, results, student, and team data to avoid the N+1 select problem.
     *
     * <p>
     * The method ensures that:
     * <ul>
     * <li>Only participations belonging to the specified exercise are retrieved.</li>
     * <li>Participations marked as a test run are excluded.</li>
     * <li>Only the latest non-illegal submission for each participation is considered.</li>
     * <li>Only the latest rated result for each submission is considered.</li>
     * </ul>
     *
     * @param exerciseId the ID of the exercise for which to retrieve participations.
     * @return a list of distinct `StudentParticipation` entities matching the criteria.
     */
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH p.student
                LEFT JOIN FETCH p.team
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND s.id = (
                    SELECT MAX(s2.id)
                    FROM p.submissions s2
                    WHERE s2.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                        OR s2.type IS NULL
                )
                AND r.id = (
                    SELECT MAX(r2.id)
                    FROM s.results r2
                    WHERE r2.rated = TRUE
                )
            """)
    List<StudentParticipation> findAllForPlagiarism(@Param("exerciseId") long exerciseId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.student.id = :studentId
                AND p.exercise IN :exercises
                AND (p.testRun = FALSE
                    OR :includeTestRuns = TRUE)
            """)
    Set<StudentParticipation> findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(@Param("studentId") long studentId,
            @Param("exercises") Collection<Exercise> exercises, @Param("includeTestRuns") boolean includeTestRuns);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.testRun = FALSE
                AND p.student.id = :studentId
                AND p.exercise IN :exercises
            """)
    List<StudentParticipation> findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(@Param("studentId") long studentId,
            @Param("exercises") Collection<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.testRun = FALSE
                AND p.student.id = :studentId
                AND p.exercise IN :exercises
            """)
    List<StudentParticipation> findByStudentIdAndIndividualExercisesWithEagerSubmissionsIgnoreTestRuns(@Param("studentId") long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor
            WHERE p.testRun = FALSE
                AND p.student.id = :studentId
                AND p.exercise IN :exercises
            """)
    List<StudentParticipation> findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultAndAssessorIgnoreTestRuns(@Param("studentId") long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p
            FROM StudentExam se
                JOIN se.exam e
                JOIN se.studentParticipations p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH r.assessor
            WHERE p.testRun = FALSE
                AND se.id IN :studentExamId
                AND e.testExam = TRUE
            """)
    List<StudentParticipation> findTestExamParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResultAndAssessorIgnoreTestRuns(
            @Param("studentExamId") long studentExamId);

    @Query("""
            SELECT DISTINCT p
                FROM StudentExam se
                    JOIN se.exam e
                    JOIN se.studentParticipations p
                    LEFT JOIN FETCH p.submissions s
                    LEFT JOIN FETCH s.results r
                WHERE p.testRun = FALSE
                    AND se.id IN :studentExamId
                    AND e.testExam = TRUE
            """)
    List<StudentParticipation> findTestExamParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(@Param("studentExamId") long studentExamId);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.testRun = TRUE
                AND p.student.id = :studentId
                AND p.exercise IN :exercises
            """)
    List<StudentParticipation> findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(@Param("studentId") long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.testRun = TRUE
                AND p.student.id = :studentId
                AND p.exercise IN :exercises
            """)
    List<StudentParticipation> findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissions(@Param("studentId") long studentId,
            @Param("exercises") List<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH p.team t
                LEFT JOIN FETCH t.students teamStudent
            WHERE teamStudent.id = :studentId
                AND p.exercise IN :exercises
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    Set<StudentParticipation> findByStudentIdAndTeamExercisesWithEagerLegalSubmissionsResult(@Param("studentId") long studentId,
            @Param("exercises") Collection<Exercise> exercises);

    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
                LEFT JOIN FETCH p.team t
            WHERE p.exercise.course.id = :courseId
                AND t.shortName = :teamShortName
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            """)
    List<StudentParticipation> findAllByCourseIdAndTeamShortNameWithEagerLegalSubmissionsResult(@Param("courseId") long courseId, @Param("teamShortName") String teamShortName);

    /**
     * Count the number of submissions for each participation in a given exercise.
     *
     * @param exerciseId the id of the exercise for which to consider participations
     * @return Tuples of participation ids and number of submissions per participation
     */
    @Query("""
            SELECT p.id, COUNT(s)
            FROM StudentParticipation p
                LEFT JOIN p.submissions s
            WHERE p.exercise.id = :exerciseId
            GROUP BY p.id
            """)
    List<long[]> countSubmissionsPerParticipationByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Count the number of submissions for each participation for a given team in a course
     *
     * @param courseId      the id of the course for which to consider participations
     * @param teamShortName the short name of the team for which to consider participations
     * @return Tuples of participation ids and number of submissions per participation
     */
    @Query("""
            SELECT p.id, COUNT(s)
            FROM StudentParticipation p
                LEFT JOIN p.submissions s
            WHERE p.team.shortName = :teamShortName
                AND p.exercise.course.id = :courseId
                AND (s.type <> de.tum.cit.aet.artemis.exercise.domain.SubmissionType.ILLEGAL
                    OR s.type IS NULL)
            GROUP BY p.id
            """)
    List<long[]> countLegalSubmissionsPerParticipationByCourseIdAndTeamShortName(@Param("courseId") long courseId, @Param("teamShortName") String teamShortName);

    // TODO SE Improve - maybe leave out max id line?
    @Query("""
            SELECT DISTINCT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
                LEFT JOIN FETCH s.results r
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND s.id = (SELECT MAX(s1.id) FROM p.submissions s1)
                AND EXISTS (
                    SELECT s1
                    FROM p.submissions s1
                    WHERE s1.participation.id = p.id
                        AND s1.submitted = TRUE
                        AND (r.assessor = :assessor
                            OR r.assessor.id IS NULL)
                )
            """)
    List<StudentParticipation> findAllByParticipationExerciseIdAndResultAssessorAndCorrectionRoundIgnoreTestRuns(@Param("exerciseId") long exerciseId,
            @Param("assessor") User assessor);

    @NotNull
    default StudentParticipation findByIdWithResultsElseThrow(long participationId) {
        return getValueElseThrow(findWithEagerResultsById(participationId), participationId);
    }

    @NotNull
    default StudentParticipation findByIdWithLegalSubmissionsResultsFeedbackElseThrow(long participationId) {
        return getValueElseThrow(findWithEagerLegalSubmissionsResultsFeedbacksById(participationId), participationId);
    }

    @NotNull
    default StudentParticipation findByIdWithLegalSubmissionsElseThrow(long participationId) {
        return getValueElseThrow(findWithEagerLegalSubmissionsById(participationId), participationId);
    }

    @NotNull
    default StudentParticipation findByIdWithEagerTeamStudentsElseThrow(long participationId) {
        return getValueElseThrow(findByIdWithEagerTeamStudents(participationId), participationId);
    }

    /**
     * Get all participations belonging to exam with submissions and their relevant results.
     *
     * @param examId the id of the exam
     * @return an unmodifiable list of participations belonging to course
     */
    default List<StudentParticipation> findByExamIdWithSubmissionRelevantResult(long examId) {
        var participations = findByExamIdWithEagerLegalSubmissionsRatedResults(examId); // without test run participations
        // filter out the participations of test runs which can only be made by instructors
        participations = participations.stream().filter(studentParticipation -> !studentParticipation.isTestRun()).toList();
        return filterParticipationsWithRelevantResults(participations, true);
    }

    /**
     * Get all participations belonging to a student in a course with relevant results.
     *
     * @param courseId  the id of the course
     * @param studentId the id of the student
     * @return an unmodifiable list of participations belonging to the given student in the given course
     */
    default List<StudentParticipation> findByCourseIdAndStudentIdWithRelevantResult(long courseId, long studentId) {
        List<StudentParticipation> participations = findByCourseIdAndStudentIdWithEagerRatedResults(courseId, studentId);
        return filterParticipationsWithRelevantResults(participations, false);
    }

    /**
     * Get all participations belonging to course with relevant results.
     *
     * @param courseId the id of the course
     * @return an unmodifiable list of participations belonging to course
     */
    default List<StudentParticipation> findByCourseIdWithRelevantResult(long courseId) {
        List<StudentParticipation> participations = findByCourseIdWithEagerRatedResults(courseId);
        return filterParticipationsWithRelevantResults(participations, false);
    }

    /**
     * filters the relevant results by removing all irrelevant ones
     *
     * @param participations     the participations to get filtered
     * @param resultInSubmission flag to indicate if the results are represented in the submission or participation
     * @return an unmodifiable list of filtered participations
     */
    private List<StudentParticipation> filterParticipationsWithRelevantResults(List<StudentParticipation> participations, boolean resultInSubmission) {
        return participations.stream()
                // Filter out participations without Students
                // These participations are used e.g. to store template and solution build plans in programming exercises
                .filter(participation -> participation.getParticipant() != null)

                // filter all irrelevant results, i.e. rated = false or no completion date or no score
                .peek(participation -> {
                    List<Result> relevantResults = new ArrayList<>();

                    // Get the results over the participation or over submissions
                    Set<Result> resultsOfParticipation;
                    if (resultInSubmission) {
                        resultsOfParticipation = participation.getSubmissions().stream().map(Submission::getLatestResult).collect(Collectors.toSet());
                    }
                    else {
                        resultsOfParticipation = participation.getResults();
                    }
                    // search for the relevant result by filtering out irrelevant results using the continue keyword
                    // this for loop is optimized for performance and thus not very easy to understand ;)
                    for (Result result : resultsOfParticipation) {
                        // this should not happen because the database call above only retrieves rated results
                        if (Boolean.FALSE.equals(result.isRated())) {
                            continue;
                        }
                        if (result.getCompletionDate() == null || result.getScore() == null) {
                            // we are only interested in results with completion date and with score
                            continue;
                        }
                        relevantResults.add(result);
                    }
                    // we take the last rated result
                    if (!relevantResults.isEmpty()) {
                        // make sure to take the latest result
                        relevantResults.sort((r1, r2) -> r2.getCompletionDate().compareTo(r1.getCompletionDate()));
                        Result correctResult = relevantResults.getFirst();
                        relevantResults.clear();
                        relevantResults.add(correctResult);
                    }
                }).toList();
    }

    /**
     * Get all participations for the given studentExam and exercises combined with their submissions with a result.
     * Distinguishes between real exams, test exams and test runs and only loads the respective participations
     *
     * @param studentExam  studentExam with exercises loaded
     * @param withAssessor (only for non-test runs) if assessor should be loaded with the result
     * @return student's participations with submissions and results
     */
    default List<StudentParticipation> findByStudentExamWithEagerSubmissionsResult(StudentExam studentExam, boolean withAssessor) {
        if (studentExam.isTestRun()) {
            return findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(studentExam.getUser().getId(), studentExam.getExercises());
        }

        if (studentExam.isTestExam()) {
            if (withAssessor) {
                return findTestExamParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResultAndAssessorIgnoreTestRuns(studentExam.getId());
            }
            else {
                return findTestExamParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(studentExam.getId());
            }
        }
        else {
            if (withAssessor) {
                return findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultAndAssessorIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
            }
            else {
                return findByStudentIdAndIndividualExercisesWithEagerSubmissionsResultIgnoreTestRuns(studentExam.getUser().getId(), studentExam.getExercises());
            }
        }
    }

    /**
     * Get all participations for the given studentExam and exercises (not necessarily all exercise of the student exam)
     * Combines the participations with their submissions, but without results and assessors.
     * Distinguishes between student exams and test runs and only loads the respective participations
     *
     * @param studentExam studentExam with exercises loaded
     * @param exercises   exercises for which participations should be loaded
     * @return student's participations with submissions and results
     */
    default List<StudentParticipation> findByStudentExamWithEagerSubmissions(StudentExam studentExam, List<Exercise> exercises) {
        if (studentExam.isTestRun()) {
            return findTestRunParticipationsByStudentIdAndIndividualExercisesWithEagerSubmissions(studentExam.getUser().getId(), exercises);
        }
        else {
            return findByStudentIdAndIndividualExercisesWithEagerSubmissionsIgnoreTestRuns(studentExam.getUser().getId(), exercises);
        }
    }

    /**
     * Get all participations for all exercises of the given studentExam combined with their submissions, but without results and assessors.
     * Distinguishes between student exams and test runs and only loads the respective participations
     *
     * @param studentExam studentExam with exercises loaded
     * @return student's participations with submissions and results
     */
    default List<StudentParticipation> findByStudentExamWithEagerSubmissions(StudentExam studentExam) {
        return findByStudentExamWithEagerSubmissions(studentExam, studentExam.getExercises());
    }

    /**
     * Get a mapping of participation ids to the number of submission for each participation.
     *
     * @param exerciseId the id of the exercise for which to consider participations
     * @return a map of submissions per participation in the given exercise
     */
    default Map<Long, Integer> countSubmissionsPerParticipationByExerciseIdAsMap(long exerciseId) {
        return convertListOfCountsIntoMap(countSubmissionsPerParticipationByExerciseId(exerciseId));
    }

    /**
     * Get a mapping of participation ids to the number of submission for each participation.
     *
     * @param courseId      the id of the course for which to consider participations
     * @param teamShortName the short name of the team for which to consider participations
     * @return a map of submissions per participation in the given course for the team
     */
    default Map<Long, Integer> countLegalSubmissionsPerParticipationByCourseIdAndTeamShortNameAsMap(long courseId, String teamShortName) {
        return convertListOfCountsIntoMap(countLegalSubmissionsPerParticipationByCourseIdAndTeamShortName(courseId, teamShortName));
    }

    @Query("""
            SELECT COUNT(p)
            FROM StudentParticipation p
                LEFT JOIN p.exercise exercise
            WHERE exercise.id = :exerciseId
                AND p.testRun = :testRun
            GROUP BY exercise.id
            """)
    Long countParticipationsByExerciseIdAndTestRun(@Param("exerciseId") long exerciseId, @Param("testRun") boolean testRun);

    /**
     * Adds the transient property numberOfParticipations for each exercise to
     * let instructors know which exercise has how many participations
     *
     * @param exerciseGroup exercise group for which to add transient property
     */
    default void addNumberOfExamExerciseParticipations(ExerciseGroup exerciseGroup) {
        exerciseGroup.getExercises().forEach(exercise -> {
            Long numberOfParticipations = countParticipationsByExerciseIdAndTestRun(exercise.getId(), false);
            // avoid setting to null in case not participations exist
            exercise.setNumberOfParticipations((numberOfParticipations != null) ? numberOfParticipations : 0);
        });
    }

    /**
     * Gets all the participations of the user in the given exercises
     *
     * @param user            the user to get the participations for
     * @param exercises       the exercise to get the participations for
     * @param includeTestRuns flag that indicates whether test run participations should be included
     * @return an unmodifiable list of participations of the user in the exercises
     */
    default Set<StudentParticipation> getAllParticipationsOfUserInExercises(User user, Set<Exercise> exercises, boolean includeTestRuns) {
        Map<ExerciseMode, List<Exercise>> exercisesGroupedByExerciseMode = exercises.stream().collect(Collectors.groupingBy(Exercise::getMode));

        Set<Exercise> individualExercises = exercisesGroupedByExerciseMode.getOrDefault(ExerciseMode.INDIVIDUAL, List.of()).stream().filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Set<Exercise> teamExercises = exercisesGroupedByExerciseMode.getOrDefault(ExerciseMode.TEAM, List.of()).stream().filter(Objects::nonNull).collect(Collectors.toSet());

        // Note: we need two database calls here, because of performance reasons: the entity structure for team is significantly different and a combined database call
        // would lead to a SQL statement that cannot be optimized. However, we only call the database if needed, i.e. if the exercise set is not empty

        // 1st: fetch participations, submissions and results for individual exercises
        Set<StudentParticipation> individualParticipations = individualExercises.isEmpty() ? Set.of()
                : findByStudentIdAndIndividualExercisesWithEagerSubmissionsResult(user.getId(), individualExercises, includeTestRuns);

        // 2nd: fetch participations, submissions and results for team exercises
        Set<StudentParticipation> teamParticipations = teamExercises.isEmpty() ? Set.of()
                : findByStudentIdAndTeamExercisesWithEagerLegalSubmissionsResult(user.getId(), teamExercises);

        // 3rd: merge both into one set for further processing
        return Stream.concat(individualParticipations.stream(), teamParticipations.stream()).collect(Collectors.toSet());
    }

    /**
     * Checks if the exercise has any test runs and sets the transient property if it does
     *
     * @param exercise - the exercise for which we check if test runs exist
     */
    default void checkTestRunsExist(Exercise exercise) {
        Long containsTestRunParticipations = countParticipationsByExerciseIdAndTestRun(exercise.getId(), true);
        if (containsTestRunParticipations != null && containsTestRunParticipations > 0) {
            exercise.setTestRunParticipationsExist(Boolean.TRUE);
        }
    }

    @Query("""
            SELECT new de.tum.cit.aet.artemis.quiz.domain.QuizSubmittedAnswerCount(COUNT(a.id), s.id, p.id)
            FROM SubmittedAnswer a
                LEFT JOIN a.submission s
                LEFT JOIN s.participation p
            WHERE p.exercise.exerciseGroup.exam.id = :examId
            GROUP BY s.id, p.id
            """)
    List<QuizSubmittedAnswerCount> findSubmittedAnswerCountForQuizzesInExam(@Param("examId") long examId);

    /**
     * Gets the sum of all presentation scores for the given course and student.
     *
     * @param courseId  the id of the course
     * @param studentId the id of the student
     * @return the sum of all presentation scores for the given course and student
     */
    @Query("""
            SELECT COALESCE(SUM(p.presentationScore), 0)
            FROM StudentParticipation p
                LEFT JOIN p.team.students ts
            WHERE p.exercise.course.id = :courseId
                AND p.presentationScore IS NOT NULL
                AND (p.student.id = :studentId OR ts.id = :studentId)
            """)
    double sumPresentationScoreByStudentIdAndCourseId(@Param("courseId") long courseId, @Param("studentId") long studentId);

    /**
     * Maps all given studentIds to their presentation score sum for the given course.
     *
     * @param courseId   the id of the course
     * @param studentIds the ids of the students
     * @return a set of id to presentation score sum mappings
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.exercise.domain.participation.IdToPresentationScoreSum(
                COALESCE(p.student.id, ts.id),
                COALESCE(SUM(p.presentationScore), 0)
            )
            FROM StudentParticipation p
                LEFT JOIN p.team.students ts
            WHERE p.exercise.course.id = :courseId
                AND p.presentationScore IS NOT NULL
                AND (p.student.id IN :studentIds OR ts.id IN :studentIds)
            GROUP BY COALESCE(p.student.id, ts.id)
            """)
    Set<IdToPresentationScoreSum> sumPresentationScoreByStudentIdsAndCourseId(@Param("courseId") long courseId, @Param("studentIds") Set<Long> studentIds);

    @Query("""
            SELECT p
            FROM StudentParticipation p
                LEFT JOIN FETCH p.submissions s
            WHERE p.exercise.id = :exerciseId
            """)
    Set<StudentParticipation> findByExerciseIdWithEagerSubmissions(@Param("exerciseId") long exerciseId);

    /**
     * Maps all given studentIds to their presentation score sum for the given course.
     *
     * @param courseId   the id of the course
     * @param studentIds the ids of the students
     * @return a map of studentId to presentation score sum
     */
    default Map<Long, Double> mapStudentIdToPresentationScoreSumByCourseIdAndStudentIds(long courseId, Set<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Map.of();
        }
        var studentIdToPresentationScoreSum = sumPresentationScoreByStudentIdsAndCourseId(courseId, studentIds);
        return studentIdToPresentationScoreSum.stream().collect(toMap(IdToPresentationScoreSum::participantId, IdToPresentationScoreSum::presentationScoreSum));
    }

    /**
     * Gets the average presentation score for all participations of the given course.
     *
     * @param courseId the id of the course
     * @return the average presentation score
     */
    @Query("""
            SELECT COALESCE(AVG(p.presentationScore), 0)
            FROM StudentParticipation p
                LEFT JOIN p.team.students ts
            WHERE p.exercise.course.id = :courseId
                AND p.presentationScore IS NOT NULL
            """)
    double getAvgPresentationScoreByCourseId(@Param("courseId") long courseId);

    /**
     * Retrieves aggregated feedback details for a given exercise, including the count of each unique feedback detail text,
     * test case name, task, and error category.
     * <br>
     * The query calculates:
     * - The number of occurrences of each feedback detail (COUNT).
     * - The relative count as a percentage of the total distinct results.
     * - The corresponding task name for each feedback item by checking if the feedback test case name is associated with a task.
     * If a feedback item is not assigned to a task, it is labeled as "Not assigned to task."
     * - The error category for each feedback item, classified as one of "Student Error", "Ares Error", or "AST Error".
     * <br>
     * It supports filtering by:
     * - Search term: Case-insensitive filtering on feedback detail text.
     * - Test case names: Filters feedback based on specific test case names (optional).
     * - Task names: Filters feedback based on specific task names by mapping them to their associated test cases.
     * If "Not assigned to task" is specified, only feedback entries without an associated task will be returned.
     * - Occurrence range: Filters feedback where the number of occurrences (COUNT) is between the specified minimum and maximum values (inclusive).
     * - Error categories: Filters feedback based on error categories, which can be "Student Error", "Ares Error", or "AST Error".
     * <br>
     * Grouping is done by feedback detail text, test case name and error category. The occurrence count is filtered using the HAVING clause.
     *
     * @param exerciseId            The ID of the exercise for which feedback details should be retrieved.
     * @param searchTerm            The search term used for filtering the feedback detail text (optional).
     * @param filterTestCases       List of active test case names to filter the feedback results (optional).
     * @param filterTaskNames       List of task names to filter feedback results based on the associated test cases (optional).
     *                                  If "Not assigned to task" is specified, only feedback entries without an associated task will be returned.
     * @param minOccurrence         The minimum number of occurrences to include in the results.
     * @param maxOccurrence         The maximum number of occurrences to include in the results.
     * @param filterErrorCategories List of error categories to filter the feedback results. Supported categories include "Student Error",
     *                                  "Ares Error", and "AST Error".
     * @param pageable              Pagination information to apply.
     * @return A page of {@link FeedbackDetailDTO} objects representing the aggregated feedback details.
     */
    @Query("""
            SELECT new de.tum.cit.aet.artemis.assessment.dto.FeedbackDetailDTO(
                LISTAGG(CAST(f.id AS string), ',') WITHIN GROUP (ORDER BY f.id),
                COUNT(f.id),
                0,
                f.detailText,
                f.testCase.testName,
                COALESCE((
                    SELECT MAX(t.taskName)
                    FROM ProgrammingExerciseTask t
                    LEFT JOIN t.testCases tct
                    WHERE t.exercise.id = :exerciseId AND tct.testName = f.testCase.testName
                ), 'Not assigned to task'),
                CASE
                    WHEN f.detailText LIKE 'ARES Security Error%' THEN 'Ares Error'
                    WHEN f.detailText LIKE 'Unwanted Statement found%' THEN 'AST Error'
                    ELSE 'Student Error'
                END,
                f.hasLongFeedbackText
            )
            FROM ProgrammingExerciseStudentParticipation p
            INNER JOIN p.submissions s
            INNER JOIN s.results r ON r.id = (
                SELECT MAX(r2.id)
                FROM Submission s2 JOIN s2.results r2
                WHERE s2.participation = p
            )
            INNER JOIN r.feedbacks f
            WHERE p.exercise.id = :exerciseId
                AND p.testRun = FALSE
                AND f.positive = FALSE
                AND (:searchTerm = '' OR LOWER(f.detailText) LIKE LOWER(CONCAT('%', REPLACE(REPLACE(:searchTerm, '%', '\\%'), '_', '\\_'), '%')) ESCAPE '\\')
                AND (:#{#filterTestCases != NULL && #filterTestCases.size() < 1} = TRUE OR f.testCase.testName IN (:filterTestCases))
                AND (:#{#filterTaskNames != NULL && #filterTaskNames.size() < 1} = TRUE OR f.testCase.testName NOT IN (
                        SELECT tct.testName
                        FROM ProgrammingExerciseTask t
                        LEFT JOIN t.testCases tct
                        WHERE t.taskName IN (:filterTaskNames)
                    ))
                AND (:#{#filterErrorCategories != NULL && #filterErrorCategories.size() < 1} = TRUE OR CASE
                            WHEN f.detailText LIKE 'ARES Security Error%' THEN 'Ares Error'
                            WHEN f.detailText LIKE 'Unwanted Statement found%' THEN 'AST Error'
                            ELSE 'Student Error'
                        END IN (:filterErrorCategories))
            GROUP BY f.detailText, f.testCase.testName, f.hasLongFeedbackText
            HAVING COUNT(f.id) BETWEEN :minOccurrence AND :maxOccurrence
            """)
    Page<FeedbackDetailDTO> findFilteredFeedbackByExerciseId(@Param("exerciseId") long exerciseId, @Param("searchTerm") String searchTerm,
            @Param("filterTestCases") List<String> filterTestCases, @Param("filterTaskNames") List<String> filterTaskNames, @Param("minOccurrence") long minOccurrence,
            @Param("maxOccurrence") long maxOccurrence, @Param("filterErrorCategories") List<String> filterErrorCategories, @Param("pageable") Pageable pageable);

    /**
     * Counts the distinct number of latest results for a given exercise, excluding those in practice mode.
     * <br>
     * For each participation, it selects only the latest result (using MAX) and ensures that the participation is not a test run.
     *
     * @param exerciseId Exercise ID for which distinct results should be counted.
     * @return The total number of distinct latest results for the given exercise.
     */
    @Query("""
            SELECT COUNT(DISTINCT r.id)
            FROM ProgrammingExerciseStudentParticipation p
                INNER JOIN p.submissions s
                INNER JOIN s.results r ON r.id = (
                         SELECT MAX(r2.id)
                         FROM Submission s2 JOIN s2.results r2
                         WHERE s2.participation = p
                     )
            WHERE p.exercise.id = :exerciseId
                  AND p.testRun = FALSE
            """)
    long countDistinctResultsByExerciseId(@Param("exerciseId") long exerciseId);

    /**
     * Retrieves the maximum feedback count for a given exercise.
     * <br>
     * This query calculates the maximum number of feedback occurrences across all feedback entries for a specific exercise.
     * It considers only the latest result per participation and excludes test runs.
     * <br>
     * Grouping is done by feedback detail text and test case name, and the maximum feedback count is returned.
     *
     * @param exerciseId The ID of the exercise for which the maximum feedback count is to be retrieved.
     * @return The maximum count of feedback occurrences for the given exercise.
     */
    // TODO: move this query to a more appropriate repository, either feedbackRepository or exerciseRepository
    @Query("""
            SELECT MAX(feedbackCounts.feedbackCount) FROM (
                SELECT COUNT(f.id) AS feedbackCount
                FROM ProgrammingExerciseStudentParticipation p
                INNER JOIN p.submissions s
                INNER JOIN s.results r ON r.id = (
                    SELECT MAX(sr.id)
                    FROM p.submissions ps
                    INNER JOIN ps.results sr
                    WHERE ps.participation.id = p.id
                )
                INNER JOIN r.feedbacks f
                WHERE p.exercise.id = :exerciseId
                    AND p.testRun = FALSE
                    AND f.positive = FALSE
                GROUP BY f.detailText, f.testCase.testName
            ) AS feedbackCounts
            """)
    long findMaxCountForExercise(@Param("exerciseId") long exerciseId);

    /**
     * Retrieves a paginated list of students affected by specific feedback entries for a given programming exercise.
     * <br>
     *
     * @param exerciseId  for which the affected student participation data is requested.
     * @param feedbackIds used to filter the participation to only those affected by specific feedback entries.
     * @return A {@link Page} of {@link FeedbackAffectedStudentDTO} objects, each representing a student affected by the feedback.
     */
    @Query("""
            SELECT DISTINCT new de.tum.cit.aet.artemis.assessment.dto.FeedbackAffectedStudentDTO(
                                p.id,
                                p.student.firstName,
                                p.student.lastName,
                                p.student.login,
                                p.repositoryUri
                            )
            FROM ProgrammingExerciseStudentParticipation p
            INNER JOIN p.submissions s
            INNER JOIN s.results r ON r.id = (
                SELECT MAX(r2.id)
                FROM Submission s2 JOIN s2.results r2
                WHERE s2.participation = p
            )
            INNER JOIN r.feedbacks f
            WHERE p.exercise.id = :exerciseId
                  AND f.id IN :feedbackIds
                  AND p.testRun = FALSE
            ORDER BY p.student.firstName ASC
            """)
    List<FeedbackAffectedStudentDTO> findAffectedStudentsByFeedbackIds(@Param("exerciseId") long exerciseId, @Param("feedbackIds") List<Long> feedbackIds);

    /**
     * Retrieves the logins of students affected by a specific feedback detail text in a given exercise.
     *
     * @param exerciseId   The ID of the exercise for which affected students are requested.
     * @param detailTexts  The feedback detail text to filter by.
     * @param testCaseName The name of the test case for which the feedback is given.
     * @return A list of student logins affected by the given feedback detail text in the specified exercise.
     */
    @Query("""
            SELECT DISTINCT p.student.login
            FROM ProgrammingExerciseStudentParticipation p
            INNER JOIN p.submissions s
            INNER JOIN s.results r ON r.id = (
                SELECT MAX(r2.id)
                FROM Submission s2 JOIN s2.results r2
                WHERE s2.participation = p
            )
            INNER JOIN r.feedbacks f
            WHERE p.exercise.id = :exerciseId
              AND f.detailText IN :detailTexts
              AND f.testCase.testName = :testCaseName
              AND p.testRun = FALSE
            """)
    List<String> findAffectedLoginsByFeedbackDetailText(@Param("exerciseId") long exerciseId, @Param("detailTexts") List<String> detailTexts,
            @Param("testCaseName") String testCaseName);

}
