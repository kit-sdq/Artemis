package de.tum.cit.aet.artemis.lecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyLectureUnitLink;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.lecture.domain.AttachmentVideoUnit;
import de.tum.cit.aet.artemis.lecture.domain.Lecture;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnit;
import de.tum.cit.aet.artemis.lecture.domain.LectureUnitCompletion;
import de.tum.cit.aet.artemis.lecture.domain.OnlineUnit;
import de.tum.cit.aet.artemis.lecture.domain.TextUnit;
import de.tum.cit.aet.artemis.lecture.dto.LectureUnitForLearningPathNodeDetailsDTO;
import de.tum.cit.aet.artemis.lecture.repository.LectureUnitCompletionRepository;
import de.tum.cit.aet.artemis.lecture.repository.TextUnitRepository;
import de.tum.cit.aet.artemis.lecture.test_repository.LectureTestRepository;
import de.tum.cit.aet.artemis.lecture.util.LectureUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class LectureUnitIntegrationTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "lectureunitintegration";

    @Autowired
    private TextUnitRepository textUnitRepository;

    @Autowired
    private LectureTestRepository lectureRepository;

    @Autowired
    private LectureUnitCompletionRepository lectureUnitCompletionRepository;

    @Autowired
    private LectureUtilService lectureUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    private Lecture lecture1;

    private TextUnit textUnit;

    private TextUnit textUnit2;

    private TextUnit textUnit3;

    @BeforeEach
    void initTestCase() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 0, 1);
        List<Course> courses = courseUtilService.createCoursesWithExercisesAndLectures(TEST_PREFIX, true, 1);
        Course course1 = this.courseRepository.findByIdWithExercisesAndExerciseDetailsAndLecturesElseThrow(courses.getFirst().getId());
        this.lecture1 = course1.getLectures().stream().findFirst().orElseThrow();

        // Add users that are not in the course
        userUtilService.createAndSaveUser(TEST_PREFIX + "student42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "tutor42");
        userUtilService.createAndSaveUser(TEST_PREFIX + "instructor42");

        this.textUnit = lectureUtilService.createTextUnit();
        this.textUnit2 = lectureUtilService.createTextUnit();
        AttachmentVideoUnit attachmentVideoUnit = lectureUtilService.createAttachmentVideoUnit(false);
        OnlineUnit onlineUnit = lectureUtilService.createOnlineUnit();
        // textUnit3 is not one of the lecture units connected to the lecture
        this.textUnit3 = lectureUtilService.createTextUnit();

        lectureUtilService.addLectureUnitsToLecture(course1.getLectures().stream().skip(1).findFirst().orElseThrow(), List.of(textUnit2));
        this.lecture1 = lectureUtilService.addLectureUnitsToLecture(this.lecture1, List.of(this.textUnit, onlineUnit, attachmentVideoUnit));
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        this.textUnit = textUnitRepository.findById(this.textUnit.getId()).orElseThrow();
        this.textUnit2 = textUnitRepository.findById(textUnit2.getId()).orElseThrow();
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

    private void testAllPreAuthorize() throws Exception {
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", List.of(), HttpStatus.FORBIDDEN);
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/0", HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().getFirst().getId();
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnitId, HttpStatus.OK);
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnitId);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_shouldUnlinkCompetency() throws Exception {
        var lectureUnit = lecture1.getLectureUnits().getFirst();
        var competency = competencyUtilService.createCompetency(lecture1.getCourse());
        lectureUnit.setCompetencyLinks(Set.of(new CompetencyLectureUnitLink(competency, lectureUnit, 1)));
        lectureRepository.save(lecture1);

        var lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits().getFirst().getCompetencyLinks()).isNotEmpty();

        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnit.getId(), HttpStatus.OK);
        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnit.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_shouldRemoveCompletions() throws Exception {
        var lectureUnit = lecture1.getLectureUnits().getFirst();
        var user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        LectureUnitCompletion completion = new LectureUnitCompletion();
        completion.setLectureUnit(lectureUnit);
        completion.setUser(user);
        completion.setCompletedAt(ZonedDateTime.now().minusDays(1));
        lectureUnitCompletionRepository.save(completion);

        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId())).isPresent();

        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnit.getId(), HttpStatus.OK);

        this.lecture1 = lectureRepository.findByIdWithLectureUnitsAndAttachmentsElseThrow(lecture1.getId());
        assertThat(this.lecture1.getLectureUnits().stream().map(DomainObject::getId)).doesNotContain(lectureUnit.getId());
        assertThat(lectureUnitCompletionRepository.findByLectureUnitIdAndUserId(lectureUnit.getId(), user.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void deleteLectureUnit_asInstructorNotInCourse_shouldReturnForbidden() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().getFirst().getId();
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lectureUnitId, HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_notPartOfLecture_shouldReturnBadRequest() throws Exception {
        var lectureUnitId = lecture1.getLectureUnits().getFirst().getId();
        request.delete("/api/lecture/lectures/" + Integer.MAX_VALUE + "/lecture-units/" + lectureUnitId, HttpStatus.BAD_REQUEST);
    }

    /**
     * We have to make sure to reorder the list of lecture units when we delete a lecture unit to prevent hibernate
     * from entering nulls into the list to keep the order of lecture units
     */
    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void deleteLectureUnit_FirstLectureUnit_ShouldReorderList() throws Exception {
        Lecture lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits()).hasSize(3);
        LectureUnit firstLectureUnit = lecture.getLectureUnits().stream().findFirst().orElseThrow();
        request.delete("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + firstLectureUnit.getId(), HttpStatus.OK);
        lecture = lectureRepository.findByIdWithLectureUnitsAndCompetenciesElseThrow(lecture1.getId());
        assertThat(lecture.getLectureUnits()).hasSize(2).noneMatch(Objects::isNull);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_asInstructor_shouldUpdateLectureUnitOrder() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toCollection(ArrayList::new));
        Collections.swap(newlyOrderedList, 0, 1);
        List<LectureUnit> returnedList = request.putWithResponseBodyList("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, LectureUnit.class,
                HttpStatus.OK);
        assertThat(returnedList.getFirst().getId()).isEqualTo(newlyOrderedList.getFirst());
        assertThat(returnedList.get(1).getId()).isEqualTo(newlyOrderedList.get(1));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_wrongSizeOfIds_shouldReturnBadRequest() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).skip(1).toList();
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_newTextUnitInOrderedList_shouldReturnBadRequest() throws Exception {
        List<Long> newlyOrderedList = lecture1.getLectureUnits().stream().map(DomainObject::getId).collect(Collectors.toCollection(ArrayList::new));
        // textUnit3 is not in specified lecture
        newlyOrderedList.set(1, this.textUnit3.getId());
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", newlyOrderedList, HttpStatus.BAD_REQUEST);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_asInstructorWithWrongLectureId_shouldReturnNotFound() throws Exception {
        request.put("/api/lecture/lectures/" + 0L + "/lecture-units-order", List.of(), HttpStatus.NOT_FOUND);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor42", roles = "INSTRUCTOR")
    void updateLectureUnitOrder_notInstructorInCourse_shouldReturnForbidden() throws Exception {
        request.put("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units-order", List.of(), HttpStatus.FORBIDDEN);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion() throws Exception {
        // Set lecture unit as completed for current user
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().getFirst().getId() + "/completion?completed=true",
                null, HttpStatus.OK, null);

        this.lecture1 = lectureRepository.findByIdWithAttachmentsAndLectureUnitsAndCompletionsElseThrow(lecture1.getId());
        LectureUnit lectureUnit = this.lecture1.getLectureUnits().getFirst();

        assertThat(lectureUnit.getCompletedUsers()).isNotEmpty();
        assertThat(lectureUnit.isCompletedFor(userTestRepository.getUser())).isTrue();

        // Set lecture unit as uncompleted for user
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().getFirst().getId() + "/completion?completed=false",
                null, HttpStatus.OK, null);

        this.lecture1 = lectureRepository.findByIdWithAttachmentsAndLectureUnitsAndCompletionsElseThrow(lecture1.getId());
        lectureUnit = this.lecture1.getLectureUnits().getFirst();

        assertThat(lectureUnit.getCompletedUsers()).isEmpty();
        assertThat(lectureUnit.isCompletedFor(userTestRepository.getUser())).isFalse();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion_lectureUnitNotPartOfLecture_shouldReturnBadRequest() throws Exception {
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit2.getId() + "/completion?completed=true", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion_withoutLecture_shouldReturnForbidden() throws Exception {
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit3.getId() + "/completion?completed=true", null,
                HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void setLectureUnitCompletion_lectureUnitNotVisible_shouldReturnBadRequest() throws Exception {
        this.textUnit.setReleaseDate(ZonedDateTime.now().plusDays(1));
        textUnitRepository.save(this.textUnit);
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + this.textUnit.getId() + "/completion?completed=true", null,
                HttpStatus.BAD_REQUEST, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void setLectureUnitCompletion_shouldReturnForbidden() throws Exception {
        // User is not in same course as lecture unit
        request.postWithoutLocation("/api/lecture/lectures/" + lecture1.getId() + "/lecture-units/" + lecture1.getLectureUnits().getFirst().getId() + "/completion?completed=true",
                null, HttpStatus.FORBIDDEN, null);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testGetLectureUnitForLearningPathNodeDetailsAsStudentOfCourse() throws Exception {
        final var result = request.get("/api/lecture/lecture-units/" + textUnit.getId() + "/for-learning-path-node-details", HttpStatus.OK,
                LectureUnitForLearningPathNodeDetailsDTO.class);
        assertThat(result).isEqualTo(LectureUnitForLearningPathNodeDetailsDTO.of(textUnit));
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "student42", roles = "USER")
    void testGetLectureUnitForLearningPathNodeDetailsAsStudentNotInCourse() throws Exception {
        request.get("/api/lecture/lecture-units/" + textUnit.getId() + "/for-learning-path-node-details", HttpStatus.FORBIDDEN, LectureUnitForLearningPathNodeDetailsDTO.class);
    }
}
