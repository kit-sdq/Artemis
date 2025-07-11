package de.tum.cit.aet.artemis.programming.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE_AND_SCHEDULING;
import static java.time.ZonedDateTime.now;

import java.net.URISyntaxException;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.core.security.SecurityUtils;
import de.tum.cit.aet.artemis.core.service.ProfileService;
import de.tum.cit.aet.artemis.exercise.service.ParticipationDeletionService;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExerciseStudentParticipation;
import de.tum.cit.aet.artemis.programming.domain.VcsRepositoryUri;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseRepository;
import de.tum.cit.aet.artemis.programming.repository.ProgrammingExerciseStudentParticipationRepository;

@Lazy
@Service
@Profile(PROFILE_CORE_AND_SCHEDULING)
public class AutomaticProgrammingExerciseCleanupService {

    private static final Logger log = LoggerFactory.getLogger(AutomaticProgrammingExerciseCleanupService.class);

    private final ProfileService profileService;

    private final ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository;

    private final ParticipationDeletionService participationDeletionService;

    private final ProgrammingExerciseRepository programmingExerciseRepository;

    private final GitService gitService;

    private static final int STUDENT_PARTICIPATION_CLEANUP_BATCH_SIZE = 500;

    @Value("${artemis.external-system-request.batch-size}")
    private int externalSystemRequestBatchSize;

    @Value("${artemis.external-system-request.batch-waiting-time}")
    private int externalSystemRequestBatchWaitingTime;

    public AutomaticProgrammingExerciseCleanupService(ProfileService profileService,
            ProgrammingExerciseStudentParticipationRepository programmingExerciseStudentParticipationRepository, ParticipationDeletionService participationDeletionService,
            ProgrammingExerciseRepository programmingExerciseRepository, GitService gitService) {
        this.profileService = profileService;
        this.programmingExerciseStudentParticipationRepository = programmingExerciseStudentParticipationRepository;
        this.participationDeletionService = participationDeletionService;
        this.programmingExerciseRepository = programmingExerciseRepository;
        this.gitService = gitService;
    }

    /**
     * cleans up old build plans on the continuous integration server and old local git repositories on the Artemis server at 3:00:00 am in the night in form of a repeating "cron"
     * job
     */
    @Scheduled(cron = "${artemis.scheduling.programming-exercises-cleanup-time:0 0 3 * * *}") // execute this every night at 3:00:00 am
    public void cleanup() {

        if (!profileService.isProductionActive()) {
            // only execute this on production server, i.e. when the prod profile is active
            // NOTE: if you want to test this locally, please comment it out, but do not commit the changes
            return;
        }
        try {
            if (!profileService.isLocalCIActive()) {
                // no build plan cleanup is needed for systems using LocalCI
                cleanupBuildPlansOnContinuousIntegrationServer();
            }
        }
        catch (Exception ex) {
            log.error("Exception occurred during cleanupBuildPlansOnContinuousIntegrationServer", ex);
        }
        try {
            cleanupGitWorkingCopiesOnArtemisServer();
        }
        catch (Exception ex) {
            log.error("Exception occurred during cleanupGitWorkingCopiesOnArtemisServer", ex);
        }
    }

    /**
     * cleans up old local git repositories on the Artemis server
     */
    public void cleanupGitWorkingCopiesOnArtemisServer() {
        SecurityUtils.setAuthorizationObject();
        log.info("Cleanup git repositories on Artemis server");
        // we are specifically interested in exercises older than 8 weeks
        var latestDate = ZonedDateTime.now().minusWeeks(8).truncatedTo(ChronoUnit.DAYS);
        // NOTE: for now we would like to cover more cases to also cleanup older repositories
        var earliestDate = latestDate.minusYears(1).truncatedTo(ChronoUnit.DAYS);

        // Cleanup all student repos in the REPOS folder (based on the student participations) 8 weeks after the exercise due date or exam end date
        cleanStudentParticipationsRepositories(earliestDate, latestDate);

        // Cleanup template, tests and solution repos in the REPOS folder 8 weeks after the course or exam is over
        log.info("Search for exercises with course or exam date from {} until {}", earliestDate, latestDate);
        var programmingExercises = programmingExerciseRepository.findAllByRecentCourseEndDate(earliestDate, latestDate);
        programmingExercises.addAll(programmingExerciseRepository.findAllByRecentExamEndDate(earliestDate, latestDate));
        log.info("Found {} programming exercise to clean local template, test and solution: {}", programmingExercises.size(),
                programmingExercises.stream().map(ProgrammingExercise::getProjectKey).collect(Collectors.joining(", ")));
        if (!programmingExercises.isEmpty()) {
            for (var programmingExercise : programmingExercises) {
                gitService.deleteLocalRepository(programmingExercise.getVcsTemplateRepositoryUri());
                gitService.deleteLocalRepository(programmingExercise.getVcsSolutionRepositoryUri());
                gitService.deleteLocalRepository(programmingExercise.getVcsTestRepositoryUri());
                gitService.deleteLocalProgrammingExerciseReposFolder(programmingExercise);
            }
            log.info("Finished cleaning local template, test and solution repositories");
        }
    }

