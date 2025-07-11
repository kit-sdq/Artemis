package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.annotation.Nullable;

import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.exam.api.ExamDateApi;
import de.tum.cit.aet.artemis.exam.api.StudentExamApi;
import de.tum.cit.aet.artemis.exam.config.ExamApiNotPresentException;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.ParticipationInterface;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.repository.ParticipationRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class ExerciseDateService {

    private final ParticipationRepository participationRepository;

    private final Optional<ExamDateApi> examDateApi;

    private final Optional<StudentExamApi> studentExamApi;

    public ExerciseDateService(ParticipationRepository participationRepository, Optional<ExamDateApi> examDateApi, Optional<StudentExamApi> studentExamApi) {
        this.participationRepository = participationRepository;
        this.examDateApi = examDateApi;
        this.studentExamApi = studentExamApi;
    }

    /**
     * Finds the latest individual due date for participants. If no individual due dates exist, then the exercise due date is returned.
     * Returns nothing if the exercise itself has no due date.
     *
     * @param exercise the exercise for which the latest due date should be returned.
     * @return the latest individual due date, or if not existing the exercise due date.
     */
    public Optional<ZonedDateTime> getLatestIndividualDueDate(Exercise exercise) {
        if (exercise.getDueDate() == null) {
            // early exit to avoid database call, same result would be produced
            // in Optional.ofNullable(exercise.getDueDate()) below
            return Optional.empty();
        }
        return participationRepository.findLatestIndividualDueDate(exercise.getId()).or(() -> Optional.ofNullable(exercise.getDueDate()));
    }

    /**
     * Finds the earliest individual due date for participants.
     * Returns null if the exercise itself has no due date.
     *
     * @param exercise the exercise for which the earliest due date should be returned.
     * @return the earliest individual due date, or if none exists the exercise due date.
     */
    public Optional<ZonedDateTime> getEarliestIndividualDueDate(Exercise exercise) {
        if (exercise.getDueDate() == null) {
            // early exit to avoid database call, same result would be produced
            // in Optional.ofNullable(exercise.getDueDate()) below
            return Optional.empty();
        }
        return participationRepository.findEarliestIndividualDueDate(exercise.getId()).or(() -> Optional.ofNullable(exercise.getDueDate()));
    }

    /**
     * Checks if submissions are no longer possible.
     * <p>
     * Checks for exam or course exercise, and if an individual due date is set for the given
     * participation or only a course-wide due date applies.
     *
     * @param participation in a course or exam exercise.
     * @return true, if the due date is in the past and submissions are no longer possible.
     */
    public boolean isAfterDueDate(ParticipationInterface participation) {
        return isAfterDueDate(participation, participation.getExercise());
    }

    /**
     * Checks if submissions are no longer possible.
     * <p>
     * Checks for exam or course exercise, and if an individual due date is set for the given
     * participation or only a course-wide due date applies.
     *
     * @param participation in a course or exam exercise.
     * @param exercise      the exercise for which the due date should be checked.
     * @return true, if the due date is in the past and submissions are no longer possible.
     */
    public boolean isAfterDueDate(ParticipationInterface participation, Exercise exercise) {
        if (exercise.isExamExercise()) {
            ExamDateApi api = examDateApi.orElseThrow(() -> new ExamApiNotPresentException(ExamDateApi.class));
            if (participation instanceof StudentParticipation studentParticipation) {
                return api.isIndividualExerciseWorkingPeriodOver(exercise.getExam(), studentParticipation);
            }
            else {
                return api.isExamWithGracePeriodOver(exercise.getExam());
            }
        }
        else {
            final ZonedDateTime now = ZonedDateTime.now();
            return getDueDate(participation).map(now::isAfter).orElse(false);
        }
    }

    /**
     * Checks if the due date for the given participation is in the future.
     *
     * @param participation in a course or exam exercise.
     * @return true, if the due date has not yet passed.
     */
    public boolean isBeforeDueDate(ParticipationInterface participation) {
        return !isAfterDueDate(participation);
    }

    /**
     * Checks if the current time is before the latest possible submission time.
     * If no due date is set, returns true (a due date infinitely far in the future is assumed).
     *
     * @param exercise for which this should be checked.
     * @return true, if the current time is before the due date.
     */
    public boolean isBeforeLatestDueDate(Exercise exercise) {
        final ZonedDateTime now = ZonedDateTime.now();
        return getLatestIndividualDueDate(exercise).map(now::isBefore).orElse(true);
    }

    /**
     * Checks if the current time is after the latest possible submission time.
     * If no due date is set, returns false (a due date infinitely far in the future is assumed).
     *
     * @param exercise for which this should be checked.
     * @return true, if the current time is after the due date.
     */
    public boolean isAfterLatestDueDate(Exercise exercise) {
        return !isBeforeLatestDueDate(exercise);
    }

    /**
     * Checks if due date is before now
     * The due date we use to check depending on whether it is present is individual -> exercise -> none
     * If no due date is set, returns an empty optional
     *
     * @param exercise for which this should be checked.
     * @return Optional of true, if the due date is before the current time.
     */
    public Optional<Boolean> isAfterEarliestDueDate(Exercise exercise) {
        final ZonedDateTime now = ZonedDateTime.now();
        return getEarliestIndividualDueDate(exercise).map(now::isAfter);
    }

    public Optional<Boolean> isBeforeEarliestDueDate(Exercise exercise) {
        return isAfterEarliestDueDate(exercise).map(x -> !x);
    }

    /**
     * Gets either the individual due date for a participation if present or else the exercise due date if present.
     *
     * @param participation of a student in an exercise.
     * @return the individual due date, or the exercise due date, or nothing.
     */
    public static Optional<ZonedDateTime> getDueDate(ParticipationInterface participation) {
        final Exercise exercise = participation.getExercise();

        if (participation.getIndividualDueDate() != null) {
            return Optional.of(participation.getIndividualDueDate());
        }
        else {
            return Optional.ofNullable(exercise.getDueDate());
        }
    }

    /**
     * Checks if the current time is after the assessment due date
     * and manual results can be published to the student.
     *
     * @param exercise to check the assessment due date
     * @return true if the assessment due date is in the past or if the exercise has no assessment due date.
     */
    public static boolean isAfterAssessmentDueDate(Exercise exercise) {
        if (exercise.isExamExercise()) {
            return exercise.getExam().resultsPublished();
        }
        return exercise.getAssessmentDueDate() == null || ZonedDateTime.now().isAfter(exercise.getAssessmentDueDate());
    }

    /**
     * Checks if the exercise has started.
     *
     * @param exercise The programming exercise.
     * @return True if the exercise has started, false otherwise.
     */
    public boolean hasExerciseStarted(Exercise exercise) {
        ZonedDateTime exerciseStartDate = exercise.getParticipationStartDate();
        return exerciseStartDate == null || exerciseStartDate.isBefore(ZonedDateTime.now());
    }

    /**
     * Return the individual due date for the exercise of the participation's user
     * <p>
     * For exam exercises, this depends on the StudentExam's working time
     *
     * @param exercise      that is possibly part of an exam
     * @param participation the participation of the student
     * @return the time from which on submissions are not allowed, for exercises that are not part of an exam, this is just the due date.
     */
    @Nullable
    public ZonedDateTime getIndividualDueDate(Exercise exercise, StudentParticipation participation) {
        if (exercise.isExamExercise()) {
            StudentExamApi api = studentExamApi.orElseThrow(() -> new ExamApiNotPresentException(StudentExamApi.class));
            var studentExam = api.findStudentExam(exercise, participation).orElse(null);
            if (studentExam == null) {
                return exercise.getDueDate();
            }
            return studentExam.getExam().getStartDate().plusSeconds(studentExam.getWorkingTime());
        }
        else {
            return ExerciseDateService.getDueDate(participation).orElse(null);
        }
    }
}
