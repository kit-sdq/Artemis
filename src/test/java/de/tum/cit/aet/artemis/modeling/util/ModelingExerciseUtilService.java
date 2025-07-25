package de.tum.cit.aet.artemis.modeling.util;

import static org.assertj.core.api.Assertions.assertThat;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.IOException;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.Feedback;
import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.FeedbackRepository;
import de.tum.cit.aet.artemis.assessment.service.AssessmentService;
import de.tum.cit.aet.artemis.assessment.test_repository.ResultTestRepository;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseFactory;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationFactory;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.ExerciseTestRepository;
import de.tum.cit.aet.artemis.exercise.test_repository.StudentParticipationTestRepository;
import de.tum.cit.aet.artemis.modeling.domain.DiagramType;
import de.tum.cit.aet.artemis.modeling.domain.ModelingExercise;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.modeling.repository.ModelingExerciseRepository;
import de.tum.cit.aet.artemis.modeling.service.ModelingSubmissionService;
import de.tum.cit.aet.artemis.modeling.test_repository.ModelingSubmissionTestRepository;

/**
 * Service responsible for initializing the database with specific testdata related to modeling exercises for use in integration tests.
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ModelingExerciseUtilService {

    private static final ZonedDateTime pastTimestamp = ZonedDateTime.now().minusDays(1);

    private static final ZonedDateTime futureTimestamp = ZonedDateTime.now().plusDays(1);

    private static final ZonedDateTime futureFutureTimestamp = ZonedDateTime.now().plusDays(2);

    @Autowired
    private CourseTestRepository courseRepo;

    @Autowired
    private ModelingExerciseRepository modelingExerciseRepository;

    @Autowired
    private ResultTestRepository resultRepo;

    @Autowired
    private StudentParticipationTestRepository studentParticipationRepo;

    @Autowired
    private ModelingSubmissionTestRepository modelingSubmissionRepo;

    @Autowired
    private FeedbackRepository feedbackRepo;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private AssessmentService assessmentService;

    @Autowired
    private ModelingSubmissionService modelSubmissionService;

    @Autowired
    private ExerciseTestRepository exerciseRepository;

    /**
     * Creates and saves a ModelingExercise.
     *
     * @param course            The Course to which the exercise belongs
     * @param startDate         The release date of the TextExercise
     * @param releaseDate       The release date of the TextExercise
     * @param dueDate           The due date of the TextExercise
     * @param assessmentDueDate The assessment due date of the TextExercise
     * @return The created TextExercise
     */
    public ModelingExercise addModelingExercise(Course course, ZonedDateTime releaseDate, ZonedDateTime startDate, ZonedDateTime dueDate, ZonedDateTime assessmentDueDate) {
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(releaseDate, startDate, dueDate, assessmentDueDate, DiagramType.ClassDiagram, course);
        modelingExercise.setTitle("Modeling Exercise");
        course.addExercises(modelingExercise);
        course.setMaxComplaintTimeDays(14);
        return exerciseRepository.save(modelingExercise);
    }

    /**
     * Creates and saves a Course with a ModelingExercise. The ModelingExercise's DiagramType is set to ClassDiagram.
     *
     * @param title The title of the ModelingExercise
     * @return The created Course
     */
    public Course addCourseWithOneModelingExercise(String title) {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        modelingExercise.setTitle(title);
        course.addExercises(modelingExercise);
        course.setMaxComplaintTimeDays(14);
        course = courseRepo.save(course);
        modelingExercise = exerciseRepository.save(modelingExercise);
        assertThat(course.getExercises()).as("course contains the exercise").containsExactlyInAnyOrder(modelingExercise);
        assertThat(modelingExercise.getPresentationScoreEnabled()).as("presentation score is enabled").isTrue();
        return course;
    }

    public ModelingExercise addModelingExerciseToCourse(Course course) {
        ModelingExercise modelingExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram,
                course);
        modelingExercise.setTitle("ClassDiagram");
        course.addExercises(modelingExercise);
        courseRepo.save(course);
        modelingExercise = exerciseRepository.save(modelingExercise);
        return modelingExercise;
    }

    /**
     * Updates an existing ModelingExercise in the database.
     *
     * @param exercise The ModelingExercise to be updated
     * @return The updated ModelingExercise
     */
    public ModelingExercise updateExercise(ModelingExercise exercise) {
        return modelingExerciseRepository.save(exercise);
    }

    /**
     * Creates and saves a Course with a ModelingExercise. The ModelingExercise's DiagramType is set to ClassDiagram.
     *
     * @return The created Course
     */
    public Course addCourseWithOneModelingExercise() {
        return addCourseWithOneModelingExercise("ClassDiagram");
    }

    /**
     * Creates and saves a Course with 11 ModelingExercises, one of each DiagramType and one finished exercise.
     *
     * @return The created Course
     */
    public Course addCourseWithDifferentModelingExercises() {
        Course course = CourseFactory.generateCourse(null, pastTimestamp, futureFutureTimestamp, new HashSet<>(), "tumuser", "tutor", "editor", "instructor");
        ModelingExercise classExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ClassDiagram, course);
        classExercise.setTitle("ClassDiagram");
        course.addExercises(classExercise);

        ModelingExercise activityExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ActivityDiagram,
                course);
        activityExercise.setTitle("ActivityDiagram");
        course.addExercises(activityExercise);

        ModelingExercise objectExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ObjectDiagram,
                course);
        objectExercise.setTitle("ObjectDiagram");
        course.addExercises(objectExercise);

        ModelingExercise useCaseExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.UseCaseDiagram,
                course);
        useCaseExercise.setTitle("UseCaseDiagram");
        course.addExercises(useCaseExercise);

        ModelingExercise communicationExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp,
                DiagramType.CommunicationDiagram, course);
        communicationExercise.setTitle("CommunicationDiagram");
        course.addExercises(communicationExercise);

        ModelingExercise componentExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.ComponentDiagram,
                course);
        componentExercise.setTitle("ComponentDiagram");
        course.addExercises(componentExercise);

        ModelingExercise deploymentExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.DeploymentDiagram,
                course);
        deploymentExercise.setTitle("DeploymentDiagram");
        course.addExercises(deploymentExercise);

        ModelingExercise petriNetExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.PetriNet, course);
        petriNetExercise.setTitle("PetriNet");
        course.addExercises(petriNetExercise);

        ModelingExercise syntaxTreeExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.SyntaxTree,
                course);
        syntaxTreeExercise.setTitle("SyntaxTree");
        course.addExercises(syntaxTreeExercise);

        ModelingExercise flowchartExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, futureTimestamp, futureFutureTimestamp, DiagramType.Flowchart, course);
        flowchartExercise.setTitle("Flowchart");
        course.addExercises(flowchartExercise);

        ModelingExercise finishedExercise = ModelingExerciseFactory.generateModelingExercise(pastTimestamp, pastTimestamp, futureTimestamp, DiagramType.ClassDiagram, course);
        finishedExercise.setTitle("finished");
        course.addExercises(finishedExercise);

        course = courseRepo.save(course);
        exerciseRepository.save(classExercise);
        exerciseRepository.save(activityExercise);
        exerciseRepository.save(objectExercise);
        exerciseRepository.save(useCaseExercise);
        exerciseRepository.save(communicationExercise);
        exerciseRepository.save(componentExercise);
        exerciseRepository.save(deploymentExercise);
        exerciseRepository.save(petriNetExercise);
        exerciseRepository.save(syntaxTreeExercise);
        exerciseRepository.save(flowchartExercise);
        exerciseRepository.save(finishedExercise);
        Course storedCourse = courseRepo.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(course.getId());
        Set<Exercise> exercises = storedCourse.getExercises();
        assertThat(exercises).as("eleven exercises got stored").hasSize(11);
        assertThat(exercises).as("Contains all exercises").containsExactlyInAnyOrder(course.getExercises().toArray(new Exercise[] {}));
        return course;
    }

    /**
     * Creates and saves a ModelingSubmission, a Result and a StudentParticipation for the given ModelingExercise.
     *
     * @param exercise The ModelingExercise the submission belongs to
     * @param model    The model of the submission
     * @param login    The login of the user the submission belongs to
     * @return The created ModelingSubmission
     */
    public ModelingSubmission addModelingSubmissionWithEmptyResult(ModelingExercise exercise, String model, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(model, true);
        var user = userUtilService.getUserByLogin(login);
        submission = modelSubmissionService.handleModelingSubmission(submission, exercise, user);
        Result result = new Result();
        result.setSubmission(submission);
        result = resultRepo.save(result);
        submission.addResult(result);
        studentParticipationRepo.save(participation);
        modelingSubmissionRepo.save(submission);
        resultRepo.save(result);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given ModelingExercise and ModelingSubmission.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission that belongs to the StudentParticipation
     * @param login      The login of the user the submission belongs to
     * @return The updated ModelingSubmission
     */
    public ModelingSubmission addModelingSubmission(ModelingExercise exercise, ModelingSubmission submission, String login) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    /**
     * Creates and saves a team StudentParticipation for the given ModelingExercise and a team ModelingSubmission.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission that belongs to the StudentParticipation
     * @param team       The team the submission belongs to
     * @return The updated ModelingSubmission
     */
    public ModelingSubmission addModelingTeamSubmission(ModelingExercise exercise, ModelingSubmission submission, Team team) {
        StudentParticipation participation = participationUtilService.addTeamParticipationForExercise(exercise, team.getId());
        participation.addSubmission(submission);
        submission.setParticipation(participation);
        modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given ModelingExercise, the ModelingSubmission, and login. Also creates and saves a Result for the StudentParticipation
     * given the assessorLogin.
     *
     * @param exercise      The ModelingExercise the submission belongs to
     * @param submission    The ModelingSubmission that belongs to the StudentParticipation
     * @param login         The login of the user the submission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated ModelingSubmission
     */
    public ModelingSubmission addModelingSubmissionWithResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {

        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        participation.addSubmission(submission);
        submission = modelingSubmissionRepo.save(submission);

        Result result = new Result();

        result.setAssessor(userUtilService.getUserByLogin(assessorLogin));
        result.setAssessmentType(AssessmentType.MANUAL);
        submission = modelingSubmissionRepo.save(submission);
        result.setSubmission(submission);
        result = resultRepo.save(result);
        studentParticipationRepo.save(participation);
        result = resultRepo.save(result);

        submission.setParticipation(participation);
        submission.addResult(result);
        submission = modelingSubmissionRepo.save(submission);
        studentParticipationRepo.save(participation);
        return submission;
    }

    /**
     * Creates and saves a StudentParticipation for the given ModelingExercise, the ModelingSubmission, and login. Also creates and saves a Result for the StudentParticipation
     * given the assessorLogin.
     *
     * @param exercise      The ModelingExercise the submission belongs to
     * @param submission    The ModelingSubmission that belongs to the StudentParticipation
     * @param login         The login of the user the submission belongs to
     * @param assessorLogin The login of the assessor the Result belongs to
     * @return The updated Submission
     */
    public Submission addModelingSubmissionWithFinishedResultAndAssessor(ModelingExercise exercise, ModelingSubmission submission, String login, String assessorLogin) {
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(exercise, login);
        return participationUtilService.addSubmissionWithFinishedResultsWithAssessor(participation, submission, assessorLogin);
    }

    /**
     * Creates and saves a ModelingSubmission from a file.
     *
     * @param exercise The ModelingExercise the submission belongs to
     * @param path     The path to the file that contains the submission's model
     * @param login    The login of the user the submission belongs to
     * @return The created ModelingSubmission
     * @throws IOException If the file can't be read
     */
    public ModelingSubmission addModelingSubmissionFromResources(ModelingExercise exercise, String path, String login) throws IOException {
        String model = TestResourceUtils.loadFileFromResources(path);
        ModelingSubmission submission = ParticipationFactory.generateModelingSubmission(model, true);
        submission = addModelingSubmission(exercise, submission, login);
        checkModelingSubmissionCorrectlyStored(submission.getId(), model);
        return submission;
    }

    /**
     * Verifies that a ModelingSubmission with the given id has been stored with the given model. Fails if the submission can't be found or the models don't match.
     *
     * @param submissionId The id of the ModelingSubmission
     * @param sentModel    The model that should have been stored
     */
    public void checkModelingSubmissionCorrectlyStored(Long submissionId, String sentModel) throws JsonProcessingException {
        Optional<ModelingSubmission> modelingSubmission = modelingSubmissionRepo.findById(submissionId);
        assertThat(modelingSubmission).as("submission correctly stored").isPresent();
        checkModelsAreEqual(modelingSubmission.orElseThrow().getModel(), sentModel);
    }

    /**
     * Verifies that the given models are equal. Fails if they are not equal.
     *
     * @param storedModel The model that has been stored
     * @param sentModel   The model that should have been stored
     */
    public void checkModelsAreEqual(String storedModel, String sentModel) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode sentModelNode = objectMapper.readTree(sentModel);
        JsonNode storedModelNode = objectMapper.readTree(storedModel);
        assertThat(storedModelNode).as("model correctly stored").isEqualTo(sentModelNode);
    }

    /**
     * Creates and saves a Result given a ModelingSubmission and a path to a file containing the Feedback for the Result.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission the Result belongs to
     * @param path       The path to the file containing the Feedback for the Result
     * @param login      The login of the assessor the Result belongs to
     * @param submit     True, if the Result should be submitted (if the Result needs to be edited before submission, set this to false)
     * @return The created Result
     * @throws Exception If the file can't be read
     */
    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String path, String login, boolean submit) throws Exception {
        List<Feedback> feedbackList = participationUtilService.loadAssessmentFomResources(path);
        Result result = assessmentService.saveAndSubmitManualAssessment(exercise, submission, feedbackList, null, null, submit);
        result.setAssessor(userUtilService.getUserByLogin(login));
        result.setSubmission(submission);
        submission.addResult(result);
        resultRepo.save(result);
        return resultRepo.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(result.getId());
    }

    /**
     * Creates and saves a Result for the given ModelingSubmission. The Result contains two Feedback elements.
     *
     * @param exercise   The ModelingExercise the submission belongs to
     * @param submission The ModelingSubmission the Result belongs to
     * @param login      The login of the assessor the Result belongs to
     * @param submit     True, if the Result should be submitted (if the Result needs to be edited before submission, set this to false)
     * @return The created Result
     */
    public Result addModelingAssessmentForSubmission(ModelingExercise exercise, ModelingSubmission submission, String login, boolean submit) {
        Feedback feedback1 = feedbackRepo.save(new Feedback().detailText("detail1"));
        Feedback feedback2 = feedbackRepo.save(new Feedback().detailText("detail2"));
        List<Feedback> feedbacks = new ArrayList<>();
        feedbacks.add(feedback1);
        feedbacks.add(feedback2);

        Result result = assessmentService.saveAndSubmitManualAssessment(exercise, submission, feedbacks, null, null, submit);
        result.setAssessor(userUtilService.getUserByLogin(login));
        resultRepo.save(result);
        return resultRepo.findWithBidirectionalSubmissionAndFeedbackAndAssessorAndAssessmentNoteAndTeamStudentsByIdElseThrow(result.getId());
    }
}
