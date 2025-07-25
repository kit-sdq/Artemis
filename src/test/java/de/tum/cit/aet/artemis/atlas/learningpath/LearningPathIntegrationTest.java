package de.tum.cit.aet.artemis.atlas.learningpath;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.atlas.AbstractAtlasIntegrationTest;
import de.tum.cit.aet.artemis.atlas.domain.LearningObject;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyRelation;
import de.tum.cit.aet.artemis.atlas.domain.competency.LearningPath;
import de.tum.cit.aet.artemis.atlas.domain.competency.RelationType;
import de.tum.cit.aet.artemis.atlas.domain.profile.CourseLearnerProfile;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyGraphNodeDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyImportOptionsDTO;
import de.tum.cit.aet.artemis.atlas.dto.CompetencyNameDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathAverageProgressDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathCompetencyGraphDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathHealthDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathInformationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationObjectDTO;
import de.tum.cit.aet.artemis.atlas.dto.LearningPathNavigationOverviewDTO;
import de.tum.cit.aet.artemis.atlas.service.competency.CompetencyProgressService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.Exercise;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

class LearningPathIntegrationTest extends AbstractAtlasIntegrationTest {

    private static final String TEST_PREFIX = "learningpathintegration";

    private Course course;

    private Competency[] competencies;

    private TextExercise textExercise;

    private TextUnit textUnit;

    private Lecture lecture;

    private static final int NUMBER_OF_STUDENTS = 5;

    private static final String STUDENT1_OF_COURSE = TEST_PREFIX + "student1";

    private static final String STUDENT2_OF_COURSE = TEST_PREFIX + "student2";

    private static final String TUTOR_OF_COURSE = TEST_PREFIX + "tutor1";

    private static final String EDITOR_OF_COURSE = TEST_PREFIX + "editor1";

    private static final String INSTRUCTOR_OF_COURSE = TEST_PREFIX + "instructor1";

