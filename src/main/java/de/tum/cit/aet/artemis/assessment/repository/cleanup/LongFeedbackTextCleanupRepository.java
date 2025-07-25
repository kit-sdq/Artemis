package de.tum.cit.aet.artemis.assessment.repository.cleanup;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.LongFeedbackText;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;

/**
 * Spring Data JPA repository for cleaning up old and orphaned long feedback text entries.
 * THE FOLLOWING METHODS ARE USED FOR CLEANUP PURPOSES AND SHOULD NOT BE USED IN OTHER CASES
 */
@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface LongFeedbackTextCleanupRepository extends ArtemisJpaRepository<LongFeedbackText, Long> {

    /**
     * Deletes {@link LongFeedbackText} entries linked to {@link Feedback} where the associated
     * {@link Result} has no submission or its submission has no participation.
     *
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LongFeedbackText lft
            WHERE lft.feedback.id IN (
                SELECT f.id
                FROM Feedback f
                    LEFT JOIN f.result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                WHERE s IS NULL
                    OR p IS NULL
            )
            """)
    int deleteLongFeedbackTextForOrphanResult();

    /**
     * Counts {@link LongFeedbackText} entries linked to {@link Feedback} where the associated
     * {@link Result} has no submission or its submission has no participation.
     *
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(lft)
            FROM LongFeedbackText lft
                JOIN lft.feedback f
                LEFT JOIN f.result r
                LEFT JOIN r.submission s
                LEFT JOIN s.participation p
            WHERE s IS NULL
                OR p IS NULL
            """)
    int countLongFeedbackTextForOrphanResult();

    /**
     * Deletes {@link LongFeedbackText} linked to {@link Feedback} with a {@code null} result.
     *
     * @return the number of deleted {@link LongFeedbackText} entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LongFeedbackText lft
            WHERE lft.feedback IN (
                SELECT f
                FROM Feedback f
                WHERE f.result IS NULL
                )
            """)
    int deleteLongFeedbackTextForOrphanedFeedback();

    /**
     * Counts {@link LongFeedbackText} linked to {@link Feedback} with a {@code null} result.
     *
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(lft)
            FROM LongFeedbackText lft
            WHERE lft.feedback IN (
                SELECT f
                FROM Feedback f
                WHERE f.result IS NULL
                )
            """)
    int countLongFeedbackTextForOrphanedFeedback();

    /**
     * Deletes {@link LongFeedbackText} entries associated with rated {@link Result} (accessed via its submission)
     * that are not the latest rated result for a {@link Participation}, within courses conducted between the specified date range.
     * This query removes old long feedback text that is not part of the latest rated results, for courses whose
     * end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LongFeedbackText lft
            WHERE lft.feedback IN (
                SELECT f
                FROM Feedback f
                    LEFT JOIN f.result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE f.result.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = TRUE
                    )
                    AND r.rated = TRUE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int deleteLongFeedbackTextForRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Counts {@link LongFeedbackText} entries associated with rated {@link Result} (accessed via its submission)
     * that are not the latest rated result for a {@link Participation}, within courses conducted between the specified date range.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of entities that would be deleted
     */
    @Query("""
            SELECT COUNT(lft)
            FROM LongFeedbackText lft
            WHERE lft.feedback IN (
                SELECT f
                FROM Feedback f
                    LEFT JOIN f.result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE f.result.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = TRUE
                    )
                    AND r.rated = TRUE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int countLongFeedbackTextForRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Deletes {@link LongFeedbackText} entries linked to non-rated {@link Feedback} (accessed via its result's submission)
     * that are not the latest non-rated result for a {@link Participation}, where the associated course's start and end dates
     * are between the specified date range.
     * This query deletes long feedback text for feedback associated with non-rated results, within courses whose
     * end date is before {@code deleteTo} and start date is after {@code deleteFrom}.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of deleted entities
     */
    @Modifying
    @Transactional // ok because of delete
    @Query("""
            DELETE FROM LongFeedbackText lft
            WHERE lft.feedback IN (
                SELECT f
                FROM Feedback f
                    LEFT JOIN f.result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE f.result.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = FALSE
                    )
                    AND r.rated = FALSE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int deleteLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);

    /**
     * Counts {@link LongFeedbackText} entries linked to non-rated {@link Feedback} (accessed via its result's submission)
     * that are not the latest non-rated result for a {@link Participation}, where the associated course's start and end dates
     * are between the specified date range.
     *
     * @param deleteFrom the start date for selecting courses
     * @param deleteTo   the end date for selecting courses
     * @return the number of entities that would be deleted upon execution of the cleanup operation
     */
    @Query("""
            SELECT COUNT(lft)
            FROM LongFeedbackText lft
            WHERE lft.feedback IN (
                SELECT f
                FROM Feedback f
                    LEFT JOIN f.result r
                    LEFT JOIN r.submission s
                    LEFT JOIN s.participation p
                    LEFT JOIN p.exercise e
                    LEFT JOIN e.course c
                WHERE f.result.id NOT IN (
                    SELECT MAX(r2.id)
                    FROM Result r2
                        LEFT JOIN r2.submission s2
                        LEFT JOIN s2.participation p2
                    WHERE p2.id = p.id
                        AND r2.rated = FALSE
                    )
                    AND r.rated = FALSE
                    AND c.endDate < :deleteTo
                    AND c.startDate > :deleteFrom
                )
            """)
    int countLongFeedbackTextForNonRatedResultsWhereCourseDateBetween(@Param("deleteFrom") ZonedDateTime deleteFrom, @Param("deleteTo") ZonedDateTime deleteTo);
}
