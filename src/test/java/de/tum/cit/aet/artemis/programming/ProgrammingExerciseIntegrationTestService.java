package de.tum.cit.aet.artemis.programming;

import static de.tum.cit.aet.artemis.core.util.TestResourceUtils.HalfSecond;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.SOLUTION;
import static de.tum.cit.aet.artemis.programming.domain.build.BuildPlanType.TEMPLATE;
import static de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResourceErrorKeys.INVALID_SOLUTION_BUILD_PLAN_ID;
import static de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResourceErrorKeys.INVALID_SOLUTION_REPOSITORY_URL;
import static de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResourceErrorKeys.INVALID_TEMPLATE_BUILD_PLAN_ID;
import static de.tum.cit.aet.artemis.programming.web.ProgrammingExerciseResourceErrorKeys.INVALID_TEMPLATE_REPOSITORY_URL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyBoolean;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static tech.jhipster.config.JHipsterConstants.SPRING_PROFILE_TEST;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.assertj.core.data.Offset;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.EmptyCommitException;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.StoredConfig;
import org.eclipse.jgit.revwalk.RevCommit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import com.fasterxml.jackson.core.type.TypeReference;

import de.tum.cit.aet.artemis.assessment.domain.AssessmentType;
import de.tum.cit.aet.artemis.assessment.domain.GradingCriterion;
import de.tum.cit.aet.artemis.assessment.domain.Visibility;
import de.tum.cit.aet.artemis.assessment.repository.GradingCriterionRepository;
import de.tum.cit.aet.artemis.assessment.util.GradingCriterionUtil;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.DomainObject;
import de.tum.cit.aet.artemis.core.dto.RepositoryExportOptionsDTO;
import de.tum.cit.aet.artemis.core.service.FileService;
import de.tum.cit.aet.artemis.core.test_repository.CourseTestRepository;
import de.tum.cit.aet.artemis.core.user.util.UserUtilService;
import de.tum.cit.aet.artemis.core.util.CourseUtilService;
import de.tum.cit.aet.artemis.core.util.RequestUtilService;
import de.tum.cit.aet.artemis.core.util.TestResourceUtils;
import de.tum.cit.aet.artemis.exercise.domain.ExerciseMode;
import de.tum.cit.aet.artemis.exercise.domain.IncludedInOverallScore;
import de.tum.cit.aet.artemis.exercise.domain.Team;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.participation.util.ParticipationUtilService;
import de.tum.cit.aet.artemis.exercise.repository.TeamRepository;
import de.tum.cit.aet.artemis.exercise.util.ExerciseUtilService;
import de.tum.cit.aet.artemis.fileupload.util.ZipFileTestUtilService;
import de.tum.cit.aet.artemis.plagiarism.PlagiarismUtilService;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismComparison;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismStatus;
import de.tum.cit.aet.artemis.plagiarism.dto.PlagiarismResultDTO;
import de.tum.cit.aet.artemis.plagiarism.service.ProgrammingPlagiarismDetectionService;
import de.tum.cit.aet.artemis.programming.domain.AuxiliaryRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseTestCase;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingLanguage;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingSubmission;
import de.tum.cit.aet.artemis.programming.domain.ProjectType;
import de.tum.cit.aet.artemis.programming.domain.RepositoryType;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseResetOptionsDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseDTO;
import de.tum.cit.aet.artemis.programming.dto.ProgrammingExerciseTestCaseStateDTO;
import de.tum.cit.aet.artemis.programming.repository.AuxiliaryRepositoryRepository;
import de.tum.cit.aet.artemis.programming.service.GitService;
import de.tum.cit.aet.artemis.programming.service.UriService;
import de.tum.cit.aet.artemis.programming.service.ci.ContinuousIntegrationService;
import de.tum.cit.aet.artemis.programming.service.localvc.LocalVCRepositoryUri;
import de.tum.cit.aet.artemis.programming.service.vcs.VersionControlService;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseStudentParticipationTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestCaseTestRepository;
import de.tum.cit.aet.artemis.programming.test_repository.ProgrammingExerciseTestRepository;
import de.tum.cit.aet.artemis.programming.util.GitUtilService;
import de.tum.cit.aet.artemis.programming.util.LocalRepository;
import de.tum.cit.aet.artemis.programming.util.MockDelegate;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseFactory;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseParticipationUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingExerciseUtilService;
import de.tum.cit.aet.artemis.programming.util.ProgrammingUtilTestService;
import de.tum.cit.aet.artemis.text.util.TextExerciseUtilService;

/**
 * Note: this class should be independent of the actual VCS and CIS and contains common test logic for scenarios:
 * 1) LocalVC + LocalCI
 */
@Lazy
@Service
@Profile(SPRING_PROFILE_TEST)
public class ProgrammingExerciseIntegrationTestService {

    private static final String NON_EXISTING_ID = Integer.toString(Integer.MAX_VALUE);

    private String userPrefix;

    @Value("${artemis.version-control.default-branch:main}")
    private String defaultBranch;

    @Value("${artemis.version-control.local-vcs-repo-path}")
    private Path localVCRepoPath;

    @Autowired
    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private FileService fileService;

    @Autowired
    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private UriService uriService;

    @Autowired
    private GitUtilService gitUtilService;

    @Autowired
    private CourseTestRepository courseRepository;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseRepository;

    @Autowired
    private ProgrammingExerciseStudentParticipationTestRepository programmingExerciseStudentParticipationRepository;

    @Autowired
    private ProgrammingExerciseTestCaseTestRepository programmingExerciseTestCaseRepository;

    @Autowired
    private AuxiliaryRepositoryRepository auxiliaryRepositoryRepository;

    @Autowired
    private RequestUtilService request;

    @Autowired
    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private GitService gitService;

    @Autowired
    private UserUtilService userUtilService;

    @Autowired
    private ProgrammingExerciseUtilService programmingExerciseUtilService;

    @Autowired
    private ProgrammingExerciseParticipationUtilService programmingExerciseParticipationUtilService;

    @Autowired
    private ExerciseUtilService exerciseUtilService;

    @Autowired
    private ParticipationUtilService participationUtilService;

    @Autowired
    private CourseUtilService courseUtilService;

    @Autowired
    private PlagiarismUtilService plagiarismUtilService;

    @Autowired
    private TextExerciseUtilService textExerciseUtilService;

    @Autowired
    private ProgrammingExerciseTestRepository programmingExerciseTestRepository;

    @Autowired
    private ZipFileTestUtilService zipFileTestUtilService;

    @Autowired
    private TeamRepository teamRepository;

    @Autowired
    private GradingCriterionRepository gradingCriterionRepository;

    @Autowired
    private ProgrammingUtilTestService programmingUtilTestService;

    @Autowired
    private ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService;

    private Course course;

    public ProgrammingExercise programmingExercise;

    private ProgrammingExercise programmingExerciseInExam;

    private ProgrammingExerciseStudentParticipation participation1;

    private ProgrammingExerciseStudentParticipation participation2;

    private File downloadedFile;

    private Path localRepoPath;

    private Git localGit;

    private Path remoteRepoPath;

    private Git remoteGit;

    private Path localRepoPath2;

    private Git localGit2;

    private Path remoteRepoPath2;

    private Git remoteGit2;

    private MockDelegate mockDelegate;

    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private VersionControlService versionControlService;

    // this will be a MockitoSpyBean because it was configured as MockitoSpyBean in the super class of the actual test class (see AbstractArtemisIntegrationTest)
    private ContinuousIntegrationService continuousIntegrationService;

    private File plagiarismChecksTestReposDir;

    void setup(String userPrefix, MockDelegate mockDelegate, VersionControlService versionControlService, ContinuousIntegrationService continuousIntegrationService)
            throws Exception {
        this.userPrefix = userPrefix;
        this.mockDelegate = mockDelegate;
        this.versionControlService = versionControlService; // this can be used like a MockitoSpyBean
        this.continuousIntegrationService = continuousIntegrationService; // this can be used like a MockitoSpyBean

        userUtilService.addUsers(userPrefix, 3, 2, 2, 2);
        course = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndTestCases();
        programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(programmingExercise.getId()).orElseThrow();
        programmingExerciseInExam = programmingExerciseUtilService.addCourseExamExerciseGroupWithOneProgrammingExerciseAndTestCases();
        programmingExerciseInExam = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExerciseInExam.getId())
                .orElseThrow();

