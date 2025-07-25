package de.tum.cit.aet.artemis.exercise;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.within;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.domain.TutorParticipation;
import de.tum.cit.aet.artemis.assessment.test_repository.TutorParticipationTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.StatsForDashboardDTO;
import de.tum.cit.aet.artemis.core.exception.EntityNotFoundException;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exam.domain.Exam;
import de.tum.cit.aet.artemis.exam.test_repository.ExamTestRepository;
import de.tum.cit.aet.artemis.exam.util.ExamUtilService;
import de.tum.cit.aet.artemis.exercise.domain.DifficultyLevel;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.participation.Participation;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.dto.ExerciseDetailsDTO;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.test_repository.ParticipationTestRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.util.ModelingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;
import de.tum.cit.aet.artemis.tutorialgroup.domain.TutorParticipationStatus;

class ExerciseIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "exerciseintegration";

    @Autowired
    private ExamTestRepository examRepository;

    @Autowired
    private ParticipationTestRepository participationRepository;

    @Autowired
    private TutorParticipationTestRepository tutorParticipationRepo;

    @Autowired
    private ExerciseService exerciseService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private ExamUtilService examUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ModelingExerciseUtilService modelingExerciseUtilService;

    static final int NUMBER_OF_TUTORS = 1;

    @BeforeEach
    void init() {
        userUtilService.addUsers(TEST_PREFIX, 3, NUMBER_OF_TUTORS, 0, 1);

        // Add users that are not in exercise/course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student11");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor6");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor2");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboardWithSubmissions() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 1);
        Course course = courses.getFirst();
        TextExercise textExercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        List<Submission> submissions = new ArrayList<>();

        userUtilService.addStudents(TEST_PREFIX, 4, 7);
        for (int i = 1; i <= 6; i++) {
            TextSubmission textSubmission = new TextSubmission();
            textSubmission.text("Text");
            textSubmission.submitted(true);
            textSubmission.submissionDate(ZonedDateTime.now());
            submissions.add(participationUtilService.addSubmission(textExercise, textSubmission, TEST_PREFIX + "student" + (i + 1))); // student1 was already used
            if (i % 3 == 0) {
                participationUtilService.addResultToSubmission(textSubmission, AssessmentType.MANUAL, userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
            }
            else if (i % 4 == 0) {
                participationUtilService.addResultToSubmission(textSubmission, AssessmentType.SEMI_AUTOMATIC, userUtilService.getUserByLogin(TEST_PREFIX + "instructor1"));
            }
        }
        StatsForDashboardDTO statsForDashboardDTO = request.get("/api/exercise/exercises/" + textExercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                StatsForDashboardDTO.class);
        assertThat(statsForDashboardDTO.getNumberOfSubmissions().inTime()).isEqualTo(submissions.size() + 1);
        assertThat(statsForDashboardDTO.getTotalNumberOfAssessments().inTime()).isEqualTo(3);
        assertThat(statsForDashboardDTO.getNumberOfAutomaticAssistedAssessments().inTime()).isEqualTo(1);

        for (Exercise exercise : course.getExercises()) {
            StatsForDashboardDTO stats = request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isZero();
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExamExerciseAssessmentDashboard() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(course, user);
        course = courseRepository.findByIdWithEagerExercisesElseThrow(course.getId());
        var exam = examRepository.findByCourseId(course.getId()).getFirst();
        var textExercise = examRepository.findAllExercisesWithDetailsByExamId(exam.getId()).stream().filter(ex -> ex instanceof TextExercise).findFirst().orElseThrow();
        StatsForDashboardDTO statsForDashboardDTO = request.get("/api/exercise/exercises/" + textExercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                StatsForDashboardDTO.class);
        assertThat(statsForDashboardDTO.getNumberOfSubmissions().inTime()).isZero();
        assertThat(statsForDashboardDTO.getTotalNumberOfAssessments().inTime()).isZero();
        assertThat(statsForDashboardDTO.getNumberOfAutomaticAssistedAssessments().inTime()).isZero();

        for (Exercise exercise : course.getExercises()) {
            StatsForDashboardDTO stats = request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK, StatsForDashboardDTO.class);
            assertThat(stats.getNumberOfComplaints()).isZero();
            assertThat(stats.getNumberOfMoreFeedbackRequests()).isZero();
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testFilterOutExercisesThatUserShouldNotSee() throws Exception {
        assertThatExceptionOfType(EntityNotFoundException.class)
                .isThrownBy(() -> exerciseService.findOneWithDetailsForStudents(Long.MAX_VALUE, userUtilService.getUserByLogin(TEST_PREFIX + "student1")));
        var course = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS).getFirst(); // the course with exercises
        var exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        var student = userTestRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "student1");
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(Set.of(), student)).isEmpty();
        var exercise = exercises.iterator().next();
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(1));
        exerciseRepository.save(exercise);
        exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), student)).hasSize(exercises.size() - 1);

        var tutor = userTestRepository.getUserWithGroupsAndAuthorities(TEST_PREFIX + "tutor1");
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), tutor)).hasSize(exercises.size());

        course.setOnlineCourse(true);
        courseRepository.save(course);
        exercises = exerciseRepository.findByCourseIdWithCategories(course.getId());
        assertThat(exerciseService.filterOutExercisesThatUserShouldNotSee(new HashSet<>(exercises), student)).isEmpty();

        var additionalCourses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, NUMBER_OF_TUTORS);
        var exercisesFromMultipleCourses = course.getExercises();
        for (var additionalCourse : additionalCourses) {
            exercisesFromMultipleCourses.addAll(additionalCourse.getExercises());
        }
        assertThatIllegalArgumentException().isThrownBy(() -> exerciseService.filterOutExercisesThatUserShouldNotSee(exercisesFromMultipleCourses, student));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExercise() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseServer = request.get("/api/exercise/exercises/" + exercise.getId(), HttpStatus.OK, Exercise.class);

                // Test that certain properties were set correctly
                assertThat(exerciseServer.getReleaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseServer.getDueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseServer.getMaxPoints()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseServer.getDifficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);

                // Test that certain properties were filtered out as the test user is a student
                assertThat(exerciseServer.getGradingInstructions()).as("Grading instructions were filtered out").isNull();
                assertThat(exerciseServer.getTutorParticipations()).as("Tutor participations not included").isEmpty();
                assertThat(exerciseServer.getExampleSubmissions()).as("Example submissions not included").isEmpty();

                // Test presence and absence of exercise type specific properties
                switch (exerciseServer) {
                    case FileUploadExercise fileUploadExercise -> assertFileUploadExercise(fileUploadExercise, "png", null);
                    case ModelingExercise modelingExercise -> assertModelingExercise(modelingExercise, DiagramType.ClassDiagram, null, null);
                    case ProgrammingExercise programmingExerciseExercise -> assertProgrammingExercise(programmingExerciseExercise, true, null, null, null, null, null);
                    case QuizExercise quizExercise -> assertQuizExercise(quizExercise, 120, 1, null, List.of());
                    case TextExercise textExercise -> assertThat(textExercise.getExampleSolution()).as("Sample solution was filtered out").isNull();
                    default -> {
                    }
                }

                // Test that the exercise does not have more than one participation.
                assertThat(exerciseServer.getStudentParticipations()).as("At most one participation for exercise").hasSizeLessThanOrEqualTo(1);

                if (exerciseServer.getStudentParticipations().isEmpty()) {
                    continue;
                }
                // Buffer participation so that null checking is easier.
                Participation participation = exerciseServer.getStudentParticipations().iterator().next();

                if (participation.getSubmissions().isEmpty()) {
                    continue;
                }
                // The call filters participations by submissions and their result. After the call each participation shouldn't have more than one submission.
                assertThat(participation.getSubmissions()).as("At most one submission for participation").hasSizeLessThanOrEqualTo(1);
                Submission submission = participation.getSubmissions().iterator().next();

                switch (submission) {

                    // Test that the correct text submission was filtered.
                    case TextSubmission textSubmission -> assertThat(textSubmission.getText()).as("Correct text submission").isEqualTo("text");

                    // Test that the correct modeling submission was filtered.
                    case ModelingSubmission modelingSubmission -> assertThat(modelingSubmission.getModel()).as("Correct modeling submission").isEqualTo("model2");
                    case null, default -> {
                    }
                }
            }
        }
    }

    private <T> void assertEqualOrNull(T actual, T expected, String entityName) {
        if (expected != null) {
            assertThat(actual).as(entityName + " was set correctly").isEqualTo(expected);
        }
        else {
            assertThat(actual).as(entityName + " not present").isNull();
        }
    }

    private void assertFileUploadExercise(FileUploadExercise exercise, String filePattern, String exampleSolution) {
        assertEqualOrNull(exercise.getFilePattern(), filePattern, "File pattern");
        assertEqualOrNull(exercise.getExampleSolution(), exampleSolution, "Sample solution");
    }

    private void assertModelingExercise(ModelingExercise exercise, DiagramType diagramType, String exampleSolutionModel, String exampleSolutionExplanation) {
        assertThat(exercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(diagramType);
        assertEqualOrNull(exercise.getExampleSolutionModel(), exampleSolutionModel, "Sample solution model");
        assertEqualOrNull(exercise.getExampleSolutionExplanation(), exampleSolutionExplanation, "Sample solution explanation");
    }

    private void assertProgrammingExercise(ProgrammingExercise exercise, boolean projectKey, String templateRepositoryUri, String solutionRepositoryUri, String testRepositoryUri,
            String templateBuildPlanId, String solutionBuildPlanId) {
        if (projectKey) {
            assertThat(exercise.getProjectKey()).as("Project key was set").isNotNull();
        }
        else {
            assertThat(exercise.getProjectKey()).as("Project key not present").isNull();
        }
        assertEqualOrNull(exercise.getTemplateRepositoryUri(), templateRepositoryUri, "Template repository uri");
        assertEqualOrNull(exercise.getSolutionRepositoryUri(), solutionRepositoryUri, "Solution repository uri");
        assertEqualOrNull(exercise.getTestRepositoryUri(), testRepositoryUri, "Test repository uri");
        assertEqualOrNull(exercise.getTemplateBuildPlanId(), templateBuildPlanId, "Template build plan id");
        assertEqualOrNull(exercise.getSolutionBuildPlanId(), solutionBuildPlanId, "Solution build plan id");
    }

    private void assertQuizExercise(QuizExercise exercise, int duration, int allowedNumberOfAttempts, QuizPointStatistic quizPointStatistic, List<QuizQuestion> quizQuestions) {
        assertThat(exercise.getDuration()).as("Duration was set correctly").isEqualTo(duration);
        assertThat(exercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(allowedNumberOfAttempts);
        assertEqualOrNull(exercise.getQuizPointStatistic(), quizPointStatistic, "Quiz point statistic");
        assertEqualOrNull(exercise.getQuizQuestions(), quizQuestions, "Quiz questions");
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetExamExercise_asStudent_forbidden() throws Exception {
        getExamExercise();
    }

    private void getExamExercise() throws Exception {
        TextExercise textExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        request.get("/api/exercise/exercises/" + textExercise.getId(), HttpStatus.FORBIDDEN, Exercise.class);
        request.get("/api/exercise/exercises/" + textExercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "admin", roles = "ADMIN")
    void testGetUpcomingExercises() throws Exception {
        var now = ZonedDateTime.now().truncatedTo(ChronoUnit.DAYS);
        List<Exercise> exercises = request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.OK, Exercise.class);
        for (var exercise : exercises) {
            assertThat(exercise.getDueDate()).isAfterOrEqualTo(now);
        }
        var size = exercises.size();

        // Test for exercise with upcoming due date.
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var exercise = course.getExercises().iterator().next();
        assertThat(exercise.getDueDate()).isAfterOrEqualTo(now);
        exercises = request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.OK, Exercise.class);
        assertThat(exercises).hasSize(size + 1).contains(exercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetUpcomingExercisesAsStudentForbidden() throws Exception {
        request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testGetUpcomingExercisesAsInstructorForbidden() throws Exception {
        request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetUpcomingExercisesAsTutorForbidden() throws Exception {
        request.getList("/api/exercise/admin/exercises/upcoming", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                ExerciseDetailsDTO exerciseWithDetailsWrapper = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
                Exercise exerciseWithDetails = exerciseWithDetailsWrapper.exercise();

                if (exerciseWithDetails instanceof FileUploadExercise fileUploadExercise) {
                    assertFileUploadExercise(fileUploadExercise, "png", null);
                    assertThat(fileUploadExercise.getStudentParticipations()).as("Number of participations is correct").isEmpty();
                }
                else if (exerciseWithDetails instanceof ModelingExercise modelingExercise) {
                    assertModelingExercise(modelingExercise, DiagramType.ClassDiagram, null, null);
                    assertThat(modelingExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(1);
                }
                else if (exerciseWithDetails instanceof ProgrammingExercise programmingExerciseExercise) {
                    assertProgrammingExercise(programmingExerciseExercise, true, null, null, null, null, null);
                    assertThat(programmingExerciseExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(2);
                }
                else if (exerciseWithDetails instanceof QuizExercise quizExercise) {
                    assertQuizExercise(quizExercise, 120, 1, null, List.of());
                    assertThat(quizExercise.getStudentParticipations()).as("Number of participations is correct").isEmpty();
                }
                else if (exerciseWithDetails instanceof TextExercise textExercise) {
                    assertThat(textExercise.getExampleSolution()).as("Sample solution was filtered out").isNull();
                    assertThat(textExercise.getStudentParticipations()).as("Number of participations is correct").hasSize(1);
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetCourseExerciseForExampleSolution() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        ZonedDateTime now = ZonedDateTime.now();
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {

                request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.FORBIDDEN, Exercise.class);

                exercise.setExampleSolutionPublicationDate(now.minusHours(1));
                exerciseRepository.save(exercise);

                Exercise exerciseForExampleSolution = request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.OK, Exercise.class);
                assertThat(exerciseForExampleSolution.getExampleSolutionPublicationDate()).isBeforeOrEqualTo(now);
                switch (exerciseForExampleSolution) {
                    case FileUploadExercise fileUploadExercise -> assertThat(fileUploadExercise.getExampleSolution()).isEqualTo("Example Solution");
                    case ModelingExercise modelingExercise -> {
                        assertThat(modelingExercise.getExampleSolutionModel()).isEqualTo("Example solution model");
                        assertThat(modelingExercise.getExampleSolutionExplanation()).isEqualTo("Example Solution");
                    }
                    case TextExercise textExercise -> assertThat(textExercise.getExampleSolution()).isEqualTo("Example Solution");
                    default -> {
                    }
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "TA")
    void testGetExamExerciseForExampleSolution() throws Exception {
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");
        Course course = courseUtilService.createCourse();
        course = examUtilService.createCourseWithExamAndExerciseGroupAndExercises(course, user);
        Exam exam = course.getExams().stream().findFirst().orElseThrow();
        exam = examRepository.findWithExerciseGroupsAndExercisesByIdOrElseThrow(exam.getId());
        TextExercise exercise = (TextExercise) exam.getExerciseGroups().getFirst().getExercises().stream().findFirst().orElseThrow();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.FORBIDDEN, Exercise.class);

        ZonedDateTime now = ZonedDateTime.now();
        exam.setExampleSolutionPublicationDate(now.minusHours(1));
        examUtilService.addStudentExamWithUser(exam, user);
        examRepository.save(exam);

        TextExercise exerciseForExampleSolution = request.get("/api/exercise/exercises/" + exercise.getId() + "/example-solution", HttpStatus.OK, TextExercise.class);

        assertThat(exerciseForExampleSolution.getExampleSolution()).isEqualTo("This is my example solution");

    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_assessmentDueDate_notPassed() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                addResultToSubmissionAndParticipation(exercise);
            }
            ExerciseDetailsDTO exerciseWithDetails = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
            for (StudentParticipation participation : exerciseWithDetails.exercise().getStudentParticipations()) {
                Set<Result> results = participationUtilService.getResultsForParticipation(participation);
                // Programming exercises should only have one automatic result
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(results).hasSize(1);
                    assertThat(results.iterator().next().getAssessmentType()).isEqualTo(AssessmentType.AUTOMATIC);
                }
                else {
                    // All other exercises should not display a result at all
                    assertThat(results).isEmpty();
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_assessmentDueDate_passed() throws Exception {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether this is correctly displayed after the assessment due date
            int resultSize = 1;
            if (exercise instanceof ProgrammingExercise) {
                addResultToSubmissionAndParticipation(exercise);
                resultSize = 2;
            }

            ExerciseDetailsDTO exerciseWithDetails = request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.OK, ExerciseDetailsDTO.class);
            for (var studentParticipation : exerciseWithDetails.exercise().getStudentParticipations()) {
                Set<Result> results = participationUtilService.getResultsForParticipation(studentParticipation);
                // Programming exercises should now how two results and the latest one is the manual result.
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(results).hasSize(resultSize);
                    assertThat(results.stream().sorted(Comparator.comparing(Result::getId).reversed()).iterator().next().getAssessmentType())
                            .isEqualTo(AssessmentType.SEMI_AUTOMATIC);
                }
                else {
                    // All other exercises have only one visible result now
                    assertThat(results).hasSize(1);
                }
            }
        }
    }

    private void addResultToSubmissionAndParticipation(Exercise exercise) {
        var participation = exercise.getStudentParticipations().iterator().next();
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        participationUtilService.addResultToSubmission(AssessmentType.SEMI_AUTOMATIC, ZonedDateTime.now().minusHours(1L), submission);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_withExamExercise_asStudent() throws Exception {
        Exercise exercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseDetails_withExamExercise_badRequest() throws Exception {
        Exercise exercise = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExercise();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, ExerciseDetailsDTO.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void filterForCourseDashboard_assessmentDueDate_notPassed() {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, false);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether the manual result will be displayed before the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                addResultToSubmissionAndParticipation(exercise);
            }
            exerciseService.filterExerciseForCourseDashboard(exercise, Set.copyOf(exercise.getStudentParticipations()), true);

            StudentParticipation participation = exercise.getStudentParticipations().iterator().next();
            Set<Result> results = participationUtilService.getResultsForParticipation(participation);
            if (exercise instanceof ProgrammingExercise) {
                var submission = participation.getSubmissions().iterator().next();
                // Programming exercises should only have one automatic result
                assertThat(results).hasSize(1).first().matches(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC);
                assertThat(participation.getSubmissions()).hasSize(1);
                assertThat(submission.getResults()).hasSize(1).first().matches(result -> result.getAssessmentType() == AssessmentType.AUTOMATIC);
            }
            else {
                // All other exercises have no visible result, and therefore no submission to transmit the result
                assertThat(participation.getSubmissions()).isEmpty();
                assertThat(results).isEmpty();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void filterForCourseDashboard_assessmentDueDate_passed() {
        Course course = courseUtilService.createCourseWithAllExerciseTypesAndParticipationsAndSubmissionsAndResults(TEST_PREFIX, true);
        for (Exercise exercise : course.getExercises()) {
            // For programming exercises we add a manual result, to check whether this is correctly displayed after the assessment due date
            if (exercise instanceof ProgrammingExercise) {
                addResultToSubmissionAndParticipation(exercise);
            }
            exerciseService.filterExerciseForCourseDashboard(exercise, Set.copyOf(exercise.getStudentParticipations()), true);
            Set<Result> results = participationUtilService.getResultsForParticipation(exercise.getStudentParticipations().iterator().next());
            // All exercises have one result
            assertThat(results).hasSize(1);
            // Programming exercises should now have one manual result
            if (exercise instanceof ProgrammingExercise) {
                assertThat(results.iterator().next().getAssessmentType()).isEqualTo(AssessmentType.SEMI_AUTOMATIC);
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student11", roles = "USER")
    void testGetExercise_forbidden() throws Exception {
        var course = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        var exercise = ExerciseUtilService.getFirstExerciseWithType(course, TextExercise.class);
        request.get("/api/exercise/exercises/" + exercise.getId(), HttpStatus.FORBIDDEN, Exercise.class);
        request.get("/api/exercise/exercises/" + exercise.getId() + "/details", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                Exercise exerciseForAssessmentDashboard = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, Exercise.class);
                assertThat(exerciseForAssessmentDashboard.getTutorParticipations()).as("Tutor participation was created").hasSize(1);
                assertThat(exerciseForAssessmentDashboard.getExampleSubmissions()).as("Example submissions are not null").isEmpty();

                // Test that certain properties were set correctly
                assertThat(exerciseForAssessmentDashboard.getReleaseDate()).as("Release date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.getDueDate()).as("Due date is present").isNotNull();
                assertThat(exerciseForAssessmentDashboard.getMaxPoints()).as("Max score was set correctly").isEqualTo(5.0);
                assertThat(exerciseForAssessmentDashboard.getDifficulty()).as("Difficulty was set correctly").isEqualTo(DifficultyLevel.MEDIUM);

                // Test presence of exercise type specific properties
                switch (exerciseForAssessmentDashboard) {
                    case FileUploadExercise fileUploadExercise -> assertThat(fileUploadExercise.getFilePattern()).as("File pattern was set correctly").isEqualTo("png");
                    case ModelingExercise modelingExercise ->
                        assertThat(modelingExercise.getDiagramType()).as("Diagram type was set correctly").isEqualTo(DiagramType.ClassDiagram);
                    case ProgrammingExercise programmingExerciseExercise -> assertThat(programmingExerciseExercise.getProjectKey()).as("Project key was set").isNotNull();
                    case QuizExercise quizExercise -> {
                        assertThat(quizExercise.getDuration()).as("Duration was set correctly").isEqualTo(120);
                        assertThat(quizExercise.getAllowedNumberOfAttempts()).as("Allowed number of attempts was set correctly").isEqualTo(1);
                    }
                    default -> {
                    }
                }
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_submissionsWithoutAssessments() throws Exception {
        var validModel = TestResourceUtils.loadFileFromResources("test-data/model-submission/model.54727.json");
        var course = modelingExerciseUtilService.addCourseWithOneModelingExercise();
        var exercise = ExerciseUtilService.getFirstExerciseWithType(course, ModelingExercise.class);
        var exampleSubmission = participationUtilService.generateExampleSubmission(validModel, exercise, true);
        participationUtilService.addExampleSubmission(exampleSubmission);
        Exercise receivedExercise = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, Exercise.class);
        assertThat(receivedExercise.getExampleSubmissions()).as("Example submission without assessment is removed from exercise").isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetExerciseForAssessmentDashboard_forbidden() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_programmingExerciseWithAutomaticAssessment() throws Exception {
        var exercise = programmingExerciseUtilService.addCourseWithOneProgrammingExercise().getExercises().iterator().next();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.BAD_REQUEST, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseForAssessmentDashboard_exerciseWithTutorParticipation() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        var tutorParticipation = new TutorParticipation().tutor(userUtilService.getUserByLogin(TEST_PREFIX + "tutor1")).assessedExercise(exercise)
                .status(TutorParticipationStatus.REVIEWED_INSTRUCTIONS);
        tutorParticipationRepo.save(tutorParticipation);
        var textExercise = request.get("/api/exercise/exercises/" + exercise.getId() + "/for-assessment-dashboard", HttpStatus.OK, TextExercise.class);
        assertThat(textExercise.getTutorParticipations().iterator().next().getStatus()).as("Status was changed to trained").isEqualTo(TutorParticipationStatus.TRAINED);
    }

    private List<User> findTutors(Course course) {
        List<User> tutors = new ArrayList<>();
        Page<User> allUsers = userTestRepository.findAllWithGroupsByDeletedIsFalse(Pageable.unpaged());
        for (User user : allUsers) {
            if (user.getGroups().contains(course.getTeachingAssistantGroupName())) {
                tutors.add(user);
            }
        }
        return tutors;
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboard() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            var tutors = findTutors(course);
            for (Exercise exercise : course.getExercises()) {
                StatsForDashboardDTO stats = request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.OK,
                        StatsForDashboardDTO.class);
                assertThat(stats.getTotalNumberOfAssessments().inTime()).as("Number of in-time assessments is correct").isZero();
                assertThat(stats.getTotalNumberOfAssessments().late()).as("Number of late assessments is correct").isZero();

                assertThat(stats.getTutorLeaderboardEntries()).as("Number of tutor leaderboard entries is correct").hasSameSizeAs(tutors);
                assertThat(stats.getNumberOfOpenComplaints()).as("Number of open complaints should be available to tutor").isNotNull();
                assertThat(stats.getNumberOfOpenMoreFeedbackRequests()).as("Number of open more feedback requests should be available to tutor").isNotNull();
                assertThat(stats.getNumberOfAssessmentLocks()).as("Number of assessment locks are not available for exercises").isNull();

                if (exercise instanceof FileUploadExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for file upload exercise is correct").isZero();
                }
                if (exercise instanceof ModelingExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for modeling exercise is correct").isEqualTo(2);
                }
                if (exercise instanceof ProgrammingExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for programming exercise is correct").isEqualTo(1);
                }
                if (exercise instanceof QuizExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for quiz exercise is correct").isZero();
                }
                if (exercise instanceof TextExercise) {
                    assertThat(stats.getNumberOfSubmissions().inTime()).as("Number of in-time submissions for text exercise is correct").isEqualTo(1);
                }

                assertThat(stats.getNumberOfSubmissions().late()).as("Number of late submissions for exercise is correct").isZero();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testGetStatsForExerciseAssessmentDashboard_forbidden() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        request.get("/api/exercise/exercises/" + exercise.getId() + "/stats-for-assessment-dashboard", HttpStatus.FORBIDDEN, Exercise.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testResetExercise() throws Exception {
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, NUMBER_OF_TUTORS);
        for (Course course : courses) {
            for (Exercise exercise : course.getExercises()) {
                request.delete("/api/exercise/exercises/" + exercise.getId() + "/reset", HttpStatus.OK);
                assertThat(exercise.getStudentParticipations()).as("Student participations have been deleted").isEmpty();
                assertThat(exercise.getTutorParticipations()).as("Tutor participations have been deleted").isEmpty();
                assertThat(participationRepository.findWithIndividualDueDateByExerciseId(exercise.getId())).isEmpty();
            }
        }
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor2", roles = "INSTRUCTOR")
    void testResetExercise_forbidden() throws Exception {
        var exercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise().getExercises().iterator().next();
        request.delete("/api/exercise/exercises/" + exercise.getId() + "/reset", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetSecondCorrectionEnabledFlagEnable() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];

        boolean isSecondCorrectionEnabled = request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class,
                HttpStatus.OK);
        assertThat(isSecondCorrectionEnabled).isTrue();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testSetSecondCorrectionEnabledFlagDisable() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        exercise.setSecondCorrectionEnabled(true);
        exerciseRepository.save(exercise);
        boolean isSecondCorrectionEnabled = request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class,
                HttpStatus.OK);
        assertThat(isSecondCorrectionEnabled).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor6", roles = "TA")
    void testSetSecondCorrectionEnabledFlagForbidden() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        request.putWithResponseBody("/api/exercise/exercises/" + exercise.getId() + "/toggle-second-correction", null, Boolean.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetExerciseTitleAsInstructor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExerciseTitle();
        testGetExamExerciseTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetExerciseTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetExerciseTitle();
        testGetExamExerciseTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetExerciseTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        // course exercise
        testGetExerciseTitle();

        // exam exercise
        testGetExamExerciseTitle();
    }

    private void testGetExerciseTitle() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        exercise.setTitle("Test Exercise");
        exercise = exerciseRepository.save(exercise);

        final var title = request.get("/api/exercise/exercises/" + exercise.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(exercise.getTitle());
    }

    private void testGetExamExerciseTitle() throws Exception {
        TextExercise textExercise = examUtilService.addCourseExamExerciseGroupWithOneTextExercise();
        final String expectedTitle = textExercise.getExerciseGroup().getTitle();
        final String title = request.get("/api/exercise/exercises/" + textExercise.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(expectedTitle);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetExerciseTitleForNonExistingExercise() throws Exception {
        request.get("/api/exercise/exercises/12312321321/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestDueDate() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        StudentParticipation studentParticipation2 = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");
        StudentParticipation studentParticipation3 = participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student3");

        studentParticipation2.setIndividualDueDate(exercise.getDueDate().plusHours(2));
        studentParticipation3.setIndividualDueDate(exercise.getDueDate().plusHours(4));
        participationRepository.save(studentParticipation2);
        participationRepository.save(studentParticipation3);

        ZonedDateTime latestDueDate = request.get("/api/exercise/exercises/" + exercise.getId() + "/latest-due-date", HttpStatus.OK, ZonedDateTime.class);
        assertThat(latestDueDate).isCloseTo(studentParticipation3.getIndividualDueDate(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLatestDueDateWhenNoIndividualDueDate() throws Exception {
        Course courseWithOneReleasedTextExercise = textExerciseUtilService.addCourseWithOneReleasedTextExercise();
        Exercise exercise = (Exercise) courseWithOneReleasedTextExercise.getExercises().toArray()[0];
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student1");
        participationUtilService.createAndSaveParticipationForExercise(exercise, TEST_PREFIX + "student2");

        ZonedDateTime latestDueDate = request.get("/api/exercise/exercises/" + exercise.getId() + "/latest-due-date", HttpStatus.OK, ZonedDateTime.class);
        assertThat(latestDueDate).isCloseTo(exercise.getDueDate(), within(1, ChronoUnit.SECONDS));
    }
}
