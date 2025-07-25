package de.tum.cit.aet.artemis.programming.repository;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO;
import de.tum.cit.aet.artemis.buildagent.dto.DockerImageBuild;
import de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob;
import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.programming.domain.build.BuildJob;
import de.tum.cit.aet.artemis.programming.domain.build.BuildStatus;
import de.tum.cit.aet.artemis.programming.dto.BuildJobStatisticsDTO;

@Profile(PROFILE_CORE)
@Lazy
@Repository
public interface BuildJobRepository extends ArtemisJpaRepository<BuildJob, Long>, JpaSpecificationExecutor<BuildJob> {

    @EntityGraph(type = LOAD, attributePaths = { "result", "result.submission", "result.submission.participation", "result.submission.participation.exercise" })
    List<BuildJob> findWithDataByIdIn(List<Long> ids);

    @Query("""
            SELECT b.id
            FROM BuildJob b
            WHERE b.buildCompletionDate IS NOT NULL
            """)
    Slice<Long> findFinishedIds(Pageable pageable);

    // Cast to string is necessary. Otherwise, the query will fail on PostgreSQL.
    @Query("""
            SELECT b.id
            FROM BuildJob b
                LEFT JOIN Course c ON b.courseId = c.id
            WHERE b.buildCompletionDate IS NOT NULL
                AND (:buildStatus IS NULL OR b.buildStatus = :buildStatus)
                AND (:buildAgentAddress IS NULL OR b.buildAgentAddress = :buildAgentAddress)
                AND (CAST(:startDate AS string) IS NULL OR b.buildSubmissionDate >= :startDate)
                AND (CAST(:endDate AS string) IS NULL OR b.buildSubmissionDate <= :endDate)
                AND (
                  :searchTerm IS NULL
                  OR b.repositoryName LIKE CONCAT('%', :searchTerm, '%')
                  OR c.title LIKE CONCAT('%', :searchTerm, '%')
                )
                AND (:courseId IS NULL OR b.courseId = :courseId)
                AND (:durationLower IS NULL OR (b.buildCompletionDate - b.buildStartDate) >= :durationLower)
                AND (:durationUpper IS NULL OR (b.buildCompletionDate - b.buildStartDate) <= :durationUpper)

            """)
    Slice<Long> findFinishedIdsByFilterCriteria(@Param("buildStatus") BuildStatus buildStatus, @Param("buildAgentAddress") String buildAgentAddress,
            @Param("startDate") ZonedDateTime startDate, @Param("endDate") ZonedDateTime endDate, @Param("searchTerm") String searchTerm, @Param("courseId") Long courseId,
            @Param("durationLower") Duration durationLower, @Param("durationUpper") Duration durationUpper, Pageable pageable);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.buildagent.dto.DockerImageBuild(
                b.dockerImage,
                MAX(b.buildStartDate)
            )
            FROM BuildJob b
            GROUP BY b.dockerImage
            """)
    Set<DockerImageBuild> findAllLastBuildDatesForDockerImages();

    @Query("""
            SELECT new de.tum.cit.aet.artemis.buildagent.dto.ResultBuildJob(
                b.result.id,
                b.exerciseId,
                b.buildJobId
            )
            FROM BuildJob b
            WHERE b.participationId = :participationId
                AND b.result.id IS NOT NULL
            """)
    Set<ResultBuildJob> findBuildJobIdsWithResultForParticipationId(@Param("participationId") long participationId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.buildagent.dto.BuildJobResultCountDTO(
                b.buildStatus,
                COUNT(b.buildStatus)
            )
            FROM BuildJob b
            WHERE b.buildSubmissionDate >= :fromDateTime
                AND (:courseId IS NULL OR b.courseId = :courseId)
            GROUP BY b.buildStatus
            """)
    List<BuildJobResultCountDTO> getBuildJobsResultsStatistics(@Param("fromDateTime") ZonedDateTime fromDateTime, @Param("courseId") Long courseId);

    Optional<BuildJob> findByBuildJobId(String buildJobId);

    default BuildJob findByBuildJobIdElseThrow(String buildJobId) {
        return getValueElseThrow(findByBuildJobId(buildJobId));
    }

    /**
     * Get the number of build jobs for a list of exercise ids.
     *
     * @param courseId the id of the course
     * @return the number of build jobs
     */
    @Query("""
            SELECT COUNT(b)
            FROM BuildJob b
            WHERE b.courseId = :courseId
            """)
    long countBuildJobsByCourseId(@Param("courseId") long courseId);

    /**
     * Get the number of build jobs for a list of exercise ids (used for exams).
     *
     * @param exerciseIds the list of exercise ids
     * @return the number of build jobs
     */
    @Query("""
            SELECT COUNT(b)
            FROM BuildJob b
            WHERE b.exerciseId IN :exerciseIds
            """)
    long countBuildJobsByExerciseIds(@Param("exerciseIds") Collection<Long> exerciseIds);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.programming.dto.BuildJobStatisticsDTO(
                ROUND(AVG((b.buildCompletionDate - b.buildStartDate) BY SECOND)),
                COUNT(b),
                b.exerciseId
            )
            FROM BuildJob b
            WHERE b.exerciseId = :exerciseId
                AND b.buildStatus = de.tum.cit.aet.artemis.programming.domain.build.BuildStatus.SUCCESSFUL
            GROUP BY b.exerciseId
            """)
    BuildJobStatisticsDTO findBuildJobStatisticsByExerciseId(@Param("exerciseId") Long exerciseId);

    @Transactional
    @Modifying
    @Query("""
            UPDATE BuildJob b
            SET b.buildStatus = :newStatus
            WHERE b.buildJobId = :buildJobId
            """)
    void updateBuildJobStatus(@Param("buildJobId") String buildJobId, @Param("newStatus") BuildStatus newStatus);

    /**
     * Update the build job status and set the build start date if it is not set yet. The buildStartDate is required to calculate the statistics and the correctly display in the
     * build overview.
     * This is used to update missing jobs that do not have a build start date yet.
     *
     * @param buildJobId     the build job id
     * @param newStatus      the new build status
     * @param buildStartDate the build start date
     */
    @Transactional
    @Modifying
    @Query("""
            UPDATE BuildJob b
            SET b.buildStatus = :newStatus,
                b.buildStartDate = CASE WHEN b.buildStartDate IS NULL THEN :buildStartDate ELSE b.buildStartDate END
            WHERE b.buildJobId = :buildJobId
            """)
    void updateBuildJobStatusWithBuildStartDate(@Param("buildJobId") String buildJobId, @Param("newStatus") BuildStatus newStatus,
            @Param("buildStartDate") ZonedDateTime buildStartDate);

    /**
     * Find all build jobs with the given build status.
     *
     * @param statuses the list of build statuses
     * @return the list of build jobs
     */
    List<BuildJob> findAllByBuildStatusIn(List<BuildStatus> statuses);
}
