package de.tum.cit.aet.artemis.programming.icl;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;

import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.programming.AbstractProgrammingIntegrationLocalCILocalVCTest;
import de.tum.cit.aet.artemis.programming.domain.AuthenticationMechanism;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.TemplateProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsAccessLog;
import de.tum.cit.aet.artemis.programming.dto.VcsAccessLogDTO;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.web.repository.RepositoryActionType;

class LocalVCLocalCIParticipationIntegrationTest extends AbstractProgrammingIntegrationLocalCILocalVCTest {

    private static final String TEST_PREFIX = "participationlocalvclocalci";

    private ProgrammingExercise programmingExercise;

    @BeforeEach
    void initTestCase() {
        userUtilService.addUsers(TEST_PREFIX, 4, 2, 0, 2);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
    }

    @Disabled // TODO enable - works isolated
    @Test
    @WithMockUser(username = TEST_PREFIX + "student1", roles = "USER")
    void testStartParticipation() throws Exception {
        userUtilService.addUsers(TEST_PREFIX, 1, 0, 0, 0);
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        String projectKey = programmingExercise.getProjectKey();
        programmingExercise.setStartDate(ZonedDateTime.now().minusHours(1));
        // Set the branch to null to force the usage of LocalVCService#getDefaultBranch().
        programmingExercise.getBuildConfig().setBranch(null);
        programmingExerciseBuildConfigRepository.save(programmingExercise.getBuildConfig());
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithAllParticipationsAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        // Prepare the template repository to copy the student assignment repository from.
        String templateRepositorySlug = projectKey.toLowerCase() + "-exercise";
        TemplateProgrammingExerciseParticipation templateParticipation = programmingExercise.getTemplateParticipation();
        templateParticipation.setRepositoryUri(localVCBaseUri + "/git/" + projectKey + "/" + templateRepositorySlug + ".git");
        templateProgrammingExerciseParticipationRepository.save(templateParticipation);
        LocalRepository templateRepository = localVCLocalCITestService.createAndConfigureLocalRepository(projectKey, templateRepositorySlug);

        User user = userUtilService.getUserByLogin(TEST_PREFIX + "student1");

        StudentParticipation participation = request.postWithResponseBody("/api/exercise/exercises/" + programmingExercise.getId() + "/participations", null,
                StudentParticipation.class, HttpStatus.CREATED);
        assertThat(participation).isNotNull();
        assertThat(participation.isPracticeMode()).isFalse();
        assertThat(participation.getStudent()).contains(user);
        LocalVCRepositoryUri studentAssignmentRepositoryUri = new LocalVCRepositoryUri(projectKey, projectKey.toLowerCase() + "-" + TEST_PREFIX + "student1", localVCBaseUri);
        assertThat(studentAssignmentRepositoryUri.getLocalRepositoryPath(localVCBasePath)).exists();

        var vcsAccessToken = request.get("/api/core/account/participation-vcs-access-token?participationId=" + participation.getId(), HttpStatus.OK, String.class);
        assertThat(vcsAccessToken).isNotNull();
        assertThat(vcsAccessToken).startsWith("vcpat");

        templateRepository.resetLocalRepo();
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLog() throws Exception {
        var participation = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, TEST_PREFIX + "instructor1");
        var user = userTestRepository.getUser();
        vcsAccessLogRepository.save(new VcsAccessLog(user, participation, "instructor", "instructorMail@mail.de", RepositoryActionType.READ, AuthenticationMechanism.SSH, "", ""));
        var li = request.getList("/api/programming/programming-exercise-participations/" + participation.getId() + "/vcs-access-log", HttpStatus.OK, VcsAccessLogDTO.class);
        assertThat(li.size()).isEqualTo(1);
        assertThat(li.getFirst().userId()).isEqualTo(user.getId());
    }

    @Test
    @WithMockUser(username = TEST_PREFIX + "instructor1", roles = "INSTRUCTOR")
    void testGetVcsAccessLogOfTemplateParticipation() throws Exception {
        var user = userTestRepository.getUser();
        vcsAccessLogRepository.save(new VcsAccessLog(user, programmingExercise.getTemplateParticipation(), "instructor", "instructorMail@mail.de", RepositoryActionType.READ,
                AuthenticationMechanism.SSH, "", ""));
        var li = request.getList("/api/programming/programming-exercise/" + programmingExercise.getId() + "/vcs-access-log/TEMPLATE", HttpStatus.OK, VcsAccessLogDTO.class);
        assertThat(li.size()).isEqualTo(1);
        assertThat(li.getFirst().userId()).isEqualTo(user.getId());
    }

}