    @BeforeEach
    void setupTestScenario() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, NUMBER_OF_STUDENTS, 1, 1, 1);

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student1337");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor1337");

        learnerProfileUtilService.createLearnerProfilesForUsers(TEST_PREFIX);

        course = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, 1).getFirst();
        competencies = competencyUtilService.createCompetencies(course, 5);

        // set threshold to 60, 70, 80, 90 and 100 respectively
        for (int i = 0; i < competencies.length; i++) {
            competencies[i] = competencyUtilService.updateMasteryThreshold(competencies[i], 60 + i * 10);
        }

        for (int i = 1; i < competencies.length; i++) {
            var relation = new CompetencyRelation();
            relation.setHeadCompetency(competencies[i - 1]);
            relation.setTailCompetency(competencies[i]);
            relation.setType(RelationType.EXTENDS);
            competencyRelationRepository.save(relation);
        }

        lecture = new Lecture();
        lecture.setDescription("Test Lecture");
        lecture.setCourse(course);
        lectureRepository.save(lecture);

        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();

        textUnit = createAndLinkTextUnit(student, competencies[0], true);
        textExercise = createAndLinkTextExercise(competencies[1], false);
    }

    private ZonedDateTime now() {
        return ZonedDateTime.now();
    }

    private ZonedDateTime past(long days) {
        return now().minusDays(days);
    }

    private ZonedDateTime future(long days) {
        return now().plusDays(days);
    }

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/atlas/courses/" + course.getId() + "/learning-paths/enable", null, HttpStatus.FORBIDDEN);
        request.put("/api/atlas/courses/" + course.getId() + "/learning-paths/generate-missing", null, HttpStatus.FORBIDDEN);
        final var search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/atlas/courses/" + course.getId() + "/learning-paths", HttpStatus.FORBIDDEN, LearningPath.class,
                pageableSearchUtilService.searchMapping(search));
        request.get("/api/atlas/courses/" + course.getId() + "/learning-path-health", HttpStatus.FORBIDDEN, LearningPathHealthDTO.class);
    }

    private void enableLearningPathsRESTCall(Course course) throws Exception {
        request.put("/api/atlas/courses/" + course.getId() + "/learning-paths/enable", null, HttpStatus.OK);
    }

    private void assertAverageProgress(long courseId, HttpStatus status, Double expectedAverage) throws Exception {
        var response = request.get("/api/atlas/courses/" + courseId + "/learning-path/average-progress", status, LearningPathAverageProgressDTO.class);
        if (expectedAverage == null) {
            assertThat(response).isNull();
        }
        else {
            assertThat(response).isNotNull();
            assertThat(response.averageProgress()).isEqualTo(expectedAverage);
        }
    }

    private Competency createCompetencyRESTCall() throws Exception {
        final var competencyToCreate = new Competency();
        competencyToCreate.setTitle("CompetencyToCreateTitle");
        competencyToCreate.setCourse(course);
        competencyToCreate.setMasteryThreshold(42);
        return request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/competencies", competencyToCreate, Competency.class, HttpStatus.CREATED);
    }

    private Competency importCompetencyRESTCall() throws Exception {
        final var course2 = courseUtilService.createCourse();
        final var competencyToImport = competencyUtilService.createCompetency(course2);
        CompetencyImportOptionsDTO importOptions = new CompetencyImportOptionsDTO(Set.of(competencyToImport.getId()), Optional.empty(), false, false, false, Optional.empty(),
                false);
        return request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/competencies/import", importOptions, Competency.class, HttpStatus.CREATED);
    }

    private void deleteCompetencyRESTCall(Competency competency) throws Exception {
        request.delete("/api/atlas/courses/" + course.getId() + "/competencies/" + competency.getId(), HttpStatus.OK);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
        request.get("/api/atlas/courses/" + course.getId() + "/learning-path/me", HttpStatus.BAD_REQUEST, LearningPathDTO.class);
    }

    @Test
    @WithMockUser(username = TUTOR_OF_COURSE, roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = EDITOR_OF_COURSE, roles = "EDITOR")
    void testAll_asEditor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPaths() throws Exception {
        enableLearningPathsRESTCall(course);
        final var updatedCourse = courseRepository.findWithEagerLearningPathsByIdElseThrow(course.getId());
        assertThat(updatedCourse.getLearningPathsEnabled()).as("should enable LearningPaths").isTrue();
        assertThat(updatedCourse.getLearningPaths()).isNotNull();
        assertThat(updatedCourse.getLearningPaths().size()).as("should create LearningPath for each student").isEqualTo(NUMBER_OF_STUDENTS);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPathsWithNoCompetencies() throws Exception {
        var courseWithoutCompetencies = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, false, false, 0).getFirst();
        enableLearningPathsRESTCall(courseWithoutCompetencies);
        final var updatedCourse = courseRepository.findWithEagerLearningPathsByIdElseThrow(courseWithoutCompetencies.getId());
        assertThat(updatedCourse.getLearningPathsEnabled()).as("should enable LearningPaths").isTrue();
        assertThat(updatedCourse.getLearningPaths()).isNotNull();
        assertThat(updatedCourse.getLearningPaths().size()).as("should create LearningPath for each student").isEqualTo(NUMBER_OF_STUDENTS);
        updatedCourse.getLearningPaths().forEach(lp -> assertThat(lp.getProgress()).as("LearningPath (id={}) should have no progress", lp.getId()).isEqualTo(0));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testEnableLearningPathsAlreadyEnabled() throws Exception {
        course.setLearningPathsEnabled(true);
        courseRepository.save(course);
        request.put("/api/atlas/courses/" + course.getId() + "/learning-paths/enable", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGenerateMissingLearningPathsForCourse() throws Exception {
        course.setLearningPathsEnabled(true);
        courseRepository.save(course);
        final var students = userTestRepository.getStudents(course);
        students.stream().map(User::getId).map(userTestRepository::findWithLearningPathsByIdElseThrow).forEach(learningPathUtilService::deleteLearningPaths);
        request.put("/api/atlas/courses/" + course.getId() + "/learning-paths/generate-missing", null, HttpStatus.OK);
        students.forEach(user -> {
            user = userTestRepository.findWithLearningPathsByIdElseThrow(user.getId());
            assertThat(user.getLearningPaths()).hasSize(1);
        });

    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGenerateMissingLearningPathsForCourseNotEnabled() throws Exception {
        request.put("/api/atlas/courses/" + course.getId() + "/learning-paths/generate-missing", null, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1337", roles = "USER")
    void testGenerateLearningPathOnEnrollment() throws Exception {
        course.setEnrollmentEnabled(true);
        course.setEnrollmentStartDate(past(1));
        course.setEnrollmentEndDate(future(1));
        course = courseRepository.save(course);
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        request.postWithResponseBody("/api/core/courses/" + course.getId() + "/enroll", null, Set.class, HttpStatus.OK);
        final var user = userTestRepository.findOneWithLearningPathsAndLearnerProfileByLogin(TEST_PREFIX + "student1337").orElseThrow();

        assertThat(user.getLearningPaths()).isNotNull();
        assertThat(user.getLearningPaths()).as("should create LearningPath for student").hasSize(1);
        assertThat(user.getLearnerProfile().getCourseLearnerProfiles()).hasSize(1);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseLearningPathsDisabled() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        request.getSearchResult("/api/atlas/courses/" + course.getId() + "/learning-paths", HttpStatus.BAD_REQUEST, LearningPathInformationDTO.class,
                pageableSearchUtilService.searchMapping(search));
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseEmpty() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var search = pageableSearchUtilService.configureSearch(STUDENT1_OF_COURSE + "SuffixThatAllowsTheResultToBeEmpty");
        final var result = request.getSearchResult("/api/atlas/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPathInformationDTO.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetLearningPathsOnPageForCourseExactlyStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var search = pageableSearchUtilService.configureSearch(STUDENT1_OF_COURSE);
        final var result = request.getSearchResult("/api/atlas/courses/" + course.getId() + "/learning-paths", HttpStatus.OK, LearningPathInformationDTO.class,
                pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    private static Stream<Arguments> addCompetencyToLearningPathsOnCreateAndImportCompetencyTestProvider() {
        final Function<LearningPathIntegrationTest, Competency> createCall = (reference) -> {
            try {
                return reference.createCompetencyRESTCall();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        final Function<LearningPathIntegrationTest, Competency> importCall = (reference) -> {
            try {
                return reference.importCompetencyRESTCall();
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
        };
        return Stream.of(Arguments.of(createCall), Arguments.of(importCall));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    @MethodSource("addCompetencyToLearningPathsOnCreateAndImportCompetencyTestProvider")
    void addCompetencyToLearningPaths(Function<LearningPathIntegrationTest, Competency> restCall) {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        final var newCompetency = restCall.apply(this);

        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPathOptional = learningPathRepositoryService.findWithEagerCompetenciesByCourseIdAndUserId(course.getId(), student.getId());
        assertThat(learningPathOptional).isPresent();
        assertThat(learningPathOptional.get().getCompetencies()).as("should contain new competency").contains(newCompetency);
        assertThat(learningPathOptional.get().getCompetencies().size()).as("should not remove old competencies").isEqualTo(competencies.length + 1);
        final var oldCompetencies = Set.of(competencies[0], competencies[1], competencies[2], competencies[3], competencies[4]);
        assertThat(learningPathOptional.get().getCompetencies()).as("should not remove old competencies").containsAll(oldCompetencies);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testUpdateLearningPathProgress() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        // add competency with completed learning unit
        createCompetencyRESTCall();

        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        var learningPath = learningPathRepositoryService.findWithEagerCompetenciesByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        assertThat(learningPath.getProgress()).as("contains no completed competency").isEqualTo(0);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetAverageProgressForCourse_successfulCalculation() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        var students = userTestRepository.getStudents(course);
        int[] progresses = { 20, 40, 60, 80, 100 };
        int i = 0;
        for (User student : students) {
            var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            learningPath.setProgress(progresses[i++]);
            learningPathRepository.save(learningPath);
        }

        double expectedAverage = Arrays.stream(progresses).average().orElse(0);
        assertAverageProgress(course.getId(), HttpStatus.OK, expectedAverage);
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetAverageProgressForCourse_emptyCalculation() throws Exception {
        course = courseUtilService.createCourse();
        assertAverageProgress(course.getId(), HttpStatus.OK, 0.0);
        assertAverageProgress(99999L, HttpStatus.FORBIDDEN, null);
    }

    /**
     * This only tests if the end point successfully retrieves the health status. The correctness of the health status is tested in LearningPathServiceTest.
     *
     * @throws Exception the request failed
     * @see de.tum.cit.aet.artemis.atlas.service.LearningPathServiceTest
     */
    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetHealthStatusForCourse() throws Exception {
        request.get("/api/atlas/courses/" + course.getId() + "/learning-path-health", HttpStatus.OK, LearningPathHealthDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPath() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        var response = request.get("/api/atlas/learning-path/" + learningPath.getId(), HttpStatus.OK, LearningPathInformationDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(learningPath.getId());
        assertThat(response.progress()).isEqualTo(learningPath.getProgress());
        assertThat(response.user()).isNotNull();
        assertThat(response.user().login()).isEqualTo(student.getLogin());
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathCompetencyGraphOfOtherUser() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var otherStudent = userTestRepository.findOneByLogin(STUDENT2_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), otherStudent.getId());
        request.get("/api/atlas/learning-path/" + learningPath.getId() + "/competency-graph", HttpStatus.FORBIDDEN, LearningPathCompetencyGraphDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathCompetencyGraph() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        Arrays.stream(competencies).forEach(competency -> competencyProgressService.updateCompetencyProgress(competency.getId(), student));

        LearningPathCompetencyGraphDTO response = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/competency-graph", HttpStatus.OK,
                LearningPathCompetencyGraphDTO.class);

        assertThat(response).isNotNull();
        assertThat(response.nodes().stream().map(CompetencyGraphNodeDTO::id))
                .containsExactlyInAnyOrderElementsOf(Arrays.stream(competencies).map(Competency::getId).map(Object::toString).toList());
        assertThat(response.nodes()).allMatch(nodeDTO -> {
            var progress = competencyProgressRepository.findByCompetencyIdAndUserIdOrElseThrow(Long.parseLong(nodeDTO.id()), student.getId());
            var masteryProgress = CompetencyProgressService.getMasteryProgress(progress);
            return Objects.equals(nodeDTO.value(), Math.floor(masteryProgress * 100))
                    && Objects.equals(nodeDTO.valueType(), CompetencyGraphNodeDTO.CompetencyNodeValueType.MASTERY_PROGRESS);
        });

        Set<CompetencyRelation> relations = competencyRelationRepository.findAllWithHeadAndTailByCourseId(course.getId());
        assertThat(response.edges()).hasSameSizeAs(relations);
        assertThat(response.edges()).allMatch(relationDTO -> relations.stream().anyMatch(relation -> relation.getId() == Long.parseLong(relationDTO.id())
                && relation.getTailCompetency().getId() == Long.parseLong(relationDTO.target()) && relation.getHeadCompetency().getId() == Long.parseLong(relationDTO.source())));
    }

    @Nested
    class GetLearningPath {

        @Test
        @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
        void shouldReturnExisting() throws Exception {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
            final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            final var result = request.get("/api/atlas/courses/" + course.getId() + "/learning-path/me", HttpStatus.OK, LearningPathDTO.class);
            assertThat(result).isEqualTo(LearningPathDTO.of(learningPath));
        }

        @Test
        @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
        void shouldReturnNotFoundIfNotExists() throws Exception {
            course.setLearningPathsEnabled(true);
            course = courseRepository.save(course);
            var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
            student = userTestRepository.findWithLearningPathsByIdElseThrow(student.getId());
            learningPathRepository.deleteAll(student.getLearningPaths());
            request.get("/api/atlas/courses/" + course.getId() + "/learning-path/me", HttpStatus.NOT_FOUND, LearningPathDTO.class);
        }
    }

    @Nested
    class GenerateLearningPath {

        @Test
        @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
        void shouldReturnForbiddenIfNotEnabled() throws Exception {
            request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/learning-path", null, LearningPathDTO.class, HttpStatus.BAD_REQUEST);
        }

        @Test
        @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
        void shouldReturnBadRequestIfAlreadyExists() throws Exception {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
            request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/learning-path", null, LearningPathDTO.class, HttpStatus.CONFLICT);
        }

        @Test
        @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
        void shouldGenerateLearningPath() throws Exception {
            course.setLearningPathsEnabled(true);
            course = courseRepository.save(course);
            final var response = request.postWithResponseBody("/api/atlas/courses/" + course.getId() + "/learning-path", null, LearningPathDTO.class, HttpStatus.CREATED);
            assertThat(response).isNotNull();
            final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
            final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            assertThat(learningPath).isNotNull();
        }
    }

    @Nested
    class StartLearningPath {

        @BeforeEach
        void setup() {
            course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        }

        @Test
        @WithMockUser(username = STUDENT2_OF_COURSE, roles = "USER")
        void shouldReturnForbiddenIfNotOwn() throws Exception {
            final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
            final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            request.patch("/api/atlas/learning-path/" + learningPath.getId() + "/start", null, HttpStatus.FORBIDDEN);
        }

        @Test
        @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
        void shouldReturnBadRequestIfAlreadyStarted() throws Exception {
            final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
            final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            learningPath.setStartedByStudent(true);
            learningPathRepository.save(learningPath);
            request.patch("/api/atlas/learning-path/" + learningPath.getId() + "/start", null, HttpStatus.CONFLICT);
        }

        @Test
        @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
        void shouldStartLearningPath() throws Exception {
            final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
            final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
            request.patch("/api/atlas/learning-path/" + learningPath.getId() + "/start", null, HttpStatus.NO_CONTENT);
            final var updatedLearningPath = learningPathRepository.findByIdElseThrow(learningPath.getId());
            assertThat(updatedLearningPath.isStartedByStudent()).isTrue();
        }
    }

    @Test
    @WithMockUser(username = STUDENT2_OF_COURSE, roles = "USER")
    void testGetCompetencyProgressForLearningPathByOtherStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/atlas/learning-path/" + learningPath.getId() + "/competency-progress", HttpStatus.FORBIDDEN, Set.class);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetCompetencyProgressForLearningPathByOwner() throws Exception {
        testGetCompetencyProgressForLearningPath();
    }

    @Test
    @WithMockUser(username = INSTRUCTOR_OF_COURSE, roles = "INSTRUCTOR")
    void testGetCompetencyProgressForLearningPathByInstructor() throws Exception {
        testGetCompetencyProgressForLearningPath();
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigation() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneWithGroupsAndAuthoritiesAndLearnerProfileByLogin(STUDENT1_OF_COURSE, course.getId()).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        competencyProgressService.updateProgressByLearningObjectSync(textUnit, Set.of(student));

        final var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);

        verifyNavigationResult(result, textUnit, textExercise, null);
        assertThat(result.progress()).isEqualTo(20);
    }

    /**
     * Provides all possible preferences for the course learner profile that can influence the navigation
     *
     * @return all possible combinations for a three tuple with the values between 0 and 5 (inclusive)
     */
    static Stream<Arguments> getLearningPathNavigationPreferencesProvider() {
        return IntStream.range(0, 6 * 6 * 6).mapToObj(i -> Arguments.of(i / 36, i / 6 % 6, i % 6));
    }

    @ParameterizedTest(name = "{displayName} [{index}] {argumentsWithNames}")
    @MethodSource("getLearningPathNavigationPreferencesProvider")
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationPreferences(int aimForGradeOrBonus, int timeInvestment, int repetitionIntensity) throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);

        final var student = userTestRepository.findOneWithGroupsAndAuthoritiesAndLearnerProfileByLogin(STUDENT1_OF_COURSE, course.getId()).orElseThrow();
        CourseLearnerProfile learnerProfile = student.getLearnerProfile().getCourseLearnerProfiles().stream().filter(clp -> clp.getCourse().getId().equals(course.getId()))
                .findFirst().orElseThrow();
        learnerProfile.setAimForGradeOrBonus(aimForGradeOrBonus);
        learnerProfile.setTimeInvestment(timeInvestment);
        learnerProfile.setRepetitionIntensity(repetitionIntensity);
        courseLearnerProfileRepository.save(learnerProfile);

        createAndLinkTextUnit(student, competencies[2], false);
        createAndLinkTextExercise(competencies[3], false);

        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);

        assertThat(result.predecessorLearningObject()).isNotNull();
        assertThat(result.currentLearningObject()).isNotNull();
        assertThat(result.successorLearningObject()).isNotNull();

        assertThat(result.predecessorLearningObject().completed()).isTrue();
        assertThat(result.currentLearningObject().completed()).isFalse();
        assertThat(result.successorLearningObject().completed()).isFalse();
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationEmptyCompetencies() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        textExercise.setCompetencyLinks(Set.of());
        textExercise = exerciseRepository.save(textExercise);

        TextUnit secondTextUnit = createAndLinkTextUnit(student, competencies[2], false);
        TextUnit thirdTextUnit = createAndLinkTextUnit(student, competencies[4], false);

        var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, textUnit, secondTextUnit, thirdTextUnit);

        lectureUnitService.setLectureUnitCompletion(secondTextUnit, student, true);
        result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, secondTextUnit, thirdTextUnit, null);

        lectureUnitService.setLectureUnitCompletion(thirdTextUnit, student, true);
        result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, thirdTextUnit, null, null);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationMultipleLearningObjects() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        TextUnit secondTextUnit = createAndLinkTextUnit(student, competencies[0], false);
        TextUnit thirdTextUnit = createAndLinkTextUnit(student, competencies[0], false);

        var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, List.of(textUnit), List.of(secondTextUnit, thirdTextUnit), List.of(secondTextUnit, thirdTextUnit));
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationDoesNotLeakUnreleasedLearningObjects() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        textExercise.setCompetencyLinks(Set.of());
        textExercise = exerciseRepository.save(textExercise);

        TextUnit secondTextUnit = createAndLinkTextUnit(student, competencies[1], false);
        secondTextUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        lectureUnitRepository.save(secondTextUnit);
        TextUnit thirdTextUnit = createAndLinkTextUnit(student, competencies[2], false);
        TextUnit fourthTextUnit = createAndLinkTextUnit(student, competencies[3], false);
        fourthTextUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        lectureUnitRepository.save(fourthTextUnit);
        TextUnit fifthTextUnit = createAndLinkTextUnit(student, competencies[4], false);

        var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.OK, LearningPathNavigationDTO.class);
        verifyNavigationResult(result, textUnit, thirdTextUnit, fifthTextUnit);
    }

    private LearningPathNavigationObjectDTO.LearningObjectType getLearningObjectType(LearningObject learningObject) {
        return switch (learningObject) {
            case LectureUnit ignored -> LearningPathNavigationObjectDTO.LearningObjectType.LECTURE;
            case Exercise ignored -> LearningPathNavigationObjectDTO.LearningObjectType.EXERCISE;
            default -> throw new IllegalArgumentException("Learning object must be either LectureUnit or Exercise");
        };
    }

    private void verifyNavigationResult(LearningPathNavigationDTO result, LearningObject expectedPredecessor, LearningObject expectedCurrent, LearningObject expectedSuccessor) {
        verifyNavigationObjectResult(expectedPredecessor, result.predecessorLearningObject());
        verifyNavigationObjectResult(expectedCurrent, result.currentLearningObject());
        verifyNavigationObjectResult(expectedSuccessor, result.successorLearningObject());
    }

    private void verifyNavigationObjectResult(LearningObject expectedObject, LearningPathNavigationObjectDTO actualObject) {
        if (expectedObject == null) {
            assertThat(actualObject).isNull();
        }
        else {
            assertThat(actualObject).isNotNull();
            assertThat(actualObject.type()).isEqualTo(getLearningObjectType(expectedObject));
            assertThat(actualObject.id()).isEqualTo(expectedObject.getId());
        }
    }

    private void verifyNavigationResult(LearningPathNavigationDTO result, List<LearningObject> expectedPredecessors, List<LearningObject> expectedCurrents,
            List<LearningObject> expectedSuccessors) {
        verifyNavigationObjectResult(expectedPredecessors, result.predecessorLearningObject());
        verifyNavigationObjectResult(expectedCurrents, result.currentLearningObject());
        verifyNavigationObjectResult(expectedSuccessors, result.successorLearningObject());
    }

    private void verifyNavigationObjectResult(List<LearningObject> expectedObjects, LearningPathNavigationObjectDTO actualObject) {
        if (expectedObjects.isEmpty()) {
            assertThat(actualObject).isNull();
        }
        else {
            assertThat(actualObject).isNotNull();
            assertThat(expectedObjects).anyMatch(expectedObject -> actualObject.type() == getLearningObjectType(expectedObject));
            assertThat(expectedObjects).anyMatch(expectedObject -> actualObject.id() == expectedObject.getId());
        }
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetRelativeLearningPathNavigation() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/relative-navigation?learningObjectId=" + textUnit.getId() + "&learningObjectType="
                + LearningPathNavigationObjectDTO.LearningObjectType.LECTURE + "&competencyId=" + competencies[0].getId(), HttpStatus.OK, LearningPathNavigationDTO.class);

        verifyNavigationResult(result, null, textUnit, textExercise);

        assertThat(result.progress()).isEqualTo(learningPath.getProgress());
    }

    @Test
    @WithMockUser(username = STUDENT2_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationForOtherStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation", HttpStatus.FORBIDDEN, LearningPathNavigationDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningPathNavigationOverview() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation-overview", HttpStatus.OK, LearningPathNavigationOverviewDTO.class);

        // TODO: currently learning objects connected to more than one competency are provided twice in the learning path
        // TODO: this is not a problem for the navigation overview as the duplicates are filtered out

        assertThat(result.learningObjects()).hasSize(2);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1337", roles = "USER")
    void testGetLearningPathNavigationOverviewForOtherStudent() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        request.get("/api/atlas/learning-path/" + learningPath.getId() + "/navigation-overview", HttpStatus.FORBIDDEN, LearningPathNavigationOverviewDTO.class);
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetCompetencyOrderForLearningPath() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.getList("/api/atlas/learning-path/" + learningPath.getId() + "/competencies", HttpStatus.OK, CompetencyNameDTO.class);
        assertThat(result).containsExactlyElementsOf(Arrays.stream(competencies).map(CompetencyNameDTO::of).toList());
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningObjectsForCompetency() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        var result = request.getList("/api/atlas/learning-path/" + learningPath.getId() + "/competencies/" + competencies[0].getId() + "/learning-objects", HttpStatus.OK,
                LearningPathNavigationObjectDTO.class);

        assertThat(result).containsExactly(LearningPathNavigationObjectDTO.of(textUnit, false, true, competencies[0].getId()));

        result = request.getList("/api/atlas/learning-path/" + learningPath.getId() + "/competencies/" + competencies[1].getId() + "/learning-objects", HttpStatus.OK,
                LearningPathNavigationObjectDTO.class);

        assertThat(result).containsExactly(LearningPathNavigationObjectDTO.of(textExercise, false, false, competencies[1].getId()));
    }

    @Test
    @WithMockUser(username = STUDENT1_OF_COURSE, roles = "USER")
    void testGetLearningObjectsForCompetencyMultipleObjects() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());

        List<LearningObject> completedLectureUnits = List.of(createAndLinkTextUnit(student, competencies[4], true), createAndLinkTextUnit(student, competencies[4], true));
        List<LearningObject> finishedExercises = List.of(createAndLinkTextExercise(competencies[4], true), createAndLinkTextExercise(competencies[4], true),
                createAndLinkTextExercise(competencies[4], true));

        List<LearningObject> uncompletedLectureUnits = List.of(createAndLinkTextUnit(student, competencies[4], false));
        List<LearningObject> unfinishedExercises = List.of(createAndLinkTextExercise(competencies[4], false), createAndLinkTextExercise(competencies[4], false));

        int a = completedLectureUnits.size();
        int b = completedLectureUnits.size() + finishedExercises.size();
        int c = completedLectureUnits.size() + finishedExercises.size() + uncompletedLectureUnits.size();
        int d = completedLectureUnits.size() + finishedExercises.size() + uncompletedLectureUnits.size() + unfinishedExercises.size();

        var result = request.getList("/api/atlas/learning-path/" + learningPath.getId() + "/competencies/" + competencies[4].getId() + "/learning-objects", HttpStatus.OK,
                LearningPathNavigationObjectDTO.class);

        assertThat(result).hasSize(d);
        assertThat(result.subList(0, a)).containsExactlyInAnyOrderElementsOf(
                completedLectureUnits.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, false, true, competencies[4].getId())).toList());
        assertThat(result.subList(a, b)).containsExactlyInAnyOrderElementsOf(
                finishedExercises.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, false, true, competencies[4].getId())).toList());
        assertThat(result.subList(b, c)).containsExactlyInAnyOrderElementsOf(
                uncompletedLectureUnits.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, false, false, competencies[4].getId())).toList());
        assertThat(result.subList(c, d)).containsExactlyInAnyOrderElementsOf(
                unfinishedExercises.stream().map(learningObject -> LearningPathNavigationObjectDTO.of(learningObject, false, false, competencies[4].getId())).toList());
    }

    void testGetCompetencyProgressForLearningPath() throws Exception {
        course = learningPathUtilService.enableAndGenerateLearningPathsForCourse(course);
        final var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
        final var learningPath = learningPathRepository.findByCourseIdAndUserIdElseThrow(course.getId(), student.getId());
        final var result = request.get("/api/atlas/learning-path/" + learningPath.getId() + "/competency-progress", HttpStatus.OK, Set.class);
        assertThat(result).hasSize(5);
    }

    private TextExercise createAndLinkTextExercise(Competency competency, boolean withAssessment) {
        TextExercise textExercise = textExerciseUtilService.createIndividualTextExercise(course, past(1), future(1), future(2));
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(textExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        if (withAssessment) {
            var student = userTestRepository.findOneByLogin(STUDENT1_OF_COURSE).orElseThrow();
            studentScoreUtilService.createStudentScore(textExercise, student, 100.0);
        }
        competencyUtilService.linkExerciseToCompetency(competency, textExercise);

        return textExercise;
    }

    private TextUnit createAndLinkTextUnit(User student, Competency competency, boolean completed) {
        TextUnit textUnit = lectureUtilService.createTextUnit();
        lectureUtilService.addLectureUnitsToLecture(lecture, List.of(textUnit));
        textUnit = (TextUnit) competencyUtilService.linkLectureUnitToCompetency(competency, textUnit);

        if (completed) {
            lectureUnitService.setLectureUnitCompletion(textUnit, student, true);
        }

        return textUnit;
    }
}