        participation1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student1");
        participation2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student2");

        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, userPrefix + "student1");
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExerciseInExam, userPrefix + "student2");

        localRepoPath = Files.createTempDirectory("repo");
        localGit = LocalRepository.initialize(localRepoPath, defaultBranch, false);
        remoteRepoPath = Files.createTempDirectory("repoOrigin");
        remoteGit = LocalRepository.initialize(remoteRepoPath, defaultBranch, true);
        StoredConfig config = localGit.getRepository().getConfig();
        config.setString("remote", "origin", "url", remoteRepoPath.toFile().getAbsolutePath());
        config.save();

        localRepoPath2 = Files.createTempDirectory("repo2");
        localGit2 = LocalRepository.initialize(localRepoPath2, defaultBranch, false);
        remoteRepoPath2 = Files.createTempDirectory("repoOrigin");
        remoteGit2 = LocalRepository.initialize(remoteRepoPath2, defaultBranch, true);
        StoredConfig config2 = localGit2.getRepository().getConfig();
        config2.setString("remote", "origin", "url", remoteRepoPath2.toFile().getAbsolutePath());
        config2.save();

        // TODO use createProgrammingExercise or setupTemplateAndPush to create actual content (based on the template repos) in this repository
        // so that e.g. addStudentIdToProjectName in ProgrammingExerciseExportService is tested properly as well

        // the following 2 lines prepare the generation of the structural test oracle
        var testjsonFilePath = localRepoPath.resolve("test").resolve(programmingExercise.getPackageFolderName()).resolve("test.json");
        gitUtilService.writeEmptyJsonFileToPath(testjsonFilePath);
        // create two empty commits
        GitService.commit(localGit).setMessage("empty").setAllowEmpty(true).setSign(false).setAuthor("test", "test@test.com").call();
        localGit.push().call();

        this.plagiarismChecksTestReposDir = Files.createTempDirectory("jplag-repos").toFile();
    }

    void tearDown() throws IOException {
        if (downloadedFile != null && downloadedFile.exists()) {
            FileUtils.forceDelete(downloadedFile);
        }
        if (localGit != null) {
            localGit.close();
        }
        if (localRepoPath != null && localRepoPath.toFile().exists()) {
            FileUtils.deleteDirectory(localRepoPath.toFile());
        }
        if (localGit2 != null) {
            localGit2.close();
        }
        if (localRepoPath2 != null && localRepoPath2.toFile().exists()) {
            FileUtils.deleteDirectory(localRepoPath2.toFile());
        }
        if (remoteGit != null) {
            remoteGit.close();
        }
        if (remoteRepoPath != null && remoteRepoPath.toFile().exists()) {
            FileUtils.deleteDirectory(remoteRepoPath.toFile());
        }
        if (remoteGit2 != null) {
            remoteGit2.close();
        }
        if (remoteRepoPath2 != null && remoteRepoPath2.toFile().exists()) {
            FileUtils.deleteDirectory(remoteRepoPath2.toFile());
        }
        if (plagiarismChecksTestReposDir != null && plagiarismChecksTestReposDir.exists()) {
            FileUtils.deleteDirectory(plagiarismChecksTestReposDir);
        }
    }

    void testProgrammingExerciseIsReleased_IsReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(programmingExercise, userPrefix + "student1");
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        participationUtilService.addResultToSubmission(null, null, submission);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state",
                HttpStatus.OK, ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.released()).isTrue();
        assertThat(releaseStateDTO.hasStudentResult()).isTrue();
        assertThat(releaseStateDTO.testCasesChanged()).isFalse();
    }

    void testProgrammingExerciseIsReleased_IsNotReleasedAndHasResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().plusHours(5L));
        programmingExerciseRepository.save(programmingExercise);
        StudentParticipation participation = participationUtilService.createAndSaveParticipationForExercise(programmingExercise, userPrefix + "student1");
        var submission = participationUtilService.addSubmission(participation, new ProgrammingSubmission());
        participationUtilService.addResultToSubmission(null, null, submission);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state",
                HttpStatus.OK, ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.released()).isFalse();
        assertThat(releaseStateDTO.hasStudentResult()).isTrue();
        assertThat(releaseStateDTO.testCasesChanged()).isFalse();
    }

    void checkIfProgrammingExerciseIsReleased_IsReleasedAndHasNoResults() throws Exception {
        programmingExercise.setReleaseDate(ZonedDateTime.now().minusHours(5L));
        programmingExercise.setTestCasesChanged(true);
        programmingExerciseRepository.save(programmingExercise);

        ProgrammingExerciseTestCaseStateDTO releaseStateDTO = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state",
                HttpStatus.OK, ProgrammingExerciseTestCaseStateDTO.class);
        assertThat(releaseStateDTO.released()).isTrue();
        assertThat(releaseStateDTO.hasStudentResult()).isFalse();
        assertThat(releaseStateDTO.testCasesChanged()).isTrue();
    }

    void testProgrammingExerciseIsReleased_forbidden() throws Exception {
        request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.FORBIDDEN, Boolean.class);
    }

    List<Path> exportSubmissionsWithPracticeSubmissionByParticipationIds(boolean excludePracticeSubmissions) throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath, null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath2, null);
        doReturn(repository1).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation1.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation2.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());

        // Set one of the participations to practice mode
        participation1.setPracticeMode(false);
        participation2.setPracticeMode(true);
        final var participations = List.of(participation1, participation2);
        programmingExerciseStudentParticipationRepository.saveAll(participations);

        // Export with excludePracticeSubmissions
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString()).toList();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + String.join(",", participationIds);
        var exportOptions = new RepositoryExportOptionsDTO(false, false, false, null, excludePracticeSubmissions, false, false, false, false);

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        return unzipExportedFile();
    }

    void testExportSubmissionsByParticipationIds_excludePracticeSubmissions() throws Exception {
        List<Path> entries = exportSubmissionsWithPracticeSubmissionByParticipationIds(true);

        // Make sure that the practice submission is not included
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith(Path.of("student1", ".git").toString()))
                .noneMatch(entry -> entry.toString().matches(".*practice-[^/]*student2.*.git$"));
    }

    void testExportSubmissionsByParticipationIds_includePracticeSubmissions() throws Exception {
        List<Path> entries = exportSubmissionsWithPracticeSubmissionByParticipationIds(false);

        // Make sure that the practice submission is included
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith(Path.of("student1", ".git").toString()))
                .anyMatch(entry -> entry.toString().matches(".*practice-[^/]*student2.*.git$"));
    }

    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectName() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath, null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath2, null);

        doReturn(repository1).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation1.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation2.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());
        doThrow(EmptyCommitException.class).when(gitService).stageAllChanges(any());

        // Create the eclipse .project file which will be modified.
        Path projectFilePath = Path.of(repository1.getLocalPath().toString(), ".project");
        File projectFile = Path.of(projectFilePath.toString()).toFile();
        String projectFileContents = TestResourceUtils.loadFileFromResources("test-data/repository-export/sample.project");
        FileUtils.writeStringToFile(projectFile, projectFileContents, StandardCharsets.UTF_8);

        // Create the maven .pom file
        Path pomPath = Path.of(repository1.getLocalPath().toString(), "pom.xml");
        File pomFile = Path.of(pomPath.toString()).toFile();
        String pomContents = TestResourceUtils.loadFileFromResources("test-data/repository-export/pom.xml");
        FileUtils.writeStringToFile(pomFile, pomContents, StandardCharsets.UTF_8);

        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(programmingExercise.getId(), userPrefix + "student1");
        assertThat(participation).isPresent();

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/"
                + String.join(",", List.of(participation.get().getId().toString()));
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO(false, false, false, null, false, true, false, false, false);

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        // Make sure both repositories are present
        String modifiedEclipseProjectFile = FileUtils.readFileToString(projectFile, StandardCharsets.UTF_8);
        assertThat(modifiedEclipseProjectFile).contains("student1");

        String modifiedPom = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);
        assertThat(modifiedPom).contains("student1");

        Files.deleteIfExists(projectFilePath);
        Files.deleteIfExists(pomPath);
    }

    void testExportSubmissionsByParticipationIds_addParticipantIdentifierToProjectNameError() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath, null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath2, null);

        doReturn(repository1).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation1.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation2.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());

        // Create the eclipse .project file which will be modified.
        Path projectFilePath = Path.of(repository1.getLocalPath().toString(), ".project");
        File projectFile = Path.of(projectFilePath.toString()).toFile();
        if (!projectFile.exists()) {
            Files.createFile(projectFilePath);
        }

        // Create the maven .pom file
        Path pomPath = Path.of(repository1.getLocalPath().toString(), "pom.xml");
        File pomFile = Path.of(pomPath.toString()).toFile();
        if (!pomFile.exists()) {
            Files.createFile(pomPath);
        }

        var participation = programmingExerciseStudentParticipationRepository.findByExerciseIdAndStudentLogin(programmingExercise.getId(), userPrefix + "student1");
        assertThat(participation).isPresent();

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/"
                + String.join(",", List.of(participation.get().getId().toString()));
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO(false, false, false, null, false, true, false, false, false);

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        // Make sure both repositories are present
        String modifiedEclipseProjectFile = FileUtils.readFileToString(projectFile, StandardCharsets.UTF_8);
        assertThat(modifiedEclipseProjectFile).contains("");

        String modifiedPom = FileUtils.readFileToString(pomFile, StandardCharsets.UTF_8);
        assertThat(modifiedPom).contains("");

        Files.deleteIfExists(projectFilePath);
        Files.deleteIfExists(pomPath);
    }

    void testExportSubmissionsByParticipationIds() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath, null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath2, null);
        doReturn(repository1).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation1.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation2.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());

        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString()).toList();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + String.join(",", participationIds);
        // all options false by default, only test if export works at all
        var exportOptions = new RepositoryExportOptionsDTO();

        downloadedFile = request.postWithResponseBodyFile(path, exportOptions, HttpStatus.OK);
        assertThat(downloadedFile).exists();

        List<Path> entries = unzipExportedFile();

        // Make sure both repositories are present
        assertThat(entries).anyMatch(entry -> entry.toString().endsWith(Path.of("student1", ".git").toString()))
                .anyMatch(entry -> entry.toString().endsWith(Path.of("student2", ".git").toString()));
    }

    void testExportSubmissionAnonymizationCombining() throws Exception {
        // provide repositories
        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath, null);
        doReturn(repository).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation1.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());

        // Mock and pretend first commit is template commit
        ObjectId head = localGit.getRepository().findRef("HEAD").getObjectId();
        when(gitService.getLastCommitHash(any())).thenReturn(head);
        doNothing().when(gitService).resetToOriginHead(any());

        // Add commit to anonymize
        assertThat(localRepoPath.resolve("Test.java").toFile().createNewFile()).isTrue();
        localGit.add().addFilepattern(".").call();
        GitService.commit(localGit).setMessage("commit").setAuthor("user1", "email1").call();

        // Rest call
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + participation1.getId();
        downloadedFile = request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
        assertThat(downloadedFile).exists();

        List<Path> entries = unzipExportedFile();

        // Checks
        assertThat(entries).anyMatch(entry -> entry.endsWith("Test.java"));
        Optional<Path> extractedRepo1 = entries.stream()
                .filter(entry -> entry.toString().endsWith(Path.of("-" + participation1.getId() + "-student-submission.git", ".git").toString())).findFirst();
        assertThat(extractedRepo1).isPresent();
        try (Git downloadedGit = Git.open(extractedRepo1.get().toFile())) {
            RevCommit commit = downloadedGit.log().setMaxCount(1).call().iterator().next();
            assertThat(commit.getAuthorIdent().getName()).isEqualTo("student");
            assertThat(commit.getFullMessage()).isEqualTo("All student changes in one commit");
        }
    }

    /**
     * Recursively unzips the exported file.
     *
     * @return the list of files that the {@code downloadedFile} contained.
     */
    private List<Path> unzipExportedFile() throws Exception {
        Path extractedZipDir = zipFileTestUtilService.extractZipFileRecursively(downloadedFile.getAbsolutePath());
        try (var files = Files.walk(extractedZipDir)) {
            return files.toList();
        }
    }

    void testExportSubmissionsByParticipationIds_invalidParticipationId_badRequest() throws Exception {
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/10";
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.BAD_REQUEST);
    }

    void testExportSubmissionsByParticipationIds_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        var participationIds = programmingExerciseStudentParticipationRepository.findAll().stream().map(participation -> participation.getId().toString()).toList();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participation-ids/" + String.join(",", participationIds);
        request.postWithResponseBodyFile(path, getOptions(), HttpStatus.FORBIDDEN);
    }

    void testExportSubmissionsByStudentLogins() throws Exception {
        File downloadedFile = exportSubmissionsByStudentLogins();
        assertThat(downloadedFile).exists();
        // TODO: unzip the files and add some checks
    }

    private File exportSubmissionsByStudentLogins() throws Exception {
        var repository1 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath, null);
        var repository2 = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath2, null);
        doReturn(repository1).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation1.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());
        doReturn(repository2).when(gitService).getOrCheckoutRepositoryWithTargetPath(eq(participation2.getVcsRepositoryUri()), any(Path.class), anyBoolean(), anyBoolean());
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participant-identifiers/" + userPrefix + "student1,"
                + userPrefix + "student2";
        return request.postWithResponseBodyFile(path, getOptions(), HttpStatus.OK);
    }

    private RepositoryExportOptionsDTO getOptions() {
        return new RepositoryExportOptionsDTO(false, true, false, null, false, true, true, true, true);
    }

    void testProgrammingExerciseDelete() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);
        request.delete(path, HttpStatus.OK, params);
    }

    void testProgrammingExerciseDelete_failToDeleteBuildPlan() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), true);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_buildPlanDoesntExist() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.OK, params);
    }

    void testProgrammingExerciseDelete_failToDeleteCiProject() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, true);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_failToDeleteVcsProject() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName(), RepositoryType.TESTS.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_failToDeleteVcsRepositories() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var params = new LinkedMultiValueMap<String, String>();
        params.add("deleteStudentReposBuildPlans", "true");
        params.add("deleteBaseReposBuildPlans", "true");

        for (final var planName : List.of("student1", "student2", TEMPLATE.getName(), SOLUTION.getName())) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }
        mockDelegate.mockDeleteBuildPlanProject(projectKey, false);

        request.delete(path, HttpStatus.INTERNAL_SERVER_ERROR, params);
    }

    void testProgrammingExerciseDelete_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        request.delete(path, HttpStatus.NOT_FOUND);
    }

    void testProgrammingExerciseDelete_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        request.delete(path, HttpStatus.FORBIDDEN);
    }

    void testGetProgrammingExercise() throws Exception {
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        // TODO add more assertions
    }

    void testGetProgrammingExerciseWithStructuredGradingInstruction() throws Exception {
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());

        exerciseUtilService.addGradingInstructionsToExercise(programmingExerciseServer);

        GradingCriterion criterionWithoutTitle = GradingCriterionUtil.findGradingCriterionByTitle(programmingExerciseServer, null);
        GradingCriterion criterionWithTitle = GradingCriterionUtil.findGradingCriterionByTitle(programmingExerciseServer, "test title");

        assertThat(criterionWithTitle.getStructuredGradingInstructions()).hasSize(3);
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions()).hasSize(1);
        final String expectedDescription = "created first instruction with empty criteria for testing";
        assertThat(criterionWithoutTitle.getStructuredGradingInstructions().stream().filter(instruction -> expectedDescription.equals(instruction.getInstructionDescription()))
                .findAny()).isPresent();
    }

    void testGetProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId();
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGetProgrammingExerciseWithSetupParticipations() throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "instructor1");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-participations";
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        checkTemplateAndSolutionParticipationsFromServer(programmingExerciseServer);
        assertThat(programmingExerciseServer.getStudentParticipations()).isNotEmpty();
        // TODO add more assertions
    }

    void testGetProgrammingExerciseWithJustTemplateAndSolutionParticipation(boolean withSubmissionResults) throws Exception {
        participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "tutor1");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-template-and-solution-participation?withSubmissionResults="
                + withSubmissionResults;
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);
        checkTemplateAndSolutionParticipationsFromServer(programmingExerciseServer);
        assertThat(programmingExerciseServer.getStudentParticipations()).isEmpty();
    }

    void testGetProgrammingExerciseWithTemplateAndSolutionParticipationAndAuxiliaryRepositories(boolean withSubmissionResults) throws Exception {
        AuxiliaryRepository auxiliaryRepository = programmingExerciseUtilService.addAuxiliaryRepositoryToExercise(programmingExercise);
        Set<GradingCriterion> gradingCriteria = exerciseUtilService.addGradingInstructionsToExercise(programmingExercise);
        gradingCriterionRepository.saveAll(gradingCriteria);
        programmingExercise = programmingExerciseRepository.save(programmingExercise);

        var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-template-and-solution-participation" + "?withSubmissionResults="
                + withSubmissionResults + "&withGradingCriteria=" + false;
        var programmingExerciseServer = request.get(path, HttpStatus.OK, ProgrammingExercise.class);

        checkTemplateAndSolutionParticipationsFromServer(programmingExerciseServer);
        assertThat(programmingExerciseServer.getAuxiliaryRepositories()).hasSize(1).containsExactly(auxiliaryRepository);
        assertThat(programmingExerciseServer.getGradingCriteria()).isEmpty();
    }

    private void checkTemplateAndSolutionParticipationsFromServer(ProgrammingExercise programmingExerciseServer) {
        assertThat(programmingExerciseServer.getTitle()).isEqualTo(programmingExercise.getTitle());
        assertThat(programmingExerciseServer.getTemplateParticipation()).isNotNull().extracting(DomainObject::getId).isNotNull();
        assertThat(programmingExerciseServer.getSolutionParticipation()).isNotNull().extracting(DomainObject::getId).isNotNull();
    }

    void testGetProgrammingExerciseWithSetupParticipations_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-participations";
        request.get(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGetProgrammingExerciseWithSetupParticipations_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/with-participations";
        request.get(path, HttpStatus.NOT_FOUND, ProgrammingExercise.class);
    }

    void testGetProgrammingExercisesForCourse() throws Exception {
        final var path = "/api/programming/courses/" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/programming-exercises";
        var programmingExercisesServer = request.getList(path, HttpStatus.OK, ProgrammingExercise.class);
        assertThat(programmingExercisesServer).isNotEmpty();
        // TODO add more assertions
    }

    void testGetProgrammingExercisesForCourse_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var path = "/api/programming/courses/" + programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId() + "/programming-exercises";
        request.getList(path, HttpStatus.FORBIDDEN, ProgrammingExercise.class);
    }

    void testGenerateStructureOracle() throws Exception {
        var repository = gitService.getExistingCheckedOutRepositoryByLocalPath(localRepoPath, null);
        doReturn(repository).when(gitService).getOrCheckoutRepositoryWithTargetPath(any(VcsRepositoryUri.class), any(Path.class), anyBoolean(), anyBoolean());
        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests";
        var result = request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.OK);
        assertThat(result).startsWith("Successfully generated the structure oracle");
        request.putWithResponseBody(path, programmingExercise, String.class, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_invalidTemplateBuildPlan_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), false, false);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_BUILD_PLAN_ID);
    }

    void updateProgrammingExercise_idIsNull_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExercise.setId(null);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        // both values are not set --> bad request
        programmingExercise.setCourse(null);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
        // both values are set --> bad request
        programmingExerciseInExam.setCourse(course);
        request.put("/api/programming/programming-exercises", programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_correctlySavesTestIds() throws Exception {
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        var test1 = tests.getFirst();

        String problemStatement = "[task][taskname](test1)";
        String problemStatementWithId = "[task][taskname](<testid>%s</testid>)".formatted(test1.getId());
        programmingExercise.setProblemStatement(problemStatement);

        mockBuildPlanAndRepositoryCheck(programmingExercise);

        var response = request.putWithResponseBody("/api/programming/programming-exercises", programmingExercise, ProgrammingExercise.class, HttpStatus.OK);
        assertThat(response.getProblemStatement()).as("the REST endpoint should return a problem statement with test names").isEqualTo(problemStatement);

        programmingExercise = programmingExerciseRepository.findByIdElseThrow(programmingExercise.getId());
        assertThat(programmingExercise.getProblemStatement()).as("test saved exercise contains test ids").isEqualTo(problemStatementWithId);
    }

    private void mockBuildPlanAndRepositoryCheck(ProgrammingExercise programmingExercise) throws Exception {
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
    }

    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_falseToTrue_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_staticCodeAnalysisMustNotChange_trueToFalse_badRequest() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void updateProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void updateProgrammingExercise_invalidTemplateVcs_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_REPOSITORY_URL);
    }

    void updateProgrammingExercise_invalidSolutionBuildPlan_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), false, false);

        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_BUILD_PLAN_ID);
    }

    void updateProgrammingExercise_invalidSolutionRepository_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        programmingExerciseParticipationUtilService.addSolutionParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getSolutionBuildPlanId(), true, false);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_SOLUTION_REPOSITORY_URL);
    }

    void updateProgrammingExercise_checkIfBuildPlanExistsFails_badRequest() throws Exception {
        programmingExerciseParticipationUtilService.addTemplateParticipationForProgrammingExercise(programmingExercise);
        mockDelegate.mockCheckIfBuildPlanExists(programmingExercise.getProjectKey(), programmingExercise.getTemplateBuildPlanId(), true, true);
        request.putAndExpectError("/api/programming/programming-exercises", programmingExercise, HttpStatus.BAD_REQUEST, INVALID_TEMPLATE_BUILD_PLAN_ID);
    }

    /**
     * This test checks that it is not allowed to change the courseId of an exercise
     * in an update request. The request should fail with 'HttpStatus.CONFLICT'.
     */
    void updateProgrammingExerciseShouldFailWithConflictWhenUpdatingCourseId() throws Exception {
        // Create a programming exercise.
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        // Create a new course with different id.
        Long oldCourseId = programmingExercise.getCourseViaExerciseGroupOrCourseMember().getId();
        Long newCourseId = oldCourseId + 1;
        Course newCourse = courseUtilService.createCourse();

        // Assign new course to the programming exercise.
        ProgrammingExercise newProgrammingExercise = programmingExercise;
        newProgrammingExercise.setCourse(newCourse);

        // Programming exercise update with the new course should fail.
        request.put("/api/programming/programming-exercises", newProgrammingExercise, HttpStatus.CONFLICT);
    }

    /**
     * This test checks that it is not allowed to change SCA enabled option
     */
    void updateProgrammingExerciseShouldFailWithBadRequestWhenUpdatingSCAOption() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        ProgrammingExercise updatedExercise = programmingExercise;
        updatedExercise.setStaticCodeAnalysisEnabled(true);

        request.put("/api/programming/programming-exercises", updatedExercise, HttpStatus.BAD_REQUEST);
    }

    void updateExerciseDueDateWithIndividualDueDateUpdate() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        final ZonedDateTime individualDueDate = ZonedDateTime.now().plusHours(20);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            participations.getFirst().setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(individualDueDate);
            programmingExerciseStudentParticipationRepository.saveAll(participations);
        }

        programmingExercise.setDueDate(ZonedDateTime.now().plusHours(12));
        assertThat(programmingExercise.getDueDate()).isNotNull();
        programmingExercise.setReleaseDate(programmingExercise.getDueDate().minusDays(1));
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.OK);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(1);

            final var withIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() != null).toList();
            assertThat(withIndividualDueDate).hasSize(1);
            assertThat(withIndividualDueDate.getFirst().getIndividualDueDate()).isCloseTo(individualDueDate, HalfSecond());
        }
    }

    void updateExerciseRemoveDueDate() throws Exception {
        mockBuildPlanAndRepositoryCheck(programmingExercise);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            assertThat(participations).hasSize(2);
            participations.getFirst().setIndividualDueDate(ZonedDateTime.now().plusHours(2));
            participations.get(1).setIndividualDueDate(ZonedDateTime.now().plusHours(20));
            programmingExerciseStudentParticipationRepository.saveAll(participations);
        }

        programmingExercise.setDueDate(null);
        programmingExercise.setAssessmentDueDate(null);
        request.put("/api/programming/programming-exercises", programmingExercise, HttpStatus.OK);

        {
            final var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
            final var withNoIndividualDueDate = participations.stream().filter(participation -> participation.getIndividualDueDate() == null).toList();
            assertThat(withNoIndividualDueDate).hasSize(2);
        }
    }

    void updateTimeline_intructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        final var endpoint = "/api/programming/programming-exercises/timeline";
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.FORBIDDEN, params);
    }

    void updateTimeline_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var endpoint = "/api/programming/programming-exercises/timeline";
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.NOT_FOUND, params);
    }

    void updateTimeline_ok() throws Exception {
        final var endpoint = "/api/programming/programming-exercises/timeline";
        MultiValueMap<String, String> params = new HttpHeaders();
        params.add("notificationText", "The notification text");
        request.putWithResponseBodyAndParams(endpoint, programmingExercise, ProgrammingExercise.class, HttpStatus.OK, params);
    }

    void updateProblemStatement_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.FORBIDDEN, MediaType.TEXT_PLAIN);
    }

    void updateProblemStatement_invalidId_notFound() throws Exception {
        programmingExercise.setId(getMaxProgrammingExerciseId() + 1);
        final var endpoint = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/problem-statement";
        request.patchWithResponseBody(endpoint, "a new problem statement", ProgrammingExercise.class, HttpStatus.NOT_FOUND, MediaType.TEXT_PLAIN);
    }

    void createProgrammingExercise_exerciseIsNull_badRequest() throws Exception {
        request.post("/api/programming/programming-exercises/setup", null, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_idIsNotNull_badRequest() throws Exception {
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post("/api/programming/programming-exercises/setup", programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        programmingExercise.setId(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void createProgrammingExercise_titleNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_titleContainsBadCharacter_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("abc?=§ ``+##");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExercise.setShortName("hi");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidCourseShortName_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        course.setShortName(null);
        courseRepository.save(course);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
        course.setShortName("Hi");
        courseRepository.save(course);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        programmingExerciseInExam.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setId(null);
        request.post("/api/programming/programming-exercises/setup", programmingExerciseInExam, HttpStatus.BAD_REQUEST);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_shortNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("asdb ³¼²½¼³`` ");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noProgrammingLanguageSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        programmingExercise.setProgrammingLanguage(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameContainsBadCharacters_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("..asd. ß?");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameContainsKeyword_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("abc.final.xyz");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameElementBeginsWithDigit_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName("eist.2020something");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_packageNameIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setPackageName(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_maxScoreIsNull_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_staticCodeAnalysisAndSequential_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.getBuildConfig().setSequentialTestRuns(true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_unsupportedProgrammingLanguageForStaticCodeAnalysis_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.programmingLanguage(ProgrammingLanguage.C);
        programmingExercise.setProjectType(ProjectType.FACT);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_noStaticCodeAnalysisButMaxPenalty_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(false);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(20);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_maxStaticCodePenaltyNegative_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        programmingExercise.setMaxStaticCodeAnalysisPenalty(-20);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("testTitle");
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_failToCheckIfProjectExistsInCi() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("unique-title");
        programmingExercise.setShortName("testuniqueshortname");
        mockDelegate.mockCheckIfProjectExistsInCi(programmingExercise, true, true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_projectTypeMissing_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.JAVA);
        programmingExercise.setProjectType(null);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_projectTypeNotExpected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.PYTHON);
        programmingExercise.setProjectType(ProjectType.MAVEN_MAVEN);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_onlineCodeEditorNotExpected_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(ProgrammingLanguage.SWIFT);
        programmingExercise.setProjectType(ProjectType.XCODE);
        programmingExercise.setAllowOnlineEditor(true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_checkoutSolutionRepositoryProgrammingLanguageNotSupported_badRequest(ProgrammingLanguage programmingLanguage) throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setTitle("New title");
        programmingExercise.setShortName("NewShortname");
        programmingExercise.setProgrammingLanguage(programmingLanguage);
        programmingExercise.getBuildConfig().setCheckoutSolutionRepository(true);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_invalidMaxScore_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(0.0);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_includedAsBonus_invalidBonusPoints_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(1.0);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.INCLUDED_AS_BONUS);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void createProgrammingExercise_notIncluded_invalidBonusPoints_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        programmingExercise.setId(null);
        programmingExercise.setMaxPoints(10.0);
        programmingExercise.setBonusPoints(1.0);
        programmingExercise.setIncludedInOverallScore(IncludedInOverallScore.NOT_INCLUDED);
        request.post("/api/programming/programming-exercises/setup", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_sourceExerciseIdNegative_badRequest() throws Exception {
        programmingExercise.setId(-1L);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExerciseMaxScoreNullBadRequest() throws Exception {
        programmingExercise.setMaxPoints(null);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_noParticipationModeSelected_badRequest() throws Exception {
        programmingExercise.setAllowOfflineIde(false);
        programmingExercise.setAllowOnlineEditor(false);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_noProgrammingLanguage_badRequest() throws Exception {
        programmingExercise.setProgrammingLanguage(null);
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_instructorNotInCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        request.post("/api/programming/programming-exercises/import/" + programmingExercise.getId(), programmingExercise, HttpStatus.FORBIDDEN);
    }

    void importProgrammingExercise_templateIdDoesNotExist_notFound() throws Exception {
        programmingExercise.setShortName("newShortName");
        programmingExercise.setTitle("newTitle");
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.NOT_FOUND);
    }

    void importProgrammingExercise_sameShortNameInCourse_badRequest() throws Exception {
        String sourceId = programmingExercise.getId().toString();
        programmingExercise.setId(null);
        programmingExercise.setTitle(programmingExercise.getTitle() + "change");
        String sourceIdExam = programmingExerciseInExam.getId().toString();
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setTitle(programmingExerciseInExam.getTitle() + "change");
        // short name will still be the same
        request.post("/api/programming/programming-exercises/import/" + sourceId, programmingExercise, HttpStatus.BAD_REQUEST);
        request.post("/api/programming/programming-exercises/import/" + sourceIdExam, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_sameTitleInCourse_badRequest() throws Exception {
        String sourceId = programmingExercise.getId().toString();
        programmingExercise.setId(null);
        programmingExercise.setShortName(programmingExercise.getShortName() + "change");
        String sourceIdExam = programmingExerciseInExam.getId().toString();
        programmingExerciseInExam.setId(null);
        programmingExerciseInExam.setShortName(programmingExerciseInExam.getShortName() + "change");
        // title will still be the same
        request.post("/api/programming/programming-exercises/import/" + sourceId, programmingExercise, HttpStatus.BAD_REQUEST);
        request.post("/api/programming/programming-exercises/import/" + sourceIdExam, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_staticCodeAnalysisMustBeSet_badRequest() throws Exception {
        var id = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setStaticCodeAnalysisEnabled(null);
        request.post("/api/programming/programming-exercises/import/" + id, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_scaChanged_badRequest(boolean recreateBuildPlan, boolean updateTemplate) throws Exception {
        var params = new LinkedMultiValueMap<String, String>();
        params.add("recreateBuildPlans", String.valueOf(recreateBuildPlan));
        params.add("updateTemplate", String.valueOf(updateTemplate));
        var programmingExerciseSca = programmingExerciseUtilService.addCourseWithOneProgrammingExerciseAndStaticCodeAnalysisCategories();

        setupMocksForConsistencyChecksOnImport(programmingExercise);
        setupMocksForConsistencyChecksOnImport(programmingExerciseSca);

        // false -> true
        var sourceId = programmingExercise.getId();
        programmingExercise.setId(null);
        programmingExercise.setTitle("NewTitle1");
        programmingExercise.setShortName("NewShortname1");
        programmingExercise.setStaticCodeAnalysisEnabled(true);
        request.postWithResponseBody("/api/programming/programming-exercises/import/" + sourceId, programmingExercise, ProgrammingExercise.class, params, HttpStatus.BAD_REQUEST);

        // true -> false
        sourceId = programmingExerciseSca.getId();
        programmingExerciseSca.setId(null);
        programmingExerciseSca.setStaticCodeAnalysisEnabled(false);
        programmingExerciseSca.setMaxStaticCodeAnalysisPenalty(null);
        programmingExerciseSca.setTitle("NewTitle2");
        programmingExerciseSca.setShortName("NewShortname2");
        request.postWithResponseBody("/api/programming/programming-exercises/import/" + sourceId, programmingExerciseSca, ProgrammingExercise.class, params,
                HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_eitherCourseOrExerciseGroupSet_badRequest() throws Exception {
        programmingExercise.setCourse(null);
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.BAD_REQUEST);
        programmingExerciseInExam.setCourse(programmingExercise.getCourseViaExerciseGroupOrCourseMember());
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExerciseInExam, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_vcsProjectWithSameKeyAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_vcsProjectWithSameTitleAlreadyExists_badRequest() throws Exception {
        programmingExercise.setId(null);
        programmingExercise.setShortName("testShortName");
        request.post("/api/programming/programming-exercises/import/" + NON_EXISTING_ID, programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void importProgrammingExercise_updatesTestCaseIds() throws Exception {
        // TODO: we should not mock this and instead use the real urls for LocalVC
        doReturn(new LocalVCRepositoryUri(remoteRepoPath.toString())).when(versionControlService).getCloneRepositoryUri(anyString(), anyString());

        programmingExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesElseThrow(programmingExercise.getId());
        var tests = programmingExerciseUtilService.addTestCasesToProgrammingExercise(programmingExercise);
        var test1 = tests.getFirst();

        String problemStatementWithId = "[task][Taskname](<testid>" + test1.getId() + "</testid>)";
        String problemStatement = "[task][Taskname](test1)";
        programmingExercise.setProblemStatement(problemStatementWithId);
        programmingExerciseRepository.save(programmingExercise);
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndBuildConfigById(programmingExercise.getId()).orElseThrow();

        String sourceId = programmingExercise.getId().toString();

        ProgrammingExercise exerciseToBeImported = ProgrammingExerciseFactory.generateToBeImportedProgrammingExercise("ImportTitle", "imported", programmingExercise, course);
        exerciseToBeImported.setProblemStatement(problemStatement);

        mockDelegate.mockConnectorRequestsForImport(programmingExercise, exerciseToBeImported, false, false);
        mockDelegate.mockConnectorRequestsForSetup(exerciseToBeImported, false, false, false);
        mockBuildPlanAndRepositoryCheck(programmingExercise);
        doNothing().when(continuousIntegrationService).updatePlanRepository(any(), any(), any(), any(), any(), any(), any());

        var response = request.postWithResponseBody("/api/programming/programming-exercises/import/" + sourceId, exerciseToBeImported, ProgrammingExercise.class, HttpStatus.OK);

        assertThat(response.getProblemStatement()).isEqualTo("[task][Taskname](test1)");

        var newTestCase = programmingExerciseTestCaseRepository.findByExerciseIdAndTestName(response.getId(), test1.getTestName()).orElseThrow();
        String newProblemStatement = "[task][Taskname](<testid>" + newTestCase.getId() + "</testid>)";
        var savedProgrammingExercise = programmingExerciseRepository.findByIdElseThrow(response.getId());

        assertThat(savedProgrammingExercise.getProblemStatement()).isEqualTo(newProblemStatement);

        reset(versionControlService);
    }

    void exportSubmissionsByStudentLogins_notInstructorForExercise_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt1");
        request.post(getDefaultAPIEndpointForExportRepos(), getOptions(), HttpStatus.FORBIDDEN);
    }

    @NotNull
    private String getDefaultAPIEndpointForExportRepos() {
        return "/api/programming/programming-exercises/" + programmingExercise.getId() + "/export-repos-by-participant-identifiers/1,2,3";
    }

    void exportSubmissionsByStudentLogins_exportAllAsTutor_forbidden() throws Exception {
        final var options = new RepositoryExportOptionsDTO(true, true, false, null, false, true, true, true, true);
        request.post(getDefaultAPIEndpointForExportRepos(), options, HttpStatus.FORBIDDEN);
    }

    void generateStructureOracleForExercise_exerciseDoesNotExist_badRequest() throws Exception {
        request.put("/api/programming/programming-exercises/" + (programmingExercise.getId() + 8337) + "/generate-tests", programmingExercise, HttpStatus.NOT_FOUND);
    }

    void generateStructureOracleForExercise_userIsNotAdminInCourse_badRequest() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "instructoralt");
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void generateStructureOracleForExercise_invalidPackageName_badRequest() throws Exception {
        programmingExercise.setPackageName(null);
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests", programmingExercise, HttpStatus.BAD_REQUEST);

        programmingExercise.setPackageName("ab");
        programmingExerciseRepository.saveAndFlush(programmingExercise);
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/generate-tests", programmingExercise, HttpStatus.BAD_REQUEST);
    }

    void hasAtLeastOneStudentResult_exerciseDoesNotExist_notFound() throws Exception {
        request.get("/api/programming/programming-exercises/" + (programmingExercise.getId() + 1337) + "/test-case-state", HttpStatus.NOT_FOUND, String.class);
    }

    void hasAtLeastOneStudentResult_isNotTeachingAssistant_forbidden() throws Exception {
        userUtilService.addTeachingAssistant("other-tutors", userPrefix + "tutoralt1");
        request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/test-case-state", HttpStatus.FORBIDDEN, String.class);
    }

    void getTestCases_asTutor() throws Exception {
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases";
        final List<ProgrammingExerciseTestCase> returnedTests = request.getList("/api" + endpoint, HttpStatus.OK, ProgrammingExerciseTestCase.class);
        final List<ProgrammingExerciseTestCase> testsInDB = new ArrayList<>(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        returnedTests.forEach(testCase -> testCase.setExercise(programmingExercise));
        assertThat(returnedTests).containsExactlyInAnyOrderElementsOf(testsInDB);
    }

    void getTestCases_asStudent_forbidden() throws Exception {
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases";
        request.getList("/api" + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    void getTestCases_tutorInOtherCourse_forbidden() throws Exception {
        userUtilService.addTeachingAssistant("other-teaching-assistants", userPrefix + "other-teaching-assistant1");
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases";

        request.getList("/api" + endpoint, HttpStatus.FORBIDDEN, ProgrammingExerciseTestCase.class);
    }

    void updateTestCases_asInstrutor() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), testCase.getId() + 42.0, testCase.getId() + 1.0,
                testCase.getId() + 2.0, Visibility.AFTER_DUE_DATE)).toList();
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testCasesInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(new HashSet<>(testCasesResponse)).usingRecursiveFieldByFieldElementComparatorIgnoringFields("exercise", "tasks")
                .containsExactlyInAnyOrderElementsOf(testCasesInDB);
        assertThat(testCasesResponse).allSatisfy(testCase -> {
            assertThat(testCase.isAfterDueDate()).isTrue();
            assertThat(testCase.getWeight()).isEqualTo(testCase.getId() + 42);
            assertThat(testCase.getBonusMultiplier()).isEqualTo(testCase.getId() + 1.0);
            assertThat(testCase.getBonusPoints()).isEqualTo(testCase.getId() + 2.0);
        });
    }

    void updateTestCases_asInstrutor_triggerBuildFails() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();
        mockDelegate.mockTriggerBuildFailed(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuildFailed(programmingExercise.getTemplateParticipation());

        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream().map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), testCase.getId() + 42.0, testCase.getId() + 1.0,
                testCase.getId() + 2.0, Visibility.AFTER_DUE_DATE)).toList();
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);

        assertThat(testCasesResponse).isNotNull();
    }

    void updateTestCases_nonExistingExercise_notFound() throws Exception {
        final var update = new ProgrammingExerciseTestCaseDTO(null, null, null, null, null);
        final var endpoint = "/programming/programming-exercises/" + (programmingExercise.getId() + 1337) + "/update-test-cases";
        request.patchWithResponseBody("/api" + endpoint, List.of(update), String.class, HttpStatus.NOT_FOUND);
    }

    void updateTestCases_instructorInWrongCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "other-instructor1");
        final var update = new ProgrammingExerciseTestCaseDTO(null, null, null, null, null);
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        request.patchWithResponseBody("/api" + endpoint, List.of(update), String.class, HttpStatus.FORBIDDEN);
    }

    void updateTestCases_testCaseWeightSmallerThanZero_badRequest() throws Exception {
        final var testCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
        final var updates = testCases.stream()
                .map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), -1., testCase.getId() + 1.0, testCase.getId() + 2.0, Visibility.AFTER_DUE_DATE)).toList();
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        request.patchWithResponseBody("/api" + endpoint, updates, String.class, HttpStatus.BAD_REQUEST);
    }

    void updateTestCases_testCaseMultiplierSmallerThanZero_badRequest() throws Exception {
        final var testCases = List.copyOf(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        final var updates = transformTestCasesToDto(testCases);
        var firstUpdate = updates.getFirst();
        firstUpdate = new ProgrammingExerciseTestCaseDTO(firstUpdate.id(), firstUpdate.weight(), -1.0, firstUpdate.bonusPoints(), firstUpdate.visibility());
        updates.set(0, firstUpdate);
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        request.performMvcRequest(
                MockMvcRequestBuilders.patch(new URI("/api" + endpoint)).contentType(MediaType.APPLICATION_JSON).content(request.getObjectMapper().writeValueAsString(updates)))
                .andExpect(status().isBadRequest()) //
                .andExpect(jsonPath("$.errorKey").value("settingNegative")) //
                .andExpect(jsonPath("$.testCase").value(testCases.getFirst().getTestName()));
    }

    /**
     * Setting the bonus points to {@code null} is okay, as {@link ProgrammingExerciseTestCase#getBonusPoints()} will replace that with 0.
     */
    void updateTestCases_testCaseBonusPointsNull() throws Exception {
        {
            final var originalTestCases = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());
            originalTestCases.forEach(testCase -> testCase.setBonusPoints(1d));
            programmingExerciseTestCaseRepository.saveAll(originalTestCases);
        }

        final var testCases = List.copyOf(programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()));
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());

        final var updates = transformTestCasesToDto(testCases);
        var firstUpdate = updates.getFirst();
        firstUpdate = new ProgrammingExerciseTestCaseDTO(firstUpdate.id(), firstUpdate.weight(), firstUpdate.bonusMultiplier(), null, firstUpdate.visibility());
        updates.set(0, firstUpdate);

        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/update-test-cases";

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, updates, new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        final var updatedTestCase = testCasesResponse.stream().filter(testCase -> testCase.getId().equals(updates.getFirst().id())).findFirst().orElseThrow();
        assertThat(updatedTestCase.getBonusPoints()).isZero();
        assertThat(testCasesResponse.stream().filter(testCase -> !testCase.getId().equals(updatedTestCase.getId()))).allMatch(testCase -> testCase.getBonusPoints() == 1d);
    }

    private static List<ProgrammingExerciseTestCaseDTO> transformTestCasesToDto(Collection<ProgrammingExerciseTestCase> testCases) {
        return testCases.stream().map(testCase -> new ProgrammingExerciseTestCaseDTO(testCase.getId(), testCase.getWeight(), testCase.getBonusMultiplier(),
                testCase.getBonusPoints(), testCase.getVisibility())).collect(Collectors.toCollection(ArrayList::new));
    }

    void resetTestCaseWeights_asInstructor() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationTeamAssignmentConfigCategoriesById(programmingExercise.getId()).orElseThrow();
        mockDelegate.mockTriggerBuild(programmingExercise.getSolutionParticipation());
        mockDelegate.mockTriggerBuild(programmingExercise.getTemplateParticipation());
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases/reset";
        programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId()).forEach(test -> {
            test.setWeight(42.0);
            programmingExerciseTestCaseRepository.saveAndFlush(test);
        });

        final var testCasesResponse = request.patchWithResponseBody("/api" + endpoint, "{}", new TypeReference<List<ProgrammingExerciseTestCase>>() {
        }, HttpStatus.OK);
        // Otherwise the HashSet for comparison can't be created because exercise id is used for the hashCode
        testCasesResponse.forEach(testCase -> testCase.setExercise(programmingExercise));
        final var testsInDB = programmingExerciseTestCaseRepository.findByExerciseId(programmingExercise.getId());

        assertThat(testCasesResponse).containsExactlyInAnyOrderElementsOf(testsInDB);
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getWeight()).isEqualTo(1));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusMultiplier()).isEqualTo(1.0));
        assertThat(testsInDB).allSatisfy(test -> assertThat(test.getBonusPoints()).isZero());
    }

    void resetTestCaseWeights_instructorInWrongCourse_forbidden() throws Exception {
        userUtilService.addInstructor("other-instructors", userPrefix + "other-instructor1");
        final var endpoint = "/programming/programming-exercises/" + programmingExercise.getId() + "/test-cases/reset";
        request.patchWithResponseBody("/api" + endpoint, "{}", String.class, HttpStatus.FORBIDDEN);
    }

    void testCheckPlagiarism() throws Exception {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationById(ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId()).orElseThrow();
        prepareTwoStudentAndOneInstructorRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, PlagiarismResultDTO.class, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertPlagiarismResult(programmingExercise, result, 100.0);
    }

    void testCheckPlagiarismForTeamExercise() throws Exception {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();

        var programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationById(ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId()).orElseThrow();
        programmingExercise.setMode(ExerciseMode.TEAM);
        programmingExerciseRepository.save(programmingExercise);

        prepareTwoTeamRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/check-plagiarism";
        var result = request.get(path, HttpStatus.OK, PlagiarismResultDTO.class, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertPlagiarismResult(programmingExercise, result, 100.0);
    }

    void testCheckPlagiarismJplagReport() throws Exception {
        var course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        var programmingExercise = programmingExerciseRepository
                .findWithTemplateAndSolutionParticipationById(ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class).getId()).orElseThrow();
        prepareTwoStudentAndOneInstructorRepositoriesForPlagiarismChecks(programmingExercise);

        final var path = "/api/programming/programming-exercises/" + programmingExercise.getId() + "/check-plagiarism-jplag-report";
        var jplagZipArchive = request.getFile(path, HttpStatus.OK, plagiarismUtilService.getDefaultPlagiarismOptions());
        assertThat(jplagZipArchive).isNotNull();
        assertThat(jplagZipArchive).exists();

        try (ZipFile zipFile = new ZipFile(jplagZipArchive)) {
            assertThat(zipFile.getEntry("overview.json")).isNotNull();
            assertThat(zipFile.getEntry("files/1-Submission1.java/1-Submission1.java")).isNotNull();
            assertThat(zipFile.getEntry("files/2-Submission2.java/2-Submission2.java")).isNotNull();

            // it is random which of the following two exists, but one of them must be part of the zip file
            var json1 = zipFile.getEntry("1-Submission1.java-2-Submission2.java.json");
            var json2 = zipFile.getEntry("2-Submission2.java-1-Submission1.java.json");
            assertThat(json1 != null || json2 != null).isTrue();
        }
    }

    private void assertPlagiarismResult(ProgrammingExercise programmingExercise, PlagiarismResultDTO result, double expectedSimilarity) {
        // verify plagiarism result
        assertThat(result.plagiarismResult().getComparisons()).hasSize(1);
        assertThat(result.plagiarismResult().getExercise().getId()).isEqualTo(programmingExercise.getId());

        PlagiarismComparison comparison = result.plagiarismResult().getComparisons().iterator().next();
        assertThat(comparison.getSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.0001));
        assertThat(comparison.getStatus()).isEqualTo(PlagiarismStatus.NONE);
        assertThat(comparison.getMatches()).hasSize(1);

        // verify plagiarism result stats
        var stats = result.plagiarismResultStats();
        assertThat(stats.numberOfDetectedSubmissions()).isEqualTo(2);
        assertThat(stats.averageSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.0001));
        assertThat(stats.maximalSimilarity()).isEqualTo(expectedSimilarity, Offset.offset(0.0001));
    }

    private void prepareTwoStudentAndOneInstructorRepositoriesForPlagiarismChecks(ProgrammingExercise programmingExercise) throws IOException, GitAPIException {
        var participationStudent1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student1");
        var participationStudent2 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "student2");
        var participationInstructor1 = participationUtilService.addStudentParticipationForProgrammingExercise(programmingExercise, userPrefix + "instructor1");
        var submissionStudent1 = programmingExerciseUtilService.createProgrammingSubmission(participationStudent1, false);
        var submissionStudent2 = programmingExerciseUtilService.createProgrammingSubmission(participationStudent2, false);
        var submissionInstructor1 = programmingExerciseUtilService.createProgrammingSubmission(participationInstructor1, false);
        participationUtilService.addResultToSubmission(submissionStudent1, AssessmentType.AUTOMATIC, null);
        participationUtilService.addResultToSubmission(submissionStudent2, AssessmentType.AUTOMATIC, null);
        participationUtilService.addResultToSubmission(submissionInstructor1, AssessmentType.AUTOMATIC, null);

        prepareTwoSubmissionsForPlagiarismChecks(programmingExercise);
    }

    private Team createTeam(ProgrammingExercise programmingExercise, String suffix) {
        var student = userUtilService.getUserByLogin(userPrefix + "student" + suffix);
        var team = new Team().name("Team " + suffix).shortName(userPrefix + "team" + suffix).exercise(programmingExercise).students(Set.of(student));
        return teamRepository.save(programmingExercise, team);
    }

    private void prepareTwoTeamRepositoriesForPlagiarismChecks(ProgrammingExercise programmingExercise) throws IOException, GitAPIException {
        var participationTeam1 = participationUtilService.addTeamParticipationForProgrammingExercise(programmingExercise, createTeam(programmingExercise, "1"));
        var participationTeam2 = participationUtilService.addTeamParticipationForProgrammingExercise(programmingExercise, createTeam(programmingExercise, "2"));
        var submissionTeam1 = programmingExerciseUtilService.createProgrammingSubmission(participationTeam1, false);
        var submissionTeam2 = programmingExerciseUtilService.createProgrammingSubmission(participationTeam2, false);
        participationUtilService.addResultToSubmission(submissionTeam1, AssessmentType.AUTOMATIC, null);
        participationUtilService.addResultToSubmission(submissionTeam2, AssessmentType.AUTOMATIC, null);

        prepareTwoSubmissionsForPlagiarismChecks(programmingExercise);
    }

    private void prepareTwoSubmissionsForPlagiarismChecks(ProgrammingExercise programmingExercise) throws IOException, GitAPIException {
        var projectKey = programmingExercise.getProjectKey();

        var exampleProgram = """
                public class Main {

                    /**
                     * DO NOT EDIT!
                     */
                    public static void main(String[] args) {
                        Main main = new Main();
                        int magicNumber = main.calculateMagicNumber();

                        System.out.println("Magic number: " + magicNumber);
                    }

                    /**
                     * Calculate the magic number.
                     *
                     * @return the magic number.
                     */
                    private int calculateMagicNumber() {
                        int a = 0;
                        int b = 5;
                        int magicNumber = 0;

                        while (a < b) {
                            magicNumber += b;
                            a++;
                        }

                        return magicNumber;
                    }
                }
                """;

        // Create temporary directories for the mock repositories with proper JPlag structure
        Path tempDir = Files.createTempDirectory("plagiarism-test-repos");
        Path projectDir = tempDir.resolve(projectKey);
        Files.createDirectories(projectDir);

        // Create repository directories with simpler names that work with both test cases
        Path repo1Dir = projectDir.resolve("1-Submission1.java");
        Path repo2Dir = projectDir.resolve("2-Submission2.java");

        Files.createDirectories(repo1Dir);
        Files.createDirectories(repo2Dir);

        // Write Java files with the expected names for the test
        FileUtils.writeByteArrayToFile(repo1Dir.resolve("1-Submission1.java").toFile(), exampleProgram.getBytes(StandardCharsets.UTF_8));
        FileUtils.writeByteArrayToFile(repo2Dir.resolve("2-Submission2.java").toFile(), exampleProgram.getBytes(StandardCharsets.UTF_8));

        // Create mock repositories pointing to these directories
        de.tum.cit.aet.artemis.programming.domain.Repository mockRepo1 = mock(de.tum.cit.aet.artemis.programming.domain.Repository.class);
        when(mockRepo1.getLocalPath()).thenReturn(repo1Dir);

        de.tum.cit.aet.artemis.programming.domain.Repository mockRepo2 = mock(de.tum.cit.aet.artemis.programming.domain.Repository.class);
        when(mockRepo2.getLocalPath()).thenReturn(repo2Dir);

        // Mock all Git service methods that the plagiarism detection service uses
        doAnswer(invocation -> {
            ProgrammingExerciseParticipation participation = invocation.getArgument(0);
            // Get all student participations for this exercise
            var studentParticipations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId()).stream()
                    .filter(p -> p.getParticipant() != null && p.getParticipant().getName() != null && !p.getParticipant().getName().contains("instructor"))
                    .sorted((p1, p2) -> p1.getId().compareTo(p2.getId())).toList();

            if (!studentParticipations.isEmpty() && participation.getId().equals(studentParticipations.get(0).getId())) {
                return mockRepo1;
            }
            else if (studentParticipations.size() > 1 && participation.getId().equals(studentParticipations.get(1).getId())) {
                return mockRepo2;
            }
            else {
                // For any other participation (including instructors), return the first repo as fallback
                return mockRepo1;
            }
        }).when(gitService).getOrCheckoutRepositoryForJPlag(any(ProgrammingExerciseParticipation.class), any(Path.class));

        // Mock the other required methods
        doNothing().when(gitService).resetToOriginHead(any());
        doNothing().when(gitService).deleteLocalRepository(any(de.tum.cit.aet.artemis.programming.domain.Repository.class));

        doReturn(tempDir).when(fileService).getTemporaryUniqueSubfolderPath(any(Path.class), eq(60L));
        doReturn(null).when(uriService).getRepositorySlugFromRepositoryUri(any());
    }

    void testGetPlagiarismResult() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        programmingExercise = programmingExerciseRepository.findWithEagerStudentParticipationsStudentAndSubmissionsById(programmingExercise.getId()).orElseThrow();

        PlagiarismResult expectedResult = textExerciseUtilService.createPlagiarismResultForExercise(programmingExercise);

        var result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/plagiarism-result", HttpStatus.OK, PlagiarismResultDTO.class);
        assertThat(result.plagiarismResult().getId()).isEqualTo(expectedResult.getId());
    }

    void testGetPlagiarismResultWithoutResult() throws Exception {
        Course course = programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = ExerciseUtilService.getFirstExerciseWithType(course, ProgrammingExercise.class);
        var result = request.get("/api/programming/programming-exercises/" + programmingExercise.getId() + "/plagiarism-result", HttpStatus.OK, String.class);
        assertThat(result).isNullOrEmpty();
    }

    void testGetPlagiarismResultWithoutExercise() throws Exception {
        PlagiarismResult result = request.get("/api/programming/programming-exercises/-1/plagiarism-result", HttpStatus.NOT_FOUND, PlagiarismResult.class);
        assertThat(result).isNull();
    }

    void testValidateValidAuxiliaryRepository() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults();
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
    }

    void testValidateAuxiliaryRepositoryIdSetOnRequest() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withId(0L), HttpStatus.INTERNAL_SERVER_ERROR);

    }

    void testValidateAuxiliaryRepositoryWithoutName() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withoutName(), HttpStatus.BAD_REQUEST);
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withName(""), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithTooLongName() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withName(generateStringWithMoreThanNCharacters(AuxiliaryRepository.MAX_NAME_LENGTH)), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithDuplicatedName() throws Exception {
        testAuxRepo(List.of(AuxiliaryRepositoryBuilder.defaults().get(), AuxiliaryRepositoryBuilder.defaults().withoutCheckoutDirectory().get()), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithRestrictedName() throws Exception {
        for (RepositoryType repositoryType : RepositoryType.values()) {
            testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withName(repositoryType.getName()), HttpStatus.BAD_REQUEST);
        }
    }

    void testValidateAuxiliaryRepositoryWithInvalidCheckoutDirectory() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withCheckoutDirectory("..."), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithoutCheckoutDirectory() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults().withoutCheckoutDirectory();
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
    }

    void testValidateAuxiliaryRepositoryWithBlankCheckoutDirectory() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults().withCheckoutDirectory("   ");
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
    }

    void testValidateAuxiliaryRepositoryWithTooLongCheckoutDirectory() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withCheckoutDirectory(generateStringWithMoreThanNCharacters(AuxiliaryRepository.MAX_CHECKOUT_DIRECTORY_LENGTH)),
                HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithDuplicatedCheckoutDirectory() throws Exception {
        testAuxRepo(List.of(AuxiliaryRepositoryBuilder.defaults().get(), AuxiliaryRepositoryBuilder.defaults().withDifferentName().get()), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithNullCheckoutDirectory() throws Exception {
        testAuxRepo(List.of(AuxiliaryRepositoryBuilder.defaults().get(), AuxiliaryRepositoryBuilder.defaults().withDifferentName().withoutCheckoutDirectory().get(),
                AuxiliaryRepositoryBuilder.defaults().get()), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithTooLongDescription() throws Exception {
        testAuxRepo(AuxiliaryRepositoryBuilder.defaults().withDescription(generateStringWithMoreThanNCharacters(500)), HttpStatus.BAD_REQUEST);
    }

    void testValidateAuxiliaryRepositoryWithoutDescription() throws Exception {
        AuxiliaryRepositoryBuilder auxRepoBuilder = AuxiliaryRepositoryBuilder.defaults().withoutDescription();
        testAuxRepo(auxRepoBuilder, HttpStatus.OK);
    }

    void testGetAuxiliaryRepositoriesMissingExercise() throws Exception {
        request.get(defaultGetAuxReposEndpoint(-1L), HttpStatus.NOT_FOUND, List.class);
    }

    void testGetAuxiliaryRepositoriesOk() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
        programmingExercise.addAuxiliaryRepository(auxiliaryRepositoryRepository.save(AuxiliaryRepositoryBuilder.defaults().get()));
        programmingExercise
                .addAuxiliaryRepository(auxiliaryRepositoryRepository.save(AuxiliaryRepositoryBuilder.defaults().withDifferentName().withDifferentCheckoutDirectory().get()));
        programmingExerciseRepository.save(programmingExercise);
        var returnedAuxiliaryRepositories = request.get(defaultGetAuxReposEndpoint(), HttpStatus.OK, List.class);
        assertThat(returnedAuxiliaryRepositories).hasSize(2);
    }

    void testGetAuxiliaryRepositoriesEmptyOk() throws Exception {
        programmingExercise = programmingExerciseRepository.findWithAuxiliaryRepositoriesById(programmingExercise.getId()).orElseThrow();
        var returnedAuxiliaryRepositories = request.get(defaultGetAuxReposEndpoint(), HttpStatus.OK, List.class);
        assertThat(returnedAuxiliaryRepositories).isEmpty();
    }

    void testGetAuxiliaryRepositoriesForbidden() throws Exception {
        request.get(defaultGetAuxReposEndpoint(), HttpStatus.FORBIDDEN, List.class);
    }

    void testResetForbidden() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, false, false, false);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.FORBIDDEN);
    }

    void testResetOnlyDeleteBuildPlansForbidden() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(true, false, false, false);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.FORBIDDEN);
    }

    void testResetDeleteBuildPlansAndDeleteStudentRepositoriesForbidden() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(true, true, false, false);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.FORBIDDEN);
    }

    void testResetOnlyDeleteStudentParticipationsSubmissionsAndResultsForbidden() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, false, true, false);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.FORBIDDEN);
    }

    void testResetExerciseNotFound() throws Exception {
        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, false, false, false);
        request.put(defaultResetEndpoint(-1L), resetOptions, HttpStatus.NOT_FOUND);
    }

    void testResetOnlyDeleteBuildPlansSuccess() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2")) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }

        // Two participations exist with build plans before reset
        var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
        assertThat(participations).hasSize(2);
        participations.forEach(participation -> assertThat(participation.getBuildPlanId()).isNotNull());

        var resetOptions = new ProgrammingExerciseResetOptionsDTO(true, false, false, false);
        request.put(defaultResetEndpoint(programmingExercise.getId()), resetOptions, HttpStatus.OK);

        // Two participations exist with build plans removed after reset
        participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
        assertThat(participations).hasSize(2);
        participations.forEach(participation -> assertThat(participation.getBuildPlanId()).isNull());
    }

    void testResetDeleteBuildPlansAndDeleteStudentRepositoriesSuccess() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2")) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }

        // Two participations exist with build plans and repositories before reset
        var participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
        assertThat(participations).hasSize(2);
        participations.forEach(participation -> {
            assertThat(participation.getRepositoryUri()).isNotNull();
            assertThat(participation.getBuildPlanId()).isNotNull();
        });

        var resetOptions = new ProgrammingExerciseResetOptionsDTO(true, true, false, false);
        request.put(defaultResetEndpoint(programmingExercise.getId()), resetOptions, HttpStatus.OK);

        // Two participations exist with build plans and repositories removed after reset
        participations = programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId());
        assertThat(participations).hasSize(2);
        participations.forEach(participation -> {
            assertThat(participation.getRepositoryUri()).isNull();
            assertThat(participation.getBuildPlanId()).isNull();
        });
    }

    void testResetOnlyDeleteStudentParticipationsSubmissionsAndResultsSuccess() throws Exception {
        final var projectKey = programmingExercise.getProjectKey();
        for (final var planName : List.of(userPrefix + "student1", userPrefix + "student2")) {
            mockDelegate.mockDeleteBuildPlan(projectKey, projectKey + "-" + planName.toUpperCase(), false);
        }

        // Two participations exist before reset
        assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId())).hasSize(2);

        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, false, true, false);
        request.put(defaultResetEndpoint(programmingExercise.getId()), resetOptions, HttpStatus.OK);

        // No participations exist after reset
        assertThat(programmingExerciseStudentParticipationRepository.findByExerciseId(programmingExercise.getId())).isEmpty();
    }

    void testResetOnlyRecreateBuildPlansSuccess() throws Exception {
        addAuxiliaryRepositoryToExercise();
        mockDelegate.mockGetProjectKeyFromAnyUrl(programmingExercise.getProjectKey());
        String templateBuildPlanName = programmingExercise.getProjectKey() + "-" + TEMPLATE.getName();
        String solutionBuildPlanName = programmingExercise.getProjectKey() + "-" + SOLUTION.getName();
        mockDelegate.mockGetBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanName, true, true, false, false);
        mockDelegate.mockGetBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanName, true, true, false, false);
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), templateBuildPlanName, false);
        mockDelegate.mockDeleteBuildPlan(programmingExercise.getProjectKey(), solutionBuildPlanName, false);
        mockDelegate.mockGetBuildPlanConfig(programmingExercise.getProjectKey(), templateBuildPlanName);
        mockDelegate.mockGetBuildPlanConfig(programmingExercise.getProjectKey(), solutionBuildPlanName);
        mockDelegate.mockConnectorRequestsForSetup(programmingExercise, false, false, false);

        var resetOptions = new ProgrammingExerciseResetOptionsDTO(false, false, false, true);
        request.put(defaultResetEndpoint(), resetOptions, HttpStatus.OK);
    }

    void testExportAuxiliaryRepositoryForbidden() throws Exception {
        AuxiliaryRepository repository = addAuxiliaryRepositoryToExercise();
        request.get(defaultExportInstructorAuxiliaryRepository(repository), HttpStatus.FORBIDDEN, File.class);
    }

    void testExportAuxiliaryRepositoryBadRequest() throws Exception {
        AuxiliaryRepository repository = addAuxiliaryRepositoryToExercise();
        request.get(defaultExportInstructorAuxiliaryRepository(repository), HttpStatus.BAD_REQUEST, File.class);
    }

    void testExportAuxiliaryRepositoryExerciseNotFound() throws Exception {
        request.get(defaultExportInstructorAuxiliaryRepository(-1L, 1L), HttpStatus.NOT_FOUND, File.class);
    }

    void testExportAuxiliaryRepositoryRepositoryNotFound() throws Exception {
        request.get(defaultExportInstructorAuxiliaryRepository(programmingExercise.getId(), -1L), HttpStatus.NOT_FOUND, File.class);
    }

    private String generateStringWithMoreThanNCharacters(int n) {
        return IntStream.range(0, n + 1).mapToObj(unused -> "a").reduce("", String::concat);
    }

    private AuxiliaryRepository addAuxiliaryRepositoryToExercise() {
        AuxiliaryRepository repository = AuxiliaryRepositoryBuilder.defaults().get();
        auxiliaryRepositoryRepository.save(repository);
        programmingExercise.setAuxiliaryRepositories(new ArrayList<>());
        programmingExercise.addAuxiliaryRepository(repository);
        programmingExerciseRepository.save(programmingExercise);
        return repository;
    }

    public void addAuxiliaryRepositoryToExercise(ProgrammingExercise exercise) {
        AuxiliaryRepository repository = AuxiliaryRepositoryBuilder.defaults().get();
        auxiliaryRepositoryRepository.save(repository);
        exercise.setAuxiliaryRepositories(new ArrayList<>());
        exercise.addAuxiliaryRepository(repository);
        programmingExerciseRepository.save(exercise);
    }

    private String defaultAuxiliaryRepositoryEndpoint() {
        return "/api/programming/programming-exercises";
    }

    private String defaultResetEndpoint() {
        return defaultResetEndpoint(programmingExercise.getId());
    }

    private String defaultGetAuxReposEndpoint() {
        return defaultGetAuxReposEndpoint(programmingExercise.getId());
    }

    private String defaultExportInstructorAuxiliaryRepository(AuxiliaryRepository repository) {
        return defaultExportInstructorAuxiliaryRepository(programmingExercise.getId(), repository.getId());
    }

    private String defaultResetEndpoint(Long exerciseId) {
        return "/api/programming/programming-exercises/" + exerciseId + "/reset";
    }

    private String defaultGetAuxReposEndpoint(Long exerciseId) {
        return "/api/programming/programming-exercises/" + exerciseId + "/auxiliary-repository";
    }

    private String defaultExportInstructorAuxiliaryRepository(Long exerciseId, Long repositoryId) {
        return "/api/programming/programming-exercises/" + exerciseId + "/export-instructor-auxiliary-repository/" + repositoryId;
    }

    private void testAuxRepo(AuxiliaryRepositoryBuilder body, HttpStatus expectedStatus) throws Exception {
        testAuxRepo(List.of(body.get()), expectedStatus);
    }

    private void testAuxRepo(List<AuxiliaryRepository> body, HttpStatus expectedStatus) throws Exception {
        programmingExercise.setAuxiliaryRepositories(body);
        request.putWithResponseBody(defaultAuxiliaryRepositoryEndpoint(), programmingExercise, ProgrammingExercise.class, expectedStatus);
    }

    private static class AuxiliaryRepositoryBuilder {

        private final AuxiliaryRepository repository;

        private AuxiliaryRepositoryBuilder() {
            this.repository = new AuxiliaryRepository();
        }

        static AuxiliaryRepositoryBuilder of() {
            return new AuxiliaryRepositoryBuilder();
        }

        static AuxiliaryRepositoryBuilder defaults() {
            return of().withoutId().withName("defaultname").withCheckoutDirectory("directory").withDescription("DefaultDescription");
        }

        AuxiliaryRepositoryBuilder withName(String name) {
            repository.setName(name);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutName() {
            repository.setName(null);
            return this;
        }

        AuxiliaryRepositoryBuilder withDifferentName() {
            repository.setName("differentname");
            return this;
        }

        AuxiliaryRepositoryBuilder withDescription(String description) {
            repository.setDescription(description);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutDescription() {
            repository.setDescription(null);
            return this;
        }

        AuxiliaryRepositoryBuilder withCheckoutDirectory(String checkoutDirectory) {
            repository.setCheckoutDirectory(checkoutDirectory);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutCheckoutDirectory() {
            repository.setCheckoutDirectory(null);
            return this;
        }

        AuxiliaryRepositoryBuilder withDifferentCheckoutDirectory() {
            repository.setCheckoutDirectory("differentcheckoutdirectory");
            return this;
        }

        AuxiliaryRepositoryBuilder withId(Long id) {
            repository.setId(id);
            return this;
        }

        AuxiliaryRepositoryBuilder withoutId() {
            repository.setId(null);
            return this;
        }

        AuxiliaryRepository get() {
            return repository;
        }
    }

    void testReEvaluateAndUpdateProgrammingExercise_instructorNotInCourse_forbidden(String testPrefix) throws Exception {
        userUtilService.addInstructor("other-instructors", testPrefix + "instructoralt1");
        programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseTestRepository.findAllWithEagerTemplateAndSolutionParticipations().getFirst();
        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate", programmingExercise, HttpStatus.FORBIDDEN);
    }

    void testReEvaluateAndUpdateProgrammingExercise_notFound() throws Exception {
        request.put("/api/programming/programming-exercises/" + 123456789 + "/re-evaluate", programmingExercise, HttpStatus.NOT_FOUND);
    }

    void testReEvaluateAndUpdateProgrammingExercise_isNotSameGivenExerciseIdInRequestBody_conflict() throws Exception {
        programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        programmingExerciseUtilService.addCourseWithOneProgrammingExercise();
        ProgrammingExercise programmingExercise = programmingExerciseTestRepository.findAllWithEagerTemplateAndSolutionParticipations().getFirst();
        ProgrammingExercise programmingExerciseToBeConflicted = programmingExerciseTestRepository.findAllWithEagerTemplateAndSolutionParticipations().get(1);

        request.put("/api/programming/programming-exercises/" + programmingExercise.getId() + "/re-evaluate", programmingExerciseToBeConflicted, HttpStatus.CONFLICT);
    }

    void test_redirectGetSolutionRepositoryFilesWithoutContent() throws Exception {
        test_redirectGetSolutionRepositoryFilesWithoutContent((exercise, files) -> {
            LocalRepository localRepository = new LocalRepository("main");
            try {
                programmingUtilTestService.setupSolution(files, exercise, localRepository);
            }
            catch (Exception e) {
                fail("Setup solution threw unexpected exception: " + e.getMessage());
            }
            return localRepository;
        });
    }

    private void test_redirectGetSolutionRepositoryFilesWithoutContent(BiFunction<ProgrammingExercise, Map<String, String>, LocalRepository> setupRepositoryMock) throws Exception {
        setupRepositoryMock.apply(programmingExercise, Map.ofEntries(Map.entry("A.java", "abc"), Map.entry("B.java", "cde"), Map.entry("C.java", "efg")));

        var savedExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        // We expect an URL which is the endpoint, with which the file contents can be retrieved
        request.getWithForwardedUrl("/api/programming/programming-exercises/" + programmingExercise.getId() + "/file-names", HttpStatus.OK,
                "/api/programming/repository/" + savedExercise.getSolutionParticipation().getId() + "/file-names");
    }

    void test_redirectGetTemplateRepositoryFilesWithContentOmitBinaries() throws Exception {
        BiFunction<ProgrammingExercise, Map<String, String>, LocalRepository> redirectFnc = (exercise, files) -> {
            LocalRepository localRepository = new LocalRepository("main");
            try {
                programmingUtilTestService.setupTemplate(files, exercise, localRepository);
            }
            catch (Exception e) {
                fail("Setup template threw unexpected exception: " + e.getMessage());
            }
            return localRepository;
        };

        test_redirectGetTemplateRepositoryFilesWithContentOmitBinaries(redirectFnc);

    }

    void test_redirectGetTemplateRepositoryFilesWithContent() throws Exception {
        BiFunction<ProgrammingExercise, Map<String, String>, LocalRepository> redirectFnc = (exercise, files) -> {
            LocalRepository localRepository = new LocalRepository("main");
            try {
                programmingUtilTestService.setupTemplate(files, exercise, localRepository);
            }
            catch (Exception e) {
                fail("Setup template threw unexpected exception: " + e.getMessage());
            }
            return localRepository;
        };

        test_redirectGetTemplateRepositoryFilesWithContent(redirectFnc);

    }

    private void test_redirectGetTemplateRepositoryFilesWithContent(BiFunction<ProgrammingExercise, Map<String, String>, LocalRepository> setupRepositoryMock) throws Exception {
        setupRepositoryMock.apply(programmingExercise, Map.ofEntries(Map.entry("A.java", "abc"), Map.entry("B.java", "cde"), Map.entry("C.java", "efg")));

        var savedExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());

        request.getWithForwardedUrl("/api/programming/programming-exercises/" + programmingExercise.getId() + "/template-files-content", HttpStatus.OK,
                "/api/programming/repository/" + savedExercise.getTemplateParticipation().getId() + "/files-content");
    }

    private void test_redirectGetTemplateRepositoryFilesWithContentOmitBinaries(BiFunction<ProgrammingExercise, Map<String, String>, LocalRepository> setupRepositoryMock)
            throws Exception {
        setupRepositoryMock.apply(programmingExercise, Map.ofEntries(Map.entry("A.java", "abc"), Map.entry("B.jar", "binaryContent")));

        var savedExercise = programmingExerciseRepository.findByIdWithTemplateAndSolutionParticipationElseThrow(programmingExercise.getId());
        var queryParams = "?omitBinaries=true";
        request.getWithForwardedUrl("/api/programming/programming-exercises/" + programmingExercise.getId() + "/template-files-content" + queryParams, HttpStatus.OK,
                "/api/programming/repository/" + savedExercise.getTemplateParticipation().getId() + "/files-content" + queryParams);
    }

    void testRedirectGetParticipationRepositoryFilesWithContentAtCommit(String testPrefix) throws Exception {
        testRedirectGetParticipationRepositoryFilesWithContentAtCommit((exercise, files) -> {
            LocalRepository localRepository = new LocalRepository("main");
            var studentLogin = testPrefix + "student1";
            try {
                localRepository.configureRepos(localVCRepoPath, "testLocalRepo", "testOriginRepo");
                return programmingUtilTestService.setupSubmission(files, exercise, localRepository, studentLogin);
            }
            catch (Exception e) {
                fail("Test setup failed", e);
            }
            return null;
        });
    }

    private void testRedirectGetParticipationRepositoryFilesWithContentAtCommit(BiFunction<ProgrammingExercise, Map<String, String>, ProgrammingSubmission> setupRepositoryMock)
            throws Exception {
        var submission = setupRepositoryMock.apply(programmingExercise, Map.of("A.java", "abc", "B.java", "cde", "C.java", "efg"));
        String filesWithContentsAsJson = """
                {
                  "C.java" : "efg",
                  "B.java" : "cde",
                  "A.java" : "abc",
                  "README.md" : "Initial commit"
                }""";

        request.getWithFileContents("/api/programming/programming-exercise-participations/" + participation1.getId() + "/files-content/" + submission.getCommitHash(),
                HttpStatus.OK, filesWithContentsAsJson);
    }

    void testRedirectGetParticipationRepositoryFilesWithContentAtCommitForbidden(String testPrefix) throws Exception {
        testRedirectGetParticipationRepositoryFilesWithContentAtCommitForbidden((exercise, files) -> {
            LocalRepository localRepository = new LocalRepository("main");

            var studentLogin = testPrefix + "student1";
            try {
                localRepository.configureRepos(localVCRepoPath, "testLocalRepo", "testOriginRepo");
                return programmingUtilTestService.setupSubmission(files, exercise, localRepository, studentLogin);
            }
            catch (Exception e) {
                fail("Test setup failed");
            }
            return null;
        });
    }

    private void testRedirectGetParticipationRepositoryFilesWithContentAtCommitForbidden(
            BiFunction<ProgrammingExercise, Map<String, String>, ProgrammingSubmission> setupRepositoryMock) throws Exception {
        var submission = setupRepositoryMock.apply(programmingExercise, Map.of("A.java", "abc", "B.java", "cde", "C.java", "efg"));

        request.get("/api/programming/programming-exercise-participations/" + participation1.getId() + "/files-content/" + submission.getCommitHash(), HttpStatus.FORBIDDEN,
                Map.class);
    }

    private long getMaxProgrammingExerciseId() {
        return programmingExerciseRepository.findAll(PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "id"))).stream().mapToLong(ProgrammingExercise::getId).max().orElse(1L);
    }

    private void setupMocksForConsistencyChecksOnImport(ProgrammingExercise sourceExercise) throws Exception {
        var programmingExercise = programmingExerciseRepository.findWithTemplateAndSolutionParticipationAndAuxiliaryRepositoriesById(sourceExercise.getId()).orElseThrow();

        for (var auxiliaryRepository : programmingExercise.getAuxiliaryRepositories()) {
            mockDelegate.mockGetRepositorySlugFromRepositoryUri(sourceExercise.generateRepositoryName("auxrepo"), auxiliaryRepository.getVcsRepositoryUri());
        }
        mockDelegate.mockCheckIfBuildPlanExists(uriService.getProjectKeyFromRepositoryUri(programmingExercise.getVcsTemplateRepositoryUri()),
                programmingExercise.getTemplateBuildPlanId(), true, false);
        mockDelegate.mockCheckIfBuildPlanExists(uriService.getProjectKeyFromRepositoryUri(programmingExercise.getVcsSolutionRepositoryUri()),
                programmingExercise.getSolutionBuildPlanId(), true, false);
    }
}