    private void cleanStudentParticipationsRepositories(ZonedDateTime earliestDate, ZonedDateTime latestDate) {
        log.info("Search for exercises with due date from {} until {}", earliestDate, latestDate);
        // Get all relevant participation ids
        Pageable pageable = Pageable.ofSize(STUDENT_PARTICIPATION_CLEANUP_BATCH_SIZE);
        Page<String> uriBatch = programmingExerciseStudentParticipationRepository.findRepositoryUrisByRecentDueDateOrRecentExamEndDate(earliestDate, latestDate, pageable);
        log.info("Found {} student participations to clean local student repositories in {} batches.", uriBatch.getTotalElements(), uriBatch.getTotalPages());
        if (uriBatch.getTotalElements() > 0) {
            uriBatch.forEach(this::deleteLocalRepositoryByUriString);
            while (!uriBatch.isLast()) {
                uriBatch = programmingExerciseStudentParticipationRepository.findRepositoryUrisByRecentDueDateOrRecentExamEndDate(earliestDate, latestDate,
                        uriBatch.nextPageable());
                uriBatch.forEach(this::deleteLocalRepositoryByUriString);
            }
            log.info("Finished cleaning local student repositories");
        }
    }

    private void deleteLocalRepositoryByUriString(String uri) {
        try {
            VcsRepositoryUri vcsRepositoryUrl = new VcsRepositoryUri(uri);
            gitService.deleteLocalRepository(vcsRepositoryUrl);
        }
        catch (URISyntaxException e) {
            log.error("Cannot create URI for repositoryUri: {}", uri, e);
        }
    }

    /**
     * Cleans up old build plans on the continuous integration server
     */
    public void cleanupBuildPlansOnContinuousIntegrationServer() {
        log.info("Find build plans for potential cleanup");

        AtomicLong countAfterBuildAndTestDate = new AtomicLong();
        AtomicLong countNoResult = new AtomicLong();
        AtomicLong countSuccessfulLatestResult = new AtomicLong();
        AtomicLong countUnsuccessfulLatestResult = new AtomicLong();

        Set<ProgrammingExerciseStudentParticipation> participationsWithBuildPlanToDelete = new HashSet<>();

        var participationsWithBuildPlans = programmingExerciseStudentParticipationRepository.findAllWithBuildPlanIdWithResults();
        log.info("Found {} potential build plans to delete", participationsWithBuildPlans.size());
        participationsWithBuildPlans.forEach(participation -> {

            if (participation.getBuildPlanId() == null || participation.getParticipant() == null) {
                // NOTE: based on the query above, this code is not reachable. We check it anyway to be 100% sure such participations won't be processed
                // already cleaned up or we only want to clean up build plans of students or teams (NOT template or solution build plans)
                return;
            }

            if (participation.getProgrammingExercise() != null && Hibernate.isInitialized(participation.getProgrammingExercise())) {
                var programmingExercise = participation.getProgrammingExercise();

                if (checkFutureExamExercises(programmingExercise)) {
                    return;
                }

                if (checkBuildAndTestExercises(programmingExercise, participation, participationsWithBuildPlanToDelete, countAfterBuildAndTestDate)) {
                    return;
                }
            }

            checkLastResults(participation, participationsWithBuildPlanToDelete, countNoResult, countSuccessfulLatestResult, countUnsuccessfulLatestResult);
        });

        log.info("Found {} old build plans to delete", participationsWithBuildPlanToDelete.size());
        log.info("  Found {} build plans at least 1 day older than 'build and test submissions after due date", countAfterBuildAndTestDate);
        log.info("  Found {} build plans without results 3 days after initialization", countNoResult);
        log.info("  Found {} build plans with successful latest result is older than 1 day", countSuccessfulLatestResult);
        log.info("  Found {} build plans with unsuccessful latest result is older than 5 days", countUnsuccessfulLatestResult);

        deleteBuildPlans(participationsWithBuildPlanToDelete);
    }

