package de.tum.cit.aet.artemis.programming.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.CategoryState;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.TestConstants;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.domain.ExerciseGroup;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionType;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.SubmissionTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTask;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.Repository;
import de.tum.cit.aet.artemis.programming.domain.submissionpolicy.SubmissionPolicy;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.repository.BuildPlanRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseBuildConfigRepository;
import de.tum.cit.aet.artemis.programming.repository.SolutionProgrammingExerciseParticipationRepository;
import de.tum.cit.aet.artemis.programming.repository.StaticCodeAnalysisCategoryRepository;
import de.tum.cit.aet.artemis.programming.repository.SubmissionPolicyRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTaskTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingSubmissionTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.TemplateProgrammingExerciseParticipationTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to programming exercises for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ProgrammingExerciseUtilService {

    private static final ZonedDateTime PAST_TIMESTAMP = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime FUTURE_FUTURE_TIMESTAMP = ZonedDateTime.now().plusDays(2);

    @Value("${artemis.version-control.default-branch:main}")
    protected String defaultBranch;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCRepoPath;

    @Autowired
    private TemplateProgrammingExerciseParticipationTestRepository templateProgrammingExerciseParticipationTestRepo;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseBuildConfigRepository programmingExerciseBuildConfigRepository;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private SubmissionTestRepository submissionRepository;

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository testCaseRepository;

    @Autowired
    private StaticCodeAnalysisCategoryRepository staticCodeAnalysisCategoryRepository;

    @Autowired
    private BuildPlanRepository buildPlanRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private SubmissionPolicyRepository submissionPolicyRepository;

    @Autowired
    private ProgrammingSubmissionTestRepository programmingSubmissionRepo;

    @Autowired
    private ResultTestRepository resultRepo;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepo;

    @Autowired
    private ProgrammingExerciseTaskTestRepository programmingExerciseTaskRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private GitService gitService;

    @Autowired
    private SolutionProgrammingExerciseParticipationRepository solutionProgrammingExerciseParticipationRepository;

    public ProgrammingExercise createSampleProgrammingExercise() {
        return createSampleProgrammingExercise("Title", "Shortname");
    }

    /**
     * Generates a programming exercise for the given course. Configures only the exercise's schedule, no other properties.
     *
     * @param course            The course of the exercise.
     * @param releaseDate       The release date of the exercise.
     * @param startDate         The start date of the exercise.
     * @param dueDate           The due date of the exercise.
     * @param assessmentDueDate The assessment due date of the exercise.
     * @return The newly generated programming exercise.
     */
    public ProgrammingExercise createProgrammingExercise(Course course, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate,
            ZonedDateTime assessmentDueDate) {
        ProgrammingExercise programmingExercise = ProgrammingExerciseFactory.generateProgrammingExercise(releaseDate, startDate, dueDate, assessmentDueDate, course);
        return programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Create an example programming exercise
     *
     * @return the created programming exercise
     */
    public ProgrammingExercise createSampleProgrammingExercise(String title, String shortName) {
        var programmingExercise = new ProgrammingExercise();
        programmingExercise.setTitle(title);
        programmingExercise.setShortName(shortName);
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(0.0);
        programmingExercise.setGradingInstructions("Grading instructions");
        programmingExercise.setProblemStatement("Problem statement");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        return programmingExercise;
    }

    /**
     * Creates and saves a course with an exam and an exercise group with a programming exercise. Test cases are added to this programming exercise.
     *
     * @return The newly created programming exercise with test cases.
     */
    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases() {
        ProgrammingExercise programmingExercise = addCourseExamExerciseGroupWithOneProgrammingExercise();
        addTestCasesToProgrammingExercise(programmingExercise);
        return programmingExercise;
    }

    /**
     * Creates and saves a course with an exam and an exercise group with a programming exercise. The provided title and short name are used for the exercise and test cases are
     * added.
     *
     * @param title                      The title of the exercise.
     * @param shortName                  The short name of the exercise.
     * @param startDateBeforeCurrentTime True, if the start date of the created Exam with a programming exercise should be before the current time, needed for examLiveEvent tests
     * @return The newly created programming exercise with test cases.
     */
    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise(String title, String shortName, boolean startDateBeforeCurrentTime) {
        ExerciseGroup exerciseGroup;
        if (startDateBeforeCurrentTime) {
            exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true, true);
        }
        else {
            exerciseGroup = examUtilService.addExerciseGroupWithExamAndCourse(true);
        }
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exerciseGroup);
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, shortName, title, false);

        var savedBuildConfig = programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExercise.setBuildConfig(savedBuildConfig);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    /**
     * Creates and saves course with an exam and an exercise group with a programming exercise. <code>Testtitle</code> is the title and <code>TESTEXFOREXAM</code> the short name of
     * the exercise.
     *
     * @return The newly created exam programming exercise.
     */
    public ProgrammingExercise addCourseExamExerciseGroupWithOneProgrammingExercise() {
        return addCourseExamExerciseGroupWithOneProgrammingExercise("Testtitle", "TESTEXFOREXAM", false);
    }

    /**
     * Adds a programming exercise into the exerciseGroupNumber-th exercise group of the provided exam.
     * exerciseGroupNumber must be smaller than the number of exercise groups!
     *
     * @param exam                The exam to which the exercise should be added.
     * @param exerciseGroupNumber Used as an index into which exercise group of the exam the programming exercise should be added. Has to be smaller than the number of exercise
     *                                groups!
     * @return The newly created exam programming exercise.
     */
    public ProgrammingExercise addProgrammingExerciseToExam(Exam exam, int exerciseGroupNumber) {
        ProgrammingExercise programmingExercise = new ProgrammingExercise();
        programmingExercise.setExerciseGroup(exam.getExerciseGroups().get(exerciseGroupNumber));
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, "TESTEXFOREXAM", "Testtitle", false);

        var savedBuildConfig = programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExercise.setBuildConfig(savedBuildConfig);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExercise = programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        exam.getExerciseGroups().get(exerciseGroupNumber).addExercise(programmingExercise);
        examRepository.save(exam);

        return programmingExercise;
    }

    /**
     * Creates and saves a course with an exam and an exercise group with a programming exercise.
     *
     * @param visibleDate        The visible date of the exam.
     * @param startDate          The start date of the exam.
     * @param endDate            The end date of the exam.
     * @param publishResultsDate The publish results date of the exam.
     * @param userLogin          The login of the user for the student exam.
     * @param workingTime        The working time of the student exam in seconds.
     * @return The newly created exam programming exercise.
     */
    public ProgrammingExercise addCourseExamExerciseGroupWithProgrammingExerciseAndExamDates(ZonedDateTime visibleDate, ZonedDateTime startDate, ZonedDateTime endDate,
            ZonedDateTime publishResultsDate, String userLogin, int workingTime) {
        var programmingExercise = this.addCourseExamExerciseGroupWithOneProgrammingExercise();
        var exam = programmingExercise.getExerciseGroup().getExam();
        examUtilService.setVisibleStartAndEndDateOfExam(exam, visibleDate, startDate, endDate);
        exam.setPublishResultsDate(publishResultsDate);
        examRepository.save(exam);
        var studentExam = examUtilService.addStudentExamWithUserAndWorkingTime(exam, userLogin, workingTime);
        examUtilService.addExerciseToStudentExam(studentExam, programmingExercise);
        return programmingExercise;
    }

    /**
     * Creates and saves an already submitted programming submission done manually.
     *
     * @param participation The exercise participation.
     * @param buildFailed   True, if the submission resulted in a build failed.
     * @param commitHash    The commit hash of the submission.
     * @return The newly created programming submission.
     */
    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed, String commitHash) {
        ProgrammingSubmission programmingSubmission = ParticipationFactory.generateProgrammingSubmission(true);
        programmingSubmission.setBuildFailed(buildFailed);
        programmingSubmission.type(SubmissionType.MANUAL).submissionDate(ZonedDateTime.now());
        programmingSubmission.setCommitHash(commitHash);
        programmingSubmission.setParticipation(participation);
        return submissionRepository.save(programmingSubmission);
    }

    /**
     * Creates and saves an already submitted programming submission done manually. <code>9b3a9bd71a0d80e5bbc42204c319ed3d1d4f0d6d</code> is used as the commit hash.
     *
     * @param participation The exercise participation.
     * @param buildFailed   True, if the submission resulted in a build failed.
     * @return The newly created programming submission.
     */
    public ProgrammingSubmission createProgrammingSubmission(Participation participation, boolean buildFailed) {
        return createProgrammingSubmission(participation, buildFailed, TestConstants.COMMIT_HASH_STRING);
    }

    /**
     * Creates and saves a course with a programming exercise with static code analysis and test wise coverage disabled, java as the programming language.
     * Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise() {
        return addCourseWithOneProgrammingExercise(false);
    }

    /**
     * Creates and saves a course with a programming exercise with test wise coverage disabled and java as the programming language.
     * Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, ProgrammingLanguage.JAVA);
    }

    /**
     * Creates and saves a course with a programming exercise with test wise coverage disabled and java as the programming language.
     *
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param title                    The title of the exercise.
     * @param shortName                The short name of the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, String title, String shortName) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, ProgrammingLanguage.JAVA, title, shortName);
    }

    /**
     * Creates and saves a course with a programming exercise. Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param programmingLanguage      The programming language fo the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, ProgrammingLanguage programmingLanguage) {
        return addCourseWithOneProgrammingExercise(enableStaticCodeAnalysis, programmingLanguage, "Programming", "TSTEXC");
    }

    /**
     * Creates and saves a course with a programming exercise.
     *
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param programmingLanguage      The programming language fo the exercise.
     * @param title                    The title of the exercise.
     * @param shortName                The short name of the exercise.
     * @return The created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExercise(boolean enableStaticCodeAnalysis, ProgrammingLanguage programmingLanguage, String title, String shortName) {
        var course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);
        addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, programmingLanguage, title, shortName, null);
        course = courseRepo.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(course.getId());
        for (var exercise : course.getExercises()) {
            if (exercise instanceof ProgrammingExercise) {
                course.getExercises().remove(exercise);
                course.addExercises(programmingExerciseRepository.getProgrammingExerciseWithBuildConfigElseThrow((ProgrammingExercise) exercise));
            }
        }
        return course;
    }

    /**
     * Adds a java programming exercise with disabled test wise code coverage and static code analysis to the given course.
     *
     * @param course The course to which the exercise should be added.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course) {
        return addProgrammingExerciseToCourse(course, false);
    }

    /**
     * Adds a java programming exercise with disabled test wise code coverage analysis to the given course.
     *
     * @param course                   The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, ProgrammingLanguage.JAVA);
    }

    /**
     * Adds a java programming exercise with disabled test wise code coverage analysis to the given course.
     *
     * @param course                   The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param assessmentDueDate        The assessment due date of the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, ZonedDateTime assessmentDueDate) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, ProgrammingLanguage.JAVA, assessmentDueDate);
    }

    /**
     * Adds a programming exercise to the given course. Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the exercise.
     *
     * @param course                   The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param programmingLanguage      The programming language used in the exercise.
     * @param assessmentDueDate        The assessment due date of the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, ProgrammingLanguage programmingLanguage,
            ZonedDateTime assessmentDueDate) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, programmingLanguage, "Programming", "TSTEXC", assessmentDueDate);
    }

    /**
     * Adds a programming exercise without an assessment due date to the given course. Uses <code>Programming</code> as the title and <code>TSTEXC</code> as the short name of the
     * exercise.
     *
     * @param course                   The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param programmingLanguage      The programming language used in the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, ProgrammingLanguage programmingLanguage) {
        return addProgrammingExerciseToCourse(course, enableStaticCodeAnalysis, programmingLanguage, "Programming", "TSTEXC", null);
    }

    /**
     * Adds a programming exercise to the given course.
     *
     * @param course                   The course to which the exercise should be added.
     * @param enableStaticCodeAnalysis True, if the static code analysis should be enabled for the exercise.
     * @param programmingLanguage      The programming language used in the exercise.
     * @param title                    The title of the exercise.
     * @param shortName                The short name of the exercise.
     * @param assessmentDueDate        The assessment due date of the exercise.
     * @return The programming exercise which was added to the course.
     */
    public ProgrammingExercise addProgrammingExerciseToCourse(Course course, boolean enableStaticCodeAnalysis, ProgrammingLanguage programmingLanguage, String title,
            String shortName, ZonedDateTime assessmentDueDate) {
        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, shortName, title, enableStaticCodeAnalysis, programmingLanguage);
        programmingExercise.setAssessmentDueDate(assessmentDueDate);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        programmingExercise.setBuildConfig(programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig()));
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);

        return programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
    }

    /**
     * Creates and saves a course with a programming exercise with the given title.
     *
     * @param programmingExerciseTitle The title of the exercise.
     * @param scaActive                True, if static code analysis should be enabled.
     * @return The newly created course with a programming exercise.
     */
    public Course addCourseWithNamedProgrammingExercise(String programmingExerciseTitle, boolean scaActive) {
        var course = CourseFactory.generateCourse(null, PAST_TIMESTAMP, FUTURE_FUTURE_TIMESTAMP, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        course = courseRepo.save(course);

        var programmingExercise = (ProgrammingExercise) new ProgrammingExercise().course(course);
        ProgrammingExerciseFactory.populateUnreleasedProgrammingExercise(programmingExercise, "TSTEXC", programmingExerciseTitle, scaActive);
        programmingExercise.setPresentationScoreEnabled(course.getPresentationScore() != 0);

        var savedBuildConfig = programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExercise.setBuildConfig(savedBuildConfig);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        course.addExercises(programmingExercise);
        programmingExercise = programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);

        return courseRepo.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(course.getId());
    }

    /**
     * Creates and saves a course with a java programming exercise with static code analysis enabled.
     *
     * @return The newly created programming exercise.
     */
    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories() {
        return addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage.JAVA);
    }

    /**
     * Creates and saves a course with a programming exercise with static code analysis enabled.
     *
     * @param programmingLanguage The programming language of the exercise.
     * @return The newly created programming exercise.
     */
    public ProgrammingExercise addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories(ProgrammingLanguage programmingLanguage) {
        Course course = addCourseWithOneProgrammingExercise(true, programmingLanguage);
        ProgrammingExercise programmingExercise = ExerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        programmingExercise = programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithBuildConfigById(programmingExercise.getId()).orElseThrow();
        addStaticCodeAnalysisCategoriesToProgrammingExercise(programmingExercise);

        return programmingExercise;
    }

    /**
     * Adds 4 static code analysis categories to the given programming exercise. 2 are graded, 1 is inactive and 1 is feedback.
     *
     * @param programmingExercise The programming exercise to which static code analysis categories should be added.
     */
    public void addStaticCodeAnalysisCategoriesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        if (programmingExercise.getBuildConfig() == null) {
            programmingExercise = programmingExerciseRepository.findWithBuildConfigById(programmingExercise.getId()).orElseThrow();
        }
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        var category1 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Bad Practice", CategoryState.GRADED, 3D, 10D);
        var category2 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Code Style", CategoryState.GRADED, 5D, 10D);
        var category3 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Miscellaneous", CategoryState.INACTIVE, 2D, 10D);
        var category4 = ProgrammingExerciseFactory.generateStaticCodeAnalysisCategory(programmingExercise, "Potential Bugs", CategoryState.FEEDBACK, 5D, 20D);
        var categories = staticCodeAnalysisCategoryRepository.saveAll(List.of(category1, category2, category3, category4));
        programmingExercise.setStaticCodeAnalysisCategories(new HashSet<>(categories));
    }

    /**
     * Creates and saves a course with a programming exercise and test cases.
     *
     * @return The newly created course with a programming exercise.
     */
    public Course addCourseWithOneProgrammingExerciseAndTestCases() {
        Course course = addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = ExerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), "Programming");
        addTestCasesToProgrammingExercise(programmingExercise);
        return courseRepo.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(course.getId());
    }

    /**
     * Creates and saves a course with a named programming exercise and test cases.
     *
     * @param programmingExerciseTitle The title of the programming exercise.
     */
    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle) {
        addCourseWithNamedProgrammingExerciseAndTestCases(programmingExerciseTitle, false);
    }

    /**
     * Creates and saves a course with a named programming exercise and test cases.
     *
     * @param programmingExerciseTitle The title of the programming exercise.
     * @param scaActive                True, if the static code analysis should be activated.
     */
    public void addCourseWithNamedProgrammingExerciseAndTestCases(String programmingExerciseTitle, boolean scaActive) {
        Course course = addCourseWithNamedProgrammingExercise(programmingExerciseTitle, scaActive);
        ProgrammingExercise programmingExercise = ExerciseUtilService.findProgrammingExerciseWithTitle(course.getExercises(), programmingExerciseTitle);

        addTestCasesToProgrammingExercise(programmingExercise);

        courseRepo.findById(course.getId()).orElseThrow();
    }

    /**
     * Adds 3 test cases to the given programming exercise. 2 are always visible and 1 is visible after due date. The test cases are weighted differently.
     *
     * @param programmingExercise The programming exercise to which test cases should be added.
     * @return The created programming exercise test cases.
     */
    public List<ProgrammingExerciseTestCase> addTestCasesToProgrammingExercise(ProgrammingExercise programmingExercise) {
        // Clean up existing test cases
        testCaseRepository.deleteAll(testCaseRepository.findByExerciseId(programmingExercise.getId()));

        List<ProgrammingExerciseTestCase> testCases = new ArrayList<>();
        testCases.add(new ProgrammingExerciseTestCase().testName("test1").weight(1.0).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("test2").weight(2.0).active(false).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1D)
                .bonusPoints(0D));
        testCases.add(new ProgrammingExerciseTestCase().testName("test3").weight(3.0).active(true).exercise(programmingExercise).visibility(Visibility.AFTER_DUE_DATE)
                .bonusMultiplier(1D).bonusPoints(0D));
        testCaseRepository.saveAll(testCases);

        return testCases;
    }

    /**
     * Adds an active test case to the given programming exercise. The test case is always visible.
     *
     * @param programmingExercise The programming exercise to which a test case should be added.
     * @param testName            The name of the test case.
     * @return The created programming exercise test case.
     */
    public ProgrammingExerciseTestCase addTestCaseToProgrammingExercise(ProgrammingExercise programmingExercise, String testName) {
        var testCase = new ProgrammingExerciseTestCase().testName(testName).weight(1.).active(true).exercise(programmingExercise).visibility(Visibility.ALWAYS).bonusMultiplier(1.)
                .bonusPoints(0.);
        return testCaseRepository.save(testCase);
    }

    /**
     * Adds build plan and build plan access secret to the given programming exercise.
     *
     * @param programmingExercise The exercise to which the build plan should be added.
     * @param buildPlan           The build plan script.
     */
    public void addBuildPlanAndSecretToProgrammingExercise(ProgrammingExercise programmingExercise, String buildPlan) {
        buildPlanRepository.setBuildPlanForExercise(buildPlan, programmingExercise);
        programmingExercise.getBuildConfig().generateAndSetBuildPlanAccessSecret();
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());

        var buildPlanOptional = buildPlanRepository.findByProgrammingExercises_IdWithProgrammingExercises(programmingExercise.getId());
        assertThat(buildPlanOptional).isPresent();
        assertThat(buildPlanOptional.get().getBuildPlan()).as("build plan is set").isNotNull();
        assertThat(programmingExercise.getBuildConfig().getBuildPlanAccessSecret()).as("build plan access secret is set").isNotNull();
    }

    /**
     * Creates, saves and adds an auxiliary repository to the given programming exercise.
     *
     * @param programmingExercise The exercise to which the auxiliary repository should be added.
     * @return The newly created auxiliary repository.
     */
    public AuxiliaryRepository addAuxiliaryRepositoryToExercise(ProgrammingExercise programmingExercise) {
        AuxiliaryRepository repository = new AuxiliaryRepository();
        repository.setName("auxrepo");
        repository.setDescription("Description");
        repository.setCheckoutDirectory("assignment/src");
        repository = auxiliaryRepositoryRepository.save(repository);
        programmingExercise.setAuxiliaryRepositories(List.of(repository));
        repository.setExercise(programmingExercise);
        programmingExerciseRepository.save(programmingExercise);
        return repository;
    }

    /**
     * Adds submission policy to a programming exercise and saves the exercise.
     *
     * @param policy              The submission policy which should be added to the exercise.
     * @param programmingExercise The exercise to which the submission policy should be added.
     */
    public void addSubmissionPolicyToExercise(SubmissionPolicy policy, ProgrammingExercise programmingExercise) {
        policy = submissionPolicyRepository.save(policy);
        programmingExercise.setSubmissionPolicy(policy);
        programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Adds programming submission to provided programming exercise. The provided login is used to access or create a participation.
     *
     * @param exercise   The exercise to which the submission should be added.
     * @param submission The submission which should be added to the programming exercise.
     * @param login      The login of the user used to access or create an exercise participation.
     * @return The created programming submission.
     */
    public ProgrammingSubmission addProgrammingSubmission(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Adds programming submission to provided programming exercise. The provided login is used to access or create a participation.
     *
     * @param exercise   The exercise to which the submission should be added.
     * @param submission The submission which should be added to the programming exercise.
     * @param team       The login of the user used to access or create an exercise participation.
     * @return The created programming submission.
     */
    public ProgrammingSubmission addProgrammingSubmissionToTeamExercise(ProgrammingExercise exercise, ProgrammingSubmission submission, Team team) {
        StudentParticipation participation = participationUtilService.addTeamParticipationForProgrammingExercise(exercise, team);
        submission.setParticipation(participation);
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Adds a submission with a result to the given programming exercise. The submission will be assigned to the corresponding participation of the given login (if exists or
     * create a new participation).
     * The method will make sure that all necessary entities are connected.
     *
     * @param exercise   The exercise for which to create the submission/participation/result combination.
     * @param submission The submission to use for adding to the exercise/participation/result.
     * @param login      The login of the user used to access or create an exercise participation.
     * @return the newly created result
     */
    public Result addProgrammingSubmissionWithResult(ProgrammingExercise exercise, ProgrammingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.addStudentParticipationForProgrammingExercise(exercise, login);
        // TODO check if it needs to be persisted
        Result result = new Result();
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        submission.addResult(result);
        result.setSubmission(submission);
        programmingSubmissionRepo.save(submission);
        resultRepo.save(result);
        studentParticipationRepo.save(participation);
        return result;
    }

    /**
     * Adds a template submission with a result to the given programming exercise.
     * The method will make sure that all necessary entities are connected.
     *
     * @param programmingExercise The ProgrammingExercise of the programming exercise to which the submission should be added.
     * @return the newly created result
     */
    public Result addTemplateSubmissionWithResult(ProgrammingExercise programmingExercise) {
        var templateParticipation = programmingExercise.getTemplateParticipation();
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission = submissionRepository.save(submission);
        // TODO check if it needs to be persisted like before
        Result result = new Result();
        templateParticipation.addSubmission(submission);
        submission.setParticipation(templateParticipation);
        submission.addResult(result);
        submission = submissionRepository.save(submission);
        result.setSubmission(submission);
        result = resultRepo.save(result);
        templateProgrammingExerciseParticipationTestRepo.save(templateParticipation);
        return result;
    }

    /**
     * Adds a solution submission with a result to the given programming exercise.
     * The method will make sure that all necessary entities are connected.
     *
     * @param programmingExercise The ProgrammingExercise of the programming exercise to which the submission should be added.
     * @return the newly created result
     */
    public Result addSolutionSubmissionWithResult(ProgrammingExercise programmingExercise) {
        var templateParticipation = programmingExercise.getSolutionParticipation();
        ProgrammingSubmission submission = new ProgrammingSubmission();
        submission = submissionRepository.save(submission);
        Result result = new Result();
        templateParticipation.addSubmission(submission);
        submission.setParticipation(templateParticipation);
        submission.addResult(result);
        submission = submissionRepository.save(submission);
        result.setSubmission(submission);
        result = resultRepo.save(result);
        solutionProgrammingExerciseParticipationRepository.save(templateParticipation);
        return result;
    }

    /**
     * Adds a programming submission with a result and assessor to the given programming exercise.
     *
     * @param exercise          The exercise to which the submission should be added.
     * @param submission        The submission which should be added to the exercise.
     * @param login             The user login used to access or create the exercise participation.
     * @param assessorLogin     The login of the user assessing the exercise.
     * @param assessmentType    The type of the assessment.
     * @param hasCompletionDate True, if the result has a completion date.
     * @return The programming submission.
     */
    public ProgrammingSubmission addProgrammingSubmissionWithResultAndAssessor(ProgrammingExercise exercise, ProgrammingSubmission submission, String login, String assessorLogin,
            AssessmentType assessmentType, boolean hasCompletionDate) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        Result result = new Result();
        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setAssessmentType(assessmentType);
        result.setScore(50D);
        if (hasCompletionDate) {
            result.setCompletionDate(ZonedDateTime.now());
        }

        studentParticipationRepo.save(participation);
        programmingSubmissionRepo.save(submission);

        submission.setParticipation(participation);

        result.setSubmission(submission);
        result = resultRepo.save(result);
        submission.addResult(result);
        // Manual results are always rated
        if (assessmentType == AssessmentType.SEMI_AUTOMATIC) {
            result.rated(true);
        }
        submission = programmingSubmissionRepo.save(submission);
        return submission;
    }

    /**
     * Adds a programming submission to result and participation of a programming exercise.
     *
     * @param result        The result of the programming exercise.
     * @param participation The participation of the programming exercise.
     * @param commitHash    The commit hash of the submission.
     * @return The newly created programming submission.
     */
    public ProgrammingSubmission addProgrammingSubmissionToResultAndParticipation(Result result, StudentParticipation participation, String commitHash) {
        ProgrammingSubmission submission = createProgrammingSubmission(participation, false);
        submission.addResult(result);
        submission.setCommitHash(commitHash);
        result.setSubmission(submission);
        resultRepo.save(result);
        participation.addSubmission(submission);
        studentParticipationRepo.save(participation);
        return submissionRepository.save(submission);
    }

    /**
     * Adds a task for each test case and adds it to the problem statement of the programming exercise.
     *
     * @param programmingExercise The programming exercise to which tasks should be added.
     */
    public void addTasksToProgrammingExercise(ProgrammingExercise programmingExercise) {
        StringBuilder problemStatement = new StringBuilder(programmingExercise.getProblemStatement());
        problemStatement.append('\n');

        var tasks = programmingExercise.getTestCases().stream().map(testCase -> {
            var task = new ProgrammingExerciseTask();
            task.setTaskName("Task for " + testCase.getTestName());
            task.setExercise(programmingExercise);
            task.setTestCases(Collections.singleton(testCase));
            testCase.setTasks(Collections.singleton(task));
            problemStatement.append("[task][").append(task.getTaskName()).append("](")
                    .append(task.getTestCases().stream().map(ProgrammingExerciseTestCase::getTestName).collect(Collectors.joining(","))).append(")\n");
            return task;
        }).toList();
        programmingExercise.setTasks(tasks);
        programmingExercise.setProblemStatement(problemStatement.toString());
        programmingExerciseTaskRepository.saveAll(tasks);
        programmingExerciseRepository.save(programmingExercise);
    }

    /**
     * Loads a programming exercise with eager references from the repository.
     *
     * @param lazyExercise The exercise without references, the id is used when accessing the repository.
     * @return The programming exercise with references.
     */
    public ProgrammingExercise loadProgrammingExerciseWithEagerReferences(ProgrammingExercise lazyExercise) {
        return programmingExerciseTestRepository.findOneWithEagerEverything(lazyExercise.getId());
    }

    /**
     * Creates an example repository and makes the given GitService return it when asked to check it out.
     *
     * @throws Exception if creating the repository fails
     */
    public void createGitRepository() throws Exception {
        // Create repository
        var testRepo = new LocalRepository(defaultBranch);
        testRepo.configureRepos(localVCRepoPath, "testLocalRepo", "testOriginRepo");
        // Add test file to the repository folder
        Path filePath = Path.of(testRepo.workingCopyGitRepoFile + "/Test.java");
        var file = Files.createFile(filePath).toFile();
        FileUtils.write(file, "Test", Charset.defaultCharset());
        // Create mock repo that has the file
        var mockRepository = mock(Repository.class);
        doReturn(true).when(mockRepository).isValidFile(any());
        doReturn(testRepo.workingCopyGitRepoFile.toPath()).when(mockRepository).getLocalPath();
        // Mock Git service operations
        doReturn(mockRepository).when(gitService).getOrCheckoutRepository(any(), any(), any(), anyBoolean(), anyString(), anyBoolean());
        doNothing().when(gitService).resetToOriginHead(any());
        doReturn(Path.of("repo.zip")).when(gitService).getRepositoryWithParticipation(any(), anyString(), anyBoolean(), eq(true));
        doReturn(Path.of("repo")).when(gitService).getRepositoryWithParticipation(any(), anyString(), anyBoolean(), eq(false));
    }
}
