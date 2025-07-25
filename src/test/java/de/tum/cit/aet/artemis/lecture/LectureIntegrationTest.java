package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.communication.domain.conversation.Channel;
import de.tum.cit.aet.artemis.communication.repository.conversation.ChannelRepository;
import de.tum.cit.aet.artemis.communication.util.ConversationUtilService;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.util.PageableSearchUtilService;
import de.tum.cit.aet.artemis.lecture.domain.Attachment;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.ExerciseUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.repository.AttachmentRepository;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.AttachmentVideoUnitTestRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureFactory;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;
import de.tum.cit.aet.artemis.text.domain.TextExercise;
import de.tum.cit.aet.artemis.text.repository.TextExerciseRepository;

class LectureIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lectureintegrationtest";

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private TextExerciseRepository textExerciseRepository;

    @Autowired
    private AttachmentRepository attachmentRepository;

    @Autowired
    private AttachmentVideoUnitTestRepository attachmentVideoUnitRepository;

    @Autowired
    ChannelRepository channelRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private PageableSearchUtilService pageableSearchUtilService;

    @Autowired
    private ConversationUtilService conversationUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    @Autowired
    private LectureUnitRepository lectureUnitRepository;

    private Attachment attachmentDirectOfLecture;

    private Attachment attachmentOfAttachmentVideoUnit;

    private TextExercise textExercise;

    private Course course1;

    private Lecture lecture1;

    private AttachmentVideoUnit attachmentVideoUnit;

    private Competency competency;

    @BeforeEach
    void initTestCase() throws Exception {
        int numberOfTutors = 2;
        userUtilService.addUsers(TEST_PREFIX, 2, numberOfTutors, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, true, numberOfTutors);
        this.course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        var lecture = this.course1.getLectures().stream().findFirst().orElseThrow();
        lecture.setTitle("Lecture " + new Random().nextInt()); // needed for search by title
        Channel channel = new Channel();
        channel.setCourse(course1);
        channel.setIsAnnouncementChannel(false);
        channel.setIsPublic(true);
        channel.setIsArchived(false);
        channel.setName("lecture-channel");
        lecture.setTitle("Lecture " + lecture.getId()); // needed for search by title
        lecture1 = lectureRepository.save(lecture);
        channel.setLecture(this.lecture1);
        channelRepository.save(channel);
        textExercise = textExerciseRepository.findByCourseIdWithCategories(course1.getId()).stream().findFirst().orElseThrow();

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        // Setting up a lecture with various kinds of content
        ExerciseUnit exerciseUnit = lectureUtilService.createExerciseUnit(textExercise);
        attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(true);
        attachmentOfAttachmentVideoUnit = attachmentVideoUnit.getAttachment();
        TextUnit textUnit = lectureUtilService.createTextUnit();
        OnlineUnit onlineUnit = lectureUtilService.createOnlineUnit();
        addAttachmentToLecture();

        lecture1 = lectureUtilService.addLectureUnitsToLecture(this.lecture1, List.of(exerciseUnit, attachmentVideoUnit, textUnit, onlineUnit));

        competency = competencyUtilService.createCompetency(course1);
        competencyUtilService.linkExerciseToCompetency(competency, textExercise);
    }

    private void addAttachmentToLecture() {
        this.attachmentDirectOfLecture = LectureFactory.generateAttachmentWithFile(null, this.lecture1.getId(), false);
        this.attachmentDirectOfLecture.setLecture(this.lecture1);
        this.attachmentDirectOfLecture = attachmentRepository.save(this.attachmentDirectOfLecture);
        this.lecture1.addAttachments(this.attachmentDirectOfLecture);
        this.lecture1 = lectureRepository.save(this.lecture1);
    }

    private void testAllPreAuthorize() throws Exception {
        request.postWithResponseBody("/api/lecture/lectures", new Lecture(), Lecture.class, HttpStatus.FORBIDDEN);
        request.putWithResponseBody("/api/lecture/lectures", new Lecture(), Lecture.class, HttpStatus.FORBIDDEN);
        request.getList("/api/lecture/courses/" + course1.getId() + "/lectures", HttpStatus.FORBIDDEN, Lecture.class);
        request.delete("/api/lecture/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN);
        request.postWithResponseBody("/api/lecture/lectures/import/" + lecture1.getId() + "?courseId=" + course1.getId(), null, Lecture.class, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testAll_asTutor() throws Exception {
        this.testAllPreAuthorize();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testAll_asStudent() throws Exception {
        this.testAllPreAuthorize();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = { "lecture-loremipsum", "" })
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLecture_correctRequestBody_shouldCreateLecture(String channelName) throws Exception {
        Course course = courseRepository.findByIdElseThrow(this.course1.getId());
        courseUtilService.enableMessagingForCourse(course);

        conversationUtilService.createCourseWideChannel(course, "loremipsum");

        Lecture lecture = new Lecture();
        lecture.setTitle("loremIpsum-()!?");
        lecture.setCourse(course);
        lecture.setDescription("loremIpsum");
        lecture.setChannelName(channelName);

        lecture.setVisibleDate(ZonedDateTime.now().minusDays(1));
        lecture.setStartDate(ZonedDateTime.now());
        lecture.setEndDate(ZonedDateTime.now().plusWeeks(1));
        Lecture returnedLecture = request.postWithResponseBody("/api/lecture/lectures", lecture, Lecture.class, HttpStatus.CREATED);

        Channel channel = channelRepository.findChannelByLectureId(returnedLecture.getId());

        assertThat(returnedLecture).isNotNull();
        assertThat(returnedLecture.getId()).isNotNull();
        assertThat(returnedLecture.getTitle()).isEqualTo(lecture.getTitle());
        assertThat(returnedLecture.getCourse().getId()).isEqualTo(lecture.getCourse().getId());
        assertThat(returnedLecture.getDescription()).isEqualTo(lecture.getDescription());
        assertThat(returnedLecture.getVisibleDate()).isEqualTo(lecture.getVisibleDate());
        assertThat(returnedLecture.getStartDate()).isEqualTo(lecture.getStartDate());
        assertThat(returnedLecture.getEndDate()).isEqualTo(lecture.getEndDate());
        assertThat(channel).isNotNull();
        assertThat(channel.getName()).isEqualTo("lecture-loremipsum"); // note "i" is lower case as a channel name should not contain upper case letters
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void createLecture_alreadyId_shouldReturnBadRequest() throws Exception {
        Lecture lecture = new Lecture();
        lecture.setId(1L);
        lecture.setChannelName("test");
        request.postWithResponseBody("/api/lecture/lectures", lecture, Lecture.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLecture_correctRequestBody_shouldUpdateLecture() throws Exception {
        Lecture originalLecture = lectureRepository.findById(lecture1.getId()).orElseThrow();
        originalLecture.setTitle("Updated");
        originalLecture.setDescription("Updated");
        ZonedDateTime updatedDate = ZonedDateTime.now().plusMonths(3);
        originalLecture.setVisibleDate(updatedDate);
        originalLecture.setStartDate(updatedDate);
        originalLecture.setEndDate(updatedDate);
        String editedChannelName = "edited-lecture-channel";
        // create channel with same name
        conversationUtilService.createCourseWideChannel(originalLecture.getCourse(), editedChannelName);
        originalLecture.setChannelName(editedChannelName);
        // lecture channel should be updated despite another channel with the same name
        Lecture updatedLecture = request.putWithResponseBody("/api/lecture/lectures", originalLecture, Lecture.class, HttpStatus.OK);

        Channel channel = channelRepository.findChannelByLectureId(updatedLecture.getId());

        assertThat(channel).isNotNull();
        assertThat(channel.getName()).isEqualTo(editedChannelName);
        assertThat(updatedLecture.getTitle()).isEqualTo("Updated");
        assertThat(updatedLecture.getDescription()).isEqualTo("Updated");
        assertThat(updatedLecture.getVisibleDate()).isEqualTo(updatedDate);
        assertThat(updatedLecture.getStartDate()).isEqualTo(updatedDate);
        assertThat(updatedLecture.getEndDate()).isEqualTo(updatedDate);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLecture_NoId_shouldReturnBadRequest() throws Exception {
        Lecture originalLecture = lectureRepository.findByIdWithLectureUnitsAndAttachments(lecture1.getId()).orElseThrow();
        originalLecture.setId(null);
        originalLecture.setChannelName("test");

        request.putWithResponseBody("/api/lecture/lectures", originalLecture, Lecture.class, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLectureForCourse_withOutLectureUnits_shouldGetLecturesWithOutLectureUnits() throws Exception {
        List<Lecture> returnedLectures = request.getList("/api/lecture/courses/" + course1.getId() + "/lectures", HttpStatus.OK, Lecture.class);
        assertThat(returnedLectures).hasSize(2);
        Lecture lecture = returnedLectures.stream().filter(l -> l.getId().equals(lecture1.getId())).findFirst().orElseThrow();
        assertThat(lecture.getLectureUnits()).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLectureForCourse_WithLectureUnitsWithSlides_shouldGetLecturesWithLectureUnitsWithSlides() throws Exception {
        int numberOfSlides = 2;
        Lecture lectureWithSlides = LectureFactory.generateLecture(ZonedDateTime.now().minusDays(5), ZonedDateTime.now().plusDays(5), course1);
        lectureWithSlides = lectureRepository.save(lectureWithSlides);
        AttachmentVideoUnit attachmentVideoUnitWithSlides = lectureUtilService.createAttachmentVideoUnitWithSlides(numberOfSlides);
        lectureWithSlides = lectureUtilService.addLectureUnitsToLecture(lectureWithSlides, List.of(attachmentVideoUnitWithSlides));

        AttachmentVideoUnit attachmentVideoUnitWithoutSlides = lectureUtilService.createAttachmentVideoUnitWithoutAttachment();
        lectureUtilService.addLectureUnitsToLecture(lectureWithSlides, List.of(attachmentVideoUnitWithoutSlides));

        List<Lecture> returnedLectures = request.getList("/api/lecture/courses/" + course1.getId() + "/lectures-with-slides", HttpStatus.OK, Lecture.class);

        final Lecture finalLectureWithSlides = lectureWithSlides;
        Lecture filteredLecture = returnedLectures.stream().filter(lecture -> lecture.getId().equals(finalLectureWithSlides.getId())).findFirst().orElseThrow();

        assertThat(filteredLecture.getLectureUnits()).hasSize(2);
        assertThat(filteredLecture.getLectureUnits()).contains(attachmentVideoUnitWithSlides);
        AttachmentVideoUnit attachmentVideoUnit = (AttachmentVideoUnit) filteredLecture.getLectureUnits().getFirst();
        assertThat(attachmentVideoUnit.getSlides()).hasSize(numberOfSlides);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void getLectureForCourse_withLectureUnits_shouldGetLecturesWithLectureUnits() throws Exception {
        List<Lecture> returnedLectures = request.getList("/api/lecture/courses/" + course1.getId() + "/lectures?withLectureUnits=true", HttpStatus.OK, Lecture.class);
        assertThat(returnedLectures).hasSize(2);
        Lecture lecture = returnedLectures.stream().filter(l -> l.getId().equals(lecture1.getId())).findFirst().orElseThrow();
        assertThat(lecture.getLectureUnits()).hasSize(4);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void getLecture_asStudentNotInCourse_shouldReturnForbidden() throws Exception {
        request.get("/api/lecture/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN, Lecture.class);
        request.get("/api/lecture/lectures/" + lecture1.getId() + "/details", HttpStatus.FORBIDDEN, Lecture.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLecture_ExerciseAndAttachmentReleased_shouldGetLectureWithAllLectureUnits() throws Exception {
        Lecture receivedLectureWithDetails = request.get("/api/lecture/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(4);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).toList().getFirst().getCompetencyLinks())
                .hasSize(1);
        assertThat(receivedLectureWithDetails.getAttachments()).hasSize(2);

        testGetLecture(lecture1.getId());
    }

    private void testGetLecture(Long lectureId) throws Exception {
        Lecture originalLecture = request.get("/api/lecture/lectures/" + lectureId, HttpStatus.OK, Lecture.class);
        assertThat(originalLecture.getId()).isEqualTo(lectureId);
        // should not fetch lecture units or posts
        assertThat(originalLecture.getLectureUnits()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLecture_ExerciseNotReleased_shouldGetLectureWithoutExerciseUnit() throws Exception {
        TextExercise exercise = textExerciseRepository.findByIdElseThrow(textExercise.getId());
        exercise.setReleaseDate(ZonedDateTime.now().plusDays(10));
        textExerciseRepository.saveAndFlush(exercise);

        Lecture receivedLectureWithDetails = request.get("/api/lecture/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(3);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).toList()).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        receivedLectureWithDetails = request.get("/api/lecture/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(4);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof ExerciseUnit).toList()).isNotEmpty();

        testGetLecture(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void getLecture_LectureAttachmentOrAttachmentVideoUnitNotReleased_shouldGetLectureWithoutAttachmentVideoUnitAndAttachment() throws Exception {
        var newReleaseDate = ZonedDateTime.now().plusDays(10);

        Attachment unitAttachment = attachmentRepository.findById(attachmentOfAttachmentVideoUnit.getId()).orElseThrow();
        attachmentVideoUnit.setReleaseDate(newReleaseDate);
        unitAttachment.setReleaseDate(newReleaseDate);
        attachmentVideoUnitRepository.save(attachmentVideoUnit);

        Attachment lectureAttachment = attachmentRepository.findById(attachmentDirectOfLecture.getId()).orElseThrow();
        lectureAttachment.setReleaseDate(newReleaseDate);

        attachmentRepository.saveAll(Set.of(unitAttachment, lectureAttachment));

        Lecture receivedLectureWithDetails = request.get("/api/lecture/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getAttachments().stream().filter(attachment -> attachment.getId().equals(lectureAttachment.getId())).findFirst()).isEmpty();
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(3);
        assertThat(receivedLectureWithDetails.getLectureUnits().stream().filter(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit).toList()).isEmpty();

        // now we test that it is included when the user is at least a teaching assistant
        userUtilService.changeUser(TEST_PREFIX + "tutor1");
        receivedLectureWithDetails = request.get("/api/lecture/lectures/" + lecture1.getId() + "/details", HttpStatus.OK, Lecture.class);
        assertThat(receivedLectureWithDetails.getId()).isEqualTo(lecture1.getId());
        assertThat(receivedLectureWithDetails.getAttachments()).anyMatch(attachment -> attachment.getId().equals(lectureAttachment.getId()));
        assertThat(receivedLectureWithDetails.getLectureUnits()).hasSize(4).anyMatch(lectureUnit -> lectureUnit instanceof AttachmentVideoUnit);
        testGetLecture(lecture1.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_lectureExists_shouldDeleteLecture() throws Exception {
        attachmentVideoUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, attachmentVideoUnit, 1)));
        lectureUnitRepository.save(attachmentVideoUnit);

        request.delete("/api/lecture/lectures/" + lecture1.getId(), HttpStatus.OK);
        Optional<Lecture> lectureOptional = lectureRepository.findById(lecture1.getId());
        assertThat(lectureOptional).isEmpty();

        // ExerciseUnits do not have competencies, their exercises do
        verify(competencyProgressApi, timeout(1000).times(lecture1.getLectureUnits().size() - 1)).updateProgressForUpdatedLearningObjectAsync(any(), eq(Optional.empty()));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureWithChannel() throws Exception {
        Lecture lecture = lectureUtilService.createCourseWithLecture(true);
        Channel lectureChannel = lectureUtilService.addLectureChannel(lecture);

        request.delete("/api/lecture/lectures/" + lecture.getId(), HttpStatus.OK);

        Optional<Channel> lectureChannelAfterDelete = channelRepository.findById(lectureChannel.getId());
        assertThat(lectureChannelAfterDelete).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void deleteLecture_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        request.delete("/api/lecture/lectures/" + lecture1.getId(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLecture_lectureDoesNot_shouldReturnNotFound() throws Exception {
        request.delete("/api/lecture/lectures/" + 0, HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetLectureTitleAsInstuctor() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "tutor1", roles = "TA")
    void testGetLectureTitleAsTeachingAssistant() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetLectureTitleAsUser() throws Exception {
        // Only user and role matter, so we can re-use the logic
        testGetLectureTitle();
    }

    private void testGetLectureTitle() throws Exception {
        Lecture lecture = new Lecture();
        lecture.setTitle("Test Lecture");
        lectureRepository.save(lecture);

        final var title = request.get("/api/lecture/lectures/" + lecture.getId() + "/title", HttpStatus.OK, String.class);
        assertThat(title).isEqualTo(lecture.getTitle());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "user1", roles = "USER")
    void testGetLectureTitleForNonExistingLecture() throws Exception {
        request.get("/api/lecture/lectures/123124123123/title", HttpStatus.NOT_FOUND, String.class);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void testInstructorGetsOnlyResultsFromOwningCourses() throws Exception {
        final var search = pageableSearchUtilService.configureSearch("");
        final var result = request.getSearchResult("/api/lecture/lectures", HttpStatus.OK, Lecture.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).isNullOrEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testInstructorGetsResultsFromOwningCoursesNotEmpty() throws Exception {
        final var search = pageableSearchUtilService.configureSearch(lecture1.getTitle());
        final var result = request.getSearchResult("/api/lecture/lectures", HttpStatus.OK, Lecture.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void testAdminGetsResultsFromAllCourses() throws Exception {
        final var search = pageableSearchUtilService.configureSearch(lecture1.getTitle());
        final var result = request.getSearchResult("/api/lecture/lectures", HttpStatus.OK, Lecture.class, pageableSearchUtilService.searchMapping(search));
        assertThat(result.getResultsOnPage()).hasSize(1);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImport() throws Exception {
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);

        Lecture lecture = request.postWithResponseBody("/api/lecture/lectures/import/" + lecture1.getId() + "?courseId=" + course2.getId(), null, Lecture.class,
                HttpStatus.CREATED);

        // Assert that all lecture units (except exercise units) were copied
        assertThat(lecture.getLectureUnits().stream().map(LectureUnit::getName).toList()).containsExactlyElementsOf(
                this.lecture1.getLectureUnits().stream().filter(lectureUnit -> !(lectureUnit instanceof ExerciseUnit)).map(LectureUnit::getName).toList());

        assertThat(lecture.getAttachments().stream().map(Attachment::getName).toList())
                .containsExactlyElementsOf(this.lecture1.getAttachments().stream().map(Attachment::getName).toList());

        Channel channel = channelRepository.findChannelByLectureId(lecture.getId());
        assertThat(channel).isNotNull();
        assertThat(channel.getName()).isEqualTo("lecture-" + lecture.getTitle().toLowerCase().replaceAll("[-\\s]+", "-")); // default name of imported lecture channel
    }
}
