package de.tum.cit.aet.artemis.fileupload.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.atlas.competency.util.CompetencyUtilService;
import de.tum.cit.aet.artemis.atlas.domain.competency.Competency;
import de.tum.cit.aet.artemis.atlas.domain.competency.CompetencyExerciseLink;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.exception.NoUniqueQueryException;
import de.tum.cit.aet.artemis.fileupload.domain.FileUploadExercise;
import de.tum.cit.aet.artemis.fileupload.util.FileUploadExerciseUtilService;
import de.tum.cit.aet.artemis.shared.base.AbstractSpringIntegrationIndependentTest;

class FileUploadApiTest extends AbstractSpringIntegrationIndependentTest {

    private static final String TEST_PREFIX = "fileuploadapitest";

    @Autowired
    private FileUploadImportApi fileUploadImportApi;

    @Autowired
    private FileUploadExerciseUtilService fileUploadExerciseUtilService;

    @Autowired
    private CompetencyUtilService competencyUtilService;

    private FileUploadExercise fileUploadExercise;

    private Competency competency;

    @BeforeEach
    void setup() {
        userUtilService.addUsers(TEST_PREFIX, 2, 1, 1, 1);

        fileUploadExercise = fileUploadExerciseUtilService.createFileUploadExercisesWithCourse().getFirst();
        Course course = fileUploadExercise.getCourseViaExerciseGroupOrCourseMember();
        competency = competencyUtilService.createCompetency(course);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testImportFileUploadExerciseViaApi() {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        FileUploadExercise expectedFileUploadExercise = (FileUploadExercise) course.getExercises().stream().findFirst().orElseThrow();
        Course course2 = courseUtilService.addEmptyCourse();
        courseUtilService.enableMessagingForCourse(course2);
        expectedFileUploadExercise.setCourse(course2);
        String uniqueChannelName = "test" + UUID.randomUUID().toString().substring(0, 8);
        expectedFileUploadExercise.setChannelName(uniqueChannelName);
        fileUploadExercise.setCompetencyLinks(Set.of(new CompetencyExerciseLink(competency, fileUploadExercise, 1)));

        var sourceExerciseId = expectedFileUploadExercise.getId();
        Optional<FileUploadExercise> optionalImportedFileUploadExercise = fileUploadImportApi.importFileUploadExercise(sourceExerciseId, expectedFileUploadExercise);
        assertThat(optionalImportedFileUploadExercise).isPresent();
        FileUploadExercise importedFileUploadExercise = optionalImportedFileUploadExercise.get();

        assertThat(importedFileUploadExercise).usingRecursiveComparison().ignoringFields("id", "course", "shortName", "releaseDate", "dueDate", "assessmentDueDate",
                "exampleSolutionPublicationDate", "channelNameTransient", "competencyLinks").isEqualTo(expectedFileUploadExercise);
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testFindFileUploadExerciseWithCompetencyViaApi() throws NoUniqueQueryException {
        Course course = fileUploadExerciseUtilService.addCourseWithFileUploadExercise();
        FileUploadExercise expectedFileUploadExercise = (FileUploadExercise) course.getExercises().stream().findFirst().orElseThrow();

        Optional<FileUploadExercise> optionalExercise = fileUploadImportApi.findUniqueWithCompetenciesByTitleAndCourseId(expectedFileUploadExercise.getTitle(), course.getId());
        assertThat(optionalExercise).isPresent();
        assertThat(optionalExercise.get().getId()).isEqualTo(expectedFileUploadExercise.getId());
    }
}
