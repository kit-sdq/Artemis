package de.tum.cit.aet.artemis.exam.service;

import static de.tum.cit.aet.artemis.core.config.Constants.EXAM_START_WAIT_TIME_MINUTES;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.exam.config.ExamEnabled;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.StudentExam;
import de.tum.cit.aet.artemis.exam.repository.ExamRepository;
import de.tum.cit.aet.artemis.exam.repository.StudentExamRepository;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;

@Conditional(ExamEnabled.class)
@Lazy
@Service
public class ExamDateService {

    private final ExamRepository examRepository;

    private final StudentExamRepository studentExamRepository;

    public ExamDateService(ExamRepository examRepository, StudentExamRepository studentExamRepository) {
        this.examRepository = examRepository;
        this.studentExamRepository = studentExamRepository;
    }

    /**
     * Returns if the exam is over by checking if the latest individual exam end date plus grace period has passed.
     * See {@link ExamDateService#getLatestIndividualExamEndDate}
     * <p>
     *
     * @param examId the id of the exam
     * @return true if the exam is over and the students cannot submit anymore
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public boolean isExamWithGracePeriodOver(Long examId) {
        final var exam = examRepository.findByIdElseThrow(examId);
        return isExamWithGracePeriodOver(exam);
    }

    /**
     * Returns if the exam is over by checking if the latest individual exam end date plus grace period has passed.
     * See {@link ExamDateService#getLatestIndividualExamEndDate}
     * <p>
     *
     * @param exam the exam
     * @return true if the exam is over and the students cannot submit anymore
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public boolean isExamWithGracePeriodOver(Exam exam) {
        var now = ZonedDateTime.now();
        return getLatestIndividualExamEndDate(exam).plusSeconds(exam.getGracePeriod()).isBefore(now);
    }

    /**
     * Returns <code>true</code> if the exercise working period is over, which is the case when:
     * <ul>
     * <li>For real exams, if no student can hand in their exam anymore</li>
     * <lI>For tests exams, if the student has handed in their own student exam</lI>
     * </ul>
     *
     * @param exercise             the course or exam exercise
     * @param studentParticipation used to find the related student exams for test exams
     * @return <code>true</code> if the exercise is over and students cannot submit (graded) solutions anymore, <code>false</code> otherwise
     * @throws EntityNotFoundException the given exercise is an exam exercise and the exam cannot be found
     */
    public boolean isExerciseWorkingPeriodOver(Exercise exercise, StudentParticipation studentParticipation) {
        if (!exercise.isExamExercise()) {
            throw new IllegalArgumentException("This function should only be used for exam exercises");
        }
        Exam exam = exercise.getExam();
        if (exam.isTestExam()) {
            return isIndividualExerciseWorkingPeriodOver(exam, studentParticipation);
        }
        return isExamWithGracePeriodOver(exam);
    }

    /**
     * Returns <code>true</code> if the exercise working period is over for a specific student participation.
     * This is the case as soon as the students hand in their results, or the individual due date is reached.
     *
     * @param exam                 the exam
     * @param studentParticipation used to find the related student exam
     * @return <code>true</code> if the working period is over, <code>false</code> otherwise
     */
    public boolean isIndividualExerciseWorkingPeriodOver(Exam exam, StudentParticipation studentParticipation) {
        if (studentParticipation.isTestRun()) {
            return false;
        }
        // Students can participate in a test exam multiple times, meaning there can be multiple student exams for a single exam.
        // For test exams, we aim to find the latest student exam.
        // For real exams, we aim to find the only existing student exam.
        Optional<StudentExam> optionalStudentExam = studentExamRepository.findFirstByExamIdAndUserIdOrderByCreatedDateDesc(exam.getId(),
                studentParticipation.getParticipant().getId());

        if (optionalStudentExam.isPresent()) {
            StudentExam studentExam = optionalStudentExam.get();
            return Boolean.TRUE.equals(studentExam.isSubmitted()) || studentExam.isEnded();
        }

        throw new IllegalStateException("No student exam found for student participation " + studentParticipation.getId());
    }

    /**
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param examId the id of the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    @NotNull
    public ZonedDateTime getLatestIndividualExamEndDate(Long examId) {
        final var exam = examRepository.findByIdElseThrow(examId);
        return getLatestIndividualExamEndDate(exam);
    }

    /**
     * Returns the latest individual exam end date as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, the exam end date is returned.
     *
     * @param exam the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     */
    @NotNull
    public ZonedDateTime getLatestIndividualExamEndDate(Exam exam) {
        var maxWorkingTime = studentExamRepository.findMaxWorkingTimeByExamId(exam.getId());
        return maxWorkingTime.map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).orElse(exam.getEndDate());
    }

    /**
     * Returns the latest individual exam end date plus the grace period as determined by the working time of the student exams and exam grace period.
     * <p>
     * If no student exams are available, the exam end date plus grace period is returned.
     *
     * @param exam the exam
     * @return the latest end date or the exam end date if no student exams are found. May return <code>null</code>, if the exam has no start/end date.
     */
    public ZonedDateTime getLatestIndividualExamEndDateWithGracePeriod(Exam exam) {
        ZonedDateTime latestEndDate = getLatestIndividualExamEndDate(exam);
        if (latestEndDate == null) {
            return null;
        }
        int gracePeriodInSeconds = Objects.requireNonNullElse(exam.getGracePeriod(), 0);
        return latestEndDate.plusSeconds(gracePeriodInSeconds);
    }

    /**
     * Returns all individual exam end dates as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, an empty set returned.
     *
     * @param examId the id of the exam
     * @return a set of all end dates. May return an empty set, if the exam has no start/end date or student exams cannot be found.
     * @throws EntityNotFoundException if no exam with the given examId can be found
     */
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Long examId) {
        final var exam = examRepository.findByIdElseThrow(examId);
        return getAllIndividualExamEndDates(exam);
    }

    /**
     * Returns all individual exam end dates as determined by the working time of the student exams.
     * <p>
     * If no student exams are available, an empty set returned.
     *
     * @param exam the exam
     * @return a set of all end dates. May return an empty set, if the exam has no start/end date or student exams cannot be found.
     */
    @Nullable
    public Set<ZonedDateTime> getAllIndividualExamEndDates(Exam exam) {
        if (exam.getStartDate() == null) {
            return null;
        }
        var workingTimes = studentExamRepository.findAllDistinctWorkingTimesByExamId(exam.getId());
        return workingTimes.stream().map(timeInSeconds -> exam.getStartDate().plusSeconds(timeInSeconds)).collect(Collectors.toSet());
    }

    /**
     * Returns the unlock date for the exam programming exercise.
     * <p>
     * The unlock date is the exam start date minus a certain amount of time to ensure that the exam is unlocked before the start date.
     *
     * @param exercise the programming exercise
     * @return the unlock date or <code>null</code> if the exercise is not an exam exercise
     */
    @Nullable
    public static ZonedDateTime getExamProgrammingExerciseUnlockDate(ProgrammingExercise exercise) {
        // TODO: can we guarantee that this is an exam exercise to avoid the null check and return?
        if (!exercise.isExamExercise()) {
            return null;
        }
        return getExamProgrammingExerciseUnlockDate(exercise.getExerciseGroup().getExam());
    }

    @NotNull
    public static ZonedDateTime getExamProgrammingExerciseUnlockDate(Exam exam) {
        // using start date minus 5 minutes here because unlocking will take some time.
        return exam.getStartDate().minusMinutes(EXAM_START_WAIT_TIME_MINUTES);
    }
}
