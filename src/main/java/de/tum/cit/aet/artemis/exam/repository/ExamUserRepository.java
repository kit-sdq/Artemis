package de.tum.cit.aet.artemis.exam.repository;

import static org.springframework.data.jpa.repository.EntityGraph.EntityGraphType.LOAD;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import de.tum.cit.aet.artemis.core.repository.base.ArtemisJpaRepository;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.ExamUser;
import de.tum.cit.aet.artemis.exam.dto.ExamUserAttendanceCheckDTO;

@Conditional(ExamEnabled.class)
@Lazy
@Repository
public interface ExamUserRepository extends ArtemisJpaRepository<ExamUser, Long> {

    @Query("""
            SELECT eu
            FROM ExamUser eu
            WHERE eu.exam.id = :examId
               AND eu.user.id = :userId
            """)
    Optional<ExamUser> findByExamIdAndUserId(@Param("examId") long examId, @Param("userId") long userId);

    @EntityGraph(type = LOAD, attributePaths = { "exam" })
    Optional<ExamUser> findWithExamById(long examUserId);

    List<ExamUser> findAllByExamId(long examId);

    @Query("""
            SELECT new de.tum.cit.aet.artemis.exam.dto.ExamUserAttendanceCheckDTO(
                examUser.id,
                examUser.studentImagePath,
                examUser.user.login,
                examUser.user.registrationNumber,
                examUser.signingImagePath,
                studentExams.started,
                studentExams.submitted
            )
            FROM ExamUser examUser
                LEFT JOIN examUser.exam exam
                LEFT JOIN exam.studentExams studentExams ON studentExams.user.id = examUser.user.id
            WHERE exam.id = :examId
                AND studentExams.started = TRUE
                AND (examUser.signingImagePath IS NULL
                    OR examUser.signingImagePath = ''
                    OR examUser.didCheckImage = FALSE
                    OR examUser.didCheckLogin = FALSE
                    OR examUser.didCheckRegistrationNumber = FALSE
                    OR examUser.didCheckName = FALSE
                )
            """)
    Set<ExamUserAttendanceCheckDTO> findAllExamUsersWhoDidNotSign(@Param("examId") long examId);

    @Query("""
            SELECT COUNT(examUser) > 0
            FROM ExamUser examUser
            WHERE examUser.exam.id = :examId
                AND examUser.user.login = :login
                AND examUser.signingImagePath IS NOT NULL
                AND examUser.signingImagePath != ''
                AND examUser.didCheckImage = TRUE
                AND examUser.didCheckLogin = TRUE
                AND examUser.didCheckRegistrationNumber = TRUE
                AND examUser.didCheckName = TRUE
            """)
    boolean isAttendanceChecked(@Param("examId") long examId, @Param("login") String login);

    @Query("""
            SELECT COUNT (DISTINCT eu)
            FROM ExamUser eu
            WHERE eu.exam.id = :examId
            """)
    long countByExamId(@Param("examId") long examId);
}
