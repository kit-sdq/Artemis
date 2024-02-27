package de.tum.in.www1.artemis.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import de.tum.in.www1.artemis.domain.Exercise;
import de.tum.in.www1.artemis.domain.User;
import de.tum.in.www1.artemis.domain.scores.StudentScore;
import de.tum.in.www1.artemis.web.rest.dto.score.StudentScoreSum;

@Repository
public interface StudentScoreRepository extends JpaRepository<StudentScore, Long> {

    @Transactional // ok because of delete
    @Modifying
    void deleteAllByUserId(long userId);

    @EntityGraph(type = LOAD, attributePaths = { "user", "exercise" })
    Optional<StudentScore> findByExercise_IdAndUser_Id(long exerciseId, long userId);

    @Query("""
            SELECT new de.tum.in.www1.artemis.web.rest.dto.score.StudentScoreSum(u.id, COALESCE(SUM(s.lastRatedPoints), 0))
            FROM StudentScore s
                LEFT JOIN s.user u
            WHERE s.exercise IN :exercises
            GROUP BY u.id
            """)
    Set<StudentScoreSum> getAchievedPointsOfStudents(@Param("exercises") Set<Exercise> exercises);

    @Query("""
            SELECT s
            FROM StudentScore s
            WHERE s.user = :user
                AND s.exercise IN :exercises
            """)
    List<StudentScore> findAllByExercisesAndUser(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Query("""
            SELECT s
            FROM StudentScore s
                LEFT JOIN FETCH s.exercise ex
            WHERE ex IN :exercises
                AND s.user = :user
            """)
    List<StudentScore> findAllByExerciseAndUserWithEagerExercise(@Param("exercises") Set<Exercise> exercises, @Param("user") User user);

    @Transactional // ok because of delete
    @Modifying
    void deleteByExerciseAndUser(Exercise exercise, User user);
}
