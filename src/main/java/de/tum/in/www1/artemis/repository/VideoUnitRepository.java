package de.tum.in.www1.artemis.repository;

import static de.tum.in.www1.artemis.config.Constants.PROFILE_CORE;

import java.util.Optional;

import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Profile;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.in.www1.artemis.domain.lecture.VideoUnit;
import de.tum.in.www1.artemis.repository.base.ArtemisJpaRepository;

/**
 * Spring Data JPA repository for the Video Unit entity.
 */
@Profile(PROFILE_CORE)
@Repository
public interface VideoUnitRepository extends ArtemisJpaRepository<VideoUnit, Long> {

    @Query("""
            SELECT vu
            FROM VideoUnit vu
                LEFT JOIN FETCH vu.competencies
            WHERE vu.id = :videoUnitId
            """)
    Optional<VideoUnit> findByIdWithCompetencies(@Param("videoUnitId") long videoUnitId);

    @NotNull
    default VideoUnit findByIdWithCompetenciesElseThrow(long videoUnitId) {
        return getValueElseThrow(findByIdWithCompetencies(videoUnitId), videoUnitId);
    }
}