    // returns false if the participation should be cleaned after the criteria checked in this method
    private boolean checkBuildAndTestExercises(ProgrammingExercise programmingExercise, ProgrammingExerciseStudentParticipation participation,
            Set<ProgrammingExerciseStudentParticipation> participationsWithBuildPlanToDelete, AtomicLong countAfterBuildAndTestDate) {
        if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate() != null) {
            if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().isAfter(now())) {
                // we don't clean up plans that will definitely be executed in the future
                return true;
            }

            // 1st case: delete the build plan 1 day after the build and test student submissions after due date, because then no builds should be executed any more
            // and the students repos will be locked anyway.
            if (programmingExercise.getBuildAndTestStudentSubmissionsAfterDueDate().plusDays(1).isBefore(now())) {
                participationsWithBuildPlanToDelete.add(participation);
                countAfterBuildAndTestDate.getAndIncrement();
                return true;
            }
        }
        return false;
    }

    private void checkLastResults(ProgrammingExerciseStudentParticipation participation, Set<ProgrammingExerciseStudentParticipation> participationsWithBuildPlanToDelete,
            AtomicLong countNoResult, AtomicLong countSuccessfulLatestResult, AtomicLong countUnsuccessfulLatestResult) {
        Result result = participation.findLatestResult();
        // 2nd case: delete the build plan 3 days after the participation was initialized in case there is no result
        if (result == null) {
            if (participation.getInitializationDate() != null && participation.getInitializationDate().plusDays(3).isBefore(now())) {
                participationsWithBuildPlanToDelete.add(participation);
                countNoResult.getAndIncrement();
            }
        }
        else {
            // 3rd case: delete the build plan after 1 days in case the latest result is successful
            if (result.isSuccessful()) {
                if (result.getCompletionDate() != null && result.getCompletionDate().plusDays(1).isBefore(now())) {
                    participationsWithBuildPlanToDelete.add(participation);
                    countSuccessfulLatestResult.getAndIncrement();
                }
            }
            // 4th case: delete the build plan after 5 days in case the latest result is NOT successful
            else {
                if (result.getCompletionDate() != null && result.getCompletionDate().plusDays(5).isBefore(now())) {
                    participationsWithBuildPlanToDelete.add(participation);
                    countUnsuccessfulLatestResult.getAndIncrement();
                }
            }
        }
    }

    // returns false if the participation should be cleaned after the criteria checked in this method
    private boolean checkFutureExamExercises(ProgrammingExercise programmingExercise) {
        if (programmingExercise.isExamExercise() && programmingExercise.getExerciseGroup().getExam() != null) {
            var exam = programmingExercise.getExerciseGroup().getExam();
            // we don't clean up plans that will definitely be executed in the future as part of an exam (and we have 1 day buffer time for exams)
            return exam.getEndDate().plusDays(1).isAfter(now());
        }
        return false;
    }

    private void deleteBuildPlans(Set<ProgrammingExerciseStudentParticipation> participationsWithBuildPlanToDelete) {
        // Limit to 5000 deletions per night
        List<ProgrammingExerciseStudentParticipation> actualParticipationsToClean = participationsWithBuildPlanToDelete.stream().limit(5000).toList();
        List<String> buildPlanIds = actualParticipationsToClean.stream().map(ProgrammingExerciseStudentParticipation::getBuildPlanId).toList();
        log.info("Build plans to cleanup: {}", buildPlanIds);

        int index = 0;
        for (ProgrammingExerciseStudentParticipation participation : actualParticipationsToClean) {
            if (index > 0 && index % externalSystemRequestBatchSize == 0) {
                try {
                    log.info("Sleep for {}s during cleanupBuildPlansOnContinuousIntegrationServer", externalSystemRequestBatchWaitingTime / 1000);
                    Thread.sleep(externalSystemRequestBatchWaitingTime);
                }
                catch (InterruptedException ex) {
                    log.error("Exception encountered when pausing before cleaning up build plans", ex);
                }
            }

            try {
                participationDeletionService.cleanupBuildPlan(participation);
            }
            catch (Exception ex) {
                log.error("Could not cleanup build plan in participation {}", participation.getId(), ex);
            }

            index++;
        }
        log.info("{} build plans have been cleaned", actualParticipationsToClean.size());
    }
}
