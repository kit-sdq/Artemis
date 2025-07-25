package de.tum.cit.aet.artemis.quiz.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;
import static java.time.ZonedDateTime.now;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Page;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import de.tum.cit.aet.artemis.assessment.domain.Result;
import de.tum.cit.aet.artemis.assessment.repository.ResultRepository;
import de.tum.cit.aet.artemis.core.FilePathType;
import de.tum.cit.aet.artemis.core.config.Constants;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.dto.SearchResultPageDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.CalendarEventDTO;
import de.tum.cit.aet.artemis.core.dto.calendar.QuizExerciseCalendarEventDTO;
import de.tum.cit.aet.artemis.core.dto.pageablesearch.SearchTermPageableSearchDTO;
import de.tum.cit.aet.artemis.core.exception.BadRequestAlertException;
import de.tum.cit.aet.artemis.core.service.messaging.InstanceMessageSendService;
import de.tum.cit.aet.artemis.core.util.CalendarEventRelatedEntity;
import de.tum.cit.aet.artemis.core.util.CalendarEventSemantics;
import de.tum.cit.aet.artemis.core.util.FilePathConverter;
import de.tum.cit.aet.artemis.core.util.FileUtil;
import de.tum.cit.aet.artemis.core.util.PageUtil;
import de.tum.cit.aet.artemis.exercise.domain.participation.StudentParticipation;
import de.tum.cit.aet.artemis.exercise.service.ExerciseService;
import de.tum.cit.aet.artemis.exercise.service.ExerciseSpecificationService;
import de.tum.cit.aet.artemis.quiz.domain.DragAndDropQuestion;
import de.tum.cit.aet.artemis.quiz.domain.DragItem;
import de.tum.cit.aet.artemis.quiz.domain.QuizBatch;
import de.tum.cit.aet.artemis.quiz.domain.QuizExercise;
import de.tum.cit.aet.artemis.quiz.domain.QuizMode;
import de.tum.cit.aet.artemis.quiz.domain.QuizPointStatistic;
import de.tum.cit.aet.artemis.quiz.domain.QuizQuestion;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.quiz.domain.SubmittedAnswer;
import de.tum.cit.aet.artemis.quiz.repository.DragAndDropMappingRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizExerciseRepository;
import de.tum.cit.aet.artemis.quiz.repository.QuizSubmissionRepository;
import de.tum.cit.aet.artemis.quiz.repository.ShortAnswerMappingRepository;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class QuizExerciseService extends QuizService<QuizExercise> {

    public static final String ENTITY_NAME = "QuizExercise";

    private static final Logger log = LoggerFactory.getLogger(QuizExerciseService.class);

    private final QuizExerciseRepository quizExerciseRepository;

    private final ResultRepository resultRepository;

    private final QuizSubmissionRepository quizSubmissionRepository;

    private final InstanceMessageSendService instanceMessageSendService;

    private final QuizStatisticService quizStatisticService;

    private final QuizBatchService quizBatchService;

    private final ExerciseSpecificationService exerciseSpecificationService;

    private final ExerciseService exerciseService;

    public QuizExerciseService(QuizExerciseRepository quizExerciseRepository, ResultRepository resultRepository, QuizSubmissionRepository quizSubmissionRepository,
            InstanceMessageSendService instanceMessageSendService, QuizStatisticService quizStatisticService, QuizBatchService quizBatchService,
            ExerciseSpecificationService exerciseSpecificationService, DragAndDropMappingRepository dragAndDropMappingRepository,
            ShortAnswerMappingRepository shortAnswerMappingRepository, ExerciseService exerciseService) {
        super(dragAndDropMappingRepository, shortAnswerMappingRepository);
        this.quizExerciseRepository = quizExerciseRepository;
        this.resultRepository = resultRepository;
        this.quizSubmissionRepository = quizSubmissionRepository;
        this.instanceMessageSendService = instanceMessageSendService;
        this.quizStatisticService = quizStatisticService;
        this.quizBatchService = quizBatchService;
        this.exerciseSpecificationService = exerciseSpecificationService;
        this.exerciseService = exerciseService;
    }

    /**
     * adjust existing results if an answer or and question was deleted and recalculate the scores
     *
     * @param quizExercise the changed quizExercise.
     */
    private void updateResultsOnQuizChanges(QuizExercise quizExercise) {
        // change existing results if an answer or and question was deleted
        List<Result> results = resultRepository.findBySubmissionParticipationExerciseIdOrderByCompletionDateAsc(quizExercise.getId());
        log.info("Found {} results to update for quiz re-evaluate", results.size());
        List<QuizSubmission> submissions = new ArrayList<>();
        for (Result result : results) {

            Set<SubmittedAnswer> submittedAnswersToDelete = new HashSet<>();
            QuizSubmission quizSubmission = quizSubmissionRepository.findWithEagerSubmittedAnswersById(result.getSubmission().getId());
            result.setSubmission(quizSubmission);

            for (SubmittedAnswer submittedAnswer : quizSubmission.getSubmittedAnswers()) {
                // Delete all references to question and question-elements if the question was changed
                submittedAnswer.checkAndDeleteReferences(quizExercise);
                if (!quizExercise.getQuizQuestions().contains(submittedAnswer.getQuizQuestion())) {
                    submittedAnswersToDelete.add(submittedAnswer);
                }
            }
            quizSubmission.getSubmittedAnswers().removeAll(submittedAnswersToDelete);

            // recalculate existing score
            quizSubmission.calculateAndUpdateScores(quizExercise.getQuizQuestions());
            // update Successful-Flag in Result
            StudentParticipation studentParticipation = (StudentParticipation) result.getSubmission().getParticipation();
            studentParticipation.setExercise(quizExercise);
            result.evaluateQuizSubmission(quizExercise);

            submissions.add(quizSubmission);
        }
        // save the updated submissions and results
        quizSubmissionRepository.saveAll(submissions);
        resultRepository.saveAll(results);
        log.info("{} results have been updated successfully for quiz re-evaluate", results.size());
    }

    /**
     * @param quizExercise         the changed quiz exercise from the client
     * @param originalQuizExercise the original quiz exercise (with statistics)
     * @param files                the files that were uploaded
     * @return the updated quiz exercise with the changed statistics
     */
    public QuizExercise reEvaluate(QuizExercise quizExercise, QuizExercise originalQuizExercise, @NotNull List<MultipartFile> files) throws IOException {
        quizExercise.undoUnallowedChanges(originalQuizExercise);
        validateQuizExerciseFiles(quizExercise, files, false);

        boolean updateOfResultsAndStatisticsNecessary = quizExercise.checkIfRecalculationIsNecessary(originalQuizExercise);

        // update QuizExercise
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());
        quizExercise.reconnectJSONIgnoreAttributes();
        handleDndQuizFileUpdates(quizExercise, originalQuizExercise, files);

        // adjust existing results if an answer or a question was deleted and recalculate them
        updateResultsOnQuizChanges(quizExercise);

        QuizExercise savedQuizExercise = save(quizExercise);

        if (updateOfResultsAndStatisticsNecessary) {
            // make sure we have all objects available before updating the statistics to avoid lazy / proxy issues
            savedQuizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
            quizStatisticService.recalculateStatistics(savedQuizExercise);
        }
        // fetch the quiz exercise again to make sure the latest changes are included
        return quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(savedQuizExercise.getId());
    }

    /**
     * Reset a QuizExercise to its original state, delete statistics and cleanup the schedule service.
     *
     * @param exerciseId id of the exercise to reset
     */
    public void resetExercise(Long exerciseId) {
        // fetch exercise again to make sure we have an updated version
        QuizExercise quizExercise = quizExerciseRepository.findByIdWithQuestionsAndStatisticsElseThrow(exerciseId);

        // for quizzes, we need to delete the statistics, and we need to reset the quiz to its original state
        quizExercise.setIsOpenForPractice(Boolean.FALSE);
        if (!quizExercise.isExamExercise()) {
            // do not set the release date of exam exercises
            quizExercise.setReleaseDate(ZonedDateTime.now());
        }
        quizExercise.setDueDate(null);
        quizExercise.setQuizBatches(Set.of());

        resetInvalidQuestions(quizExercise);

        QuizExercise savedQuizExercise = save(quizExercise);

        // in case the quiz has not yet started or the quiz is currently running, we have to clean up
        instanceMessageSendService.sendQuizExerciseStartSchedule(savedQuizExercise.getId());

        // clean up the statistics
        quizStatisticService.recalculateStatistics(savedQuizExercise);
    }

    public void cancelScheduledQuiz(Long quizExerciseId) {
        instanceMessageSendService.sendQuizExerciseStartCancel(quizExerciseId);
    }

    /**
     * Update a QuizExercise so that it ends at a specific date and moves the start date of the batches as required.
     * Does not save the quiz.
     *
     * @param quizExercise The quiz to end
     */
    public void endQuiz(QuizExercise quizExercise) {
        quizExercise.setDueDate(ZonedDateTime.now().truncatedTo(ChronoUnit.SECONDS));
        quizExercise.getQuizBatches().forEach(batch -> batch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, batch.getStartTime())));
    }

    /**
     * Search for all quiz exercises fitting a {@link SearchTermPageableSearchDTO search query}. The result is paged,
     * meaning that there is only a predefined portion of the result returned to the user, so that the server doesn't
     * have to send hundreds/thousands of exercises if there are that many in Artemis.
     *
     * @param search         The search query defining the search term and the size of the returned page
     * @param isCourseFilter Whether to search in courses for exercises
     * @param isExamFilter   Whether to search in exams for exercises
     * @param user           The user for whom to fetch all available exercises
     * @return A wrapper object containing a list of all found exercises and the total number of pages
     */
    public SearchResultPageDTO<QuizExercise> getAllOnPageWithSize(final SearchTermPageableSearchDTO<String> search, final Boolean isCourseFilter, final Boolean isExamFilter,
            final User user) {
        if (!isCourseFilter && !isExamFilter) {
            return new SearchResultPageDTO<>(Collections.emptyList(), 0);
        }
        final var pageable = PageUtil.createDefaultPageRequest(search, PageUtil.ColumnMapping.EXERCISE);
        final var searchTerm = search.getSearchTerm();
        Specification<QuizExercise> specification = exerciseSpecificationService.getExerciseSearchSpecification(searchTerm, isCourseFilter, isExamFilter, user, pageable);
        Page<QuizExercise> exercisePage = quizExerciseRepository.findAll(specification, pageable);
        return new SearchResultPageDTO<>(exercisePage.getContent(), exercisePage.getTotalPages());
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the
     * exercise accordingly.
     *
     * @param quizExercise the quiz exercise to create
     * @param files        the provided files
     */
    public void handleDndQuizFileCreation(QuizExercise quizExercise, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizExerciseFiles(quizExercise, nullsafeFiles, true);
        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    saveDndQuestionBackground(dragAndDropQuestion, fileMap, null);
                }
                handleDndQuizDragItemsCreation(dragAndDropQuestion, fileMap);
            }
        }
    }

    private void handleDndQuizDragItemsCreation(DragAndDropQuestion dragAndDropQuestion, Map<String, MultipartFile> fileMap) throws IOException {
        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            if (dragItem.getPictureFilePath() != null) {
                saveDndDragItemPicture(dragItem, fileMap, null);
            }
        }
    }

    /**
     * Verifies that for DragAndDropQuestions all files are present and valid. Saves the files and updates the
     * exercise accordingly.
     * Ignores unchanged paths and removes deleted background images.
     *
     * @param updatedExercise  the updated quiz exercise
     * @param originalExercise the original quiz exercise
     * @param files            the provided files
     */
    public void handleDndQuizFileUpdates(QuizExercise updatedExercise, QuizExercise originalExercise, List<MultipartFile> files) throws IOException {
        List<MultipartFile> nullsafeFiles = files == null ? new ArrayList<>() : files;
        validateQuizExerciseFiles(updatedExercise, nullsafeFiles, false);
        Map<FilePathType, Set<String>> oldPaths = getAllPathsFromDragAndDropQuestionsOfExercise(originalExercise);
        Map<FilePathType, Set<String>> filesToRemove = new HashMap<>(oldPaths);

        Map<String, MultipartFile> fileMap = nullsafeFiles.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, file -> file));

        for (var question : updatedExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                handleDndQuestionUpdate(dragAndDropQuestion, oldPaths, filesToRemove, fileMap, dragAndDropQuestion);
            }
        }

        var allFilesToRemoveMerged = filesToRemove.entrySet().stream()
                .flatMap(entry -> entry.getValue().stream().map(path -> FilePathConverter.fileSystemPathForExternalUri(URI.create(path), entry.getKey()))).filter(Objects::nonNull)
                .toList();

        FileUtil.deleteFiles(allFilesToRemoveMerged);
    }

    private Map<FilePathType, Set<String>> getAllPathsFromDragAndDropQuestionsOfExercise(QuizExercise quizExercise) {
        Map<FilePathType, Set<String>> paths = new HashMap<>();
        paths.put(FilePathType.DRAG_AND_DROP_BACKGROUND, new HashSet<>());
        paths.put(FilePathType.DRAG_ITEM, new HashSet<>());

        for (var question : quizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                if (dragAndDropQuestion.getBackgroundFilePath() != null) {
                    paths.get(FilePathType.DRAG_AND_DROP_BACKGROUND).add(dragAndDropQuestion.getBackgroundFilePath());
                }
                Set<String> dragItemPaths = dragAndDropQuestion.getDragItems().stream().map(DragItem::getPictureFilePath).filter(Objects::nonNull).collect(Collectors.toSet());
                paths.get(FilePathType.DRAG_ITEM).addAll(dragItemPaths);
            }
        }

        return paths;
    }

    private void handleDndQuestionUpdate(DragAndDropQuestion dragAndDropQuestion, Map<FilePathType, Set<String>> oldPaths, Map<FilePathType, Set<String>> filesToRemove,
            Map<String, MultipartFile> fileMap, DragAndDropQuestion questionUpdate) throws IOException {
        String newBackgroundPath = dragAndDropQuestion.getBackgroundFilePath();

        // Don't do anything if the path is null because it's getting removed
        if (newBackgroundPath != null) {
            Set<String> oldBackgroundPaths = oldPaths.get(FilePathType.DRAG_AND_DROP_BACKGROUND);
            if (oldBackgroundPaths.contains(newBackgroundPath)) {
                // Path didn't change
                filesToRemove.get(FilePathType.DRAG_AND_DROP_BACKGROUND).remove(newBackgroundPath);
            }
            else {
                // Path changed and file was provided
                saveDndQuestionBackground(dragAndDropQuestion, fileMap, questionUpdate.getId());
            }
        }

        for (var dragItem : dragAndDropQuestion.getDragItems()) {
            String newDragItemPath = dragItem.getPictureFilePath();
            Set<String> dragItemOldPaths = oldPaths.get(FilePathType.DRAG_ITEM);
            if (newDragItemPath != null && !dragItemOldPaths.contains(newDragItemPath)) {
                // Path changed and file was provided
                saveDndDragItemPicture(dragItem, fileMap, null);
            }
            else if (newDragItemPath != null) {
                filesToRemove.get(FilePathType.DRAG_ITEM).remove(newDragItemPath);
            }
        }
    }

    /**
     * Verifies that the provided files match the provided filenames in the exercise entity.
     *
     * @param quizExercise  the quiz exercise to validate
     * @param providedFiles the provided files to validate
     * @param isCreate      On create all files get validated, on update only changed files get validated
     */
    public void validateQuizExerciseFiles(QuizExercise quizExercise, @NotNull List<MultipartFile> providedFiles, boolean isCreate) {
        long fileCount = providedFiles.size();

        Map<FilePathType, Set<String>> exerciseFilePathsMap = getAllPathsFromDragAndDropQuestionsOfExercise(quizExercise);

        Map<FilePathType, Set<String>> newFilePathsMap = new HashMap<>();

        if (isCreate) {
            newFilePathsMap = new HashMap<>(exerciseFilePathsMap);
        }
        else {
            for (Map.Entry<FilePathType, Set<String>> entry : exerciseFilePathsMap.entrySet()) {
                FilePathType type = entry.getKey();
                Set<String> paths = entry.getValue();
                paths.forEach(FileUtil::sanitizeFilePathByCheckingForInvalidCharactersElseThrow);
                paths.stream().filter(path -> Files.exists(FilePathConverter.fileSystemPathForExternalUri(URI.create(path), type))).forEach(path -> {
                    URI intendedSubPath = type == FilePathType.DRAG_AND_DROP_BACKGROUND ? URI.create(FileUtil.BACKGROUND_FILE_SUBPATH) : URI.create(FileUtil.PICTURE_FILE_SUBPATH);
                    FileUtil.sanitizeByCheckingIfPathStartsWithSubPathElseThrow(URI.create(path), intendedSubPath);
                });

                Set<String> newPaths = paths.stream().filter(filePath -> !Files.exists(FilePathConverter.fileSystemPathForExternalUri(URI.create(filePath), type)))
                        .collect(Collectors.toSet());

                if (!newPaths.isEmpty()) {
                    newFilePathsMap.put(type, newPaths);
                }
            }
        }

        int totalNewPathsCount = newFilePathsMap.values().stream().mapToInt(Set::size).sum();

        if (totalNewPathsCount != fileCount) {
            throw new BadRequestAlertException("Number of files does not match number of new drag items and " + "backgrounds", ENTITY_NAME, null);
        }

        Set<String> allNewFilePaths = newFilePathsMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet());

        Set<String> providedFileNames = providedFiles.stream().map(MultipartFile::getOriginalFilename).collect(Collectors.toSet());

        if (!allNewFilePaths.equals(providedFileNames)) {
            throw new BadRequestAlertException("File names do not match new drag item and background file names", ENTITY_NAME, null);
        }
    }

    /**
     * Saves the background image of a drag and drop question without saving the question itself
     *
     * @param question   the drag and drop question
     * @param files      all provided files
     * @param questionId the id of the question, null on creation
     */
    public void saveDndQuestionBackground(DragAndDropQuestion question, Map<String, MultipartFile> files, @Nullable Long questionId) throws IOException {
        MultipartFile file = files.get(question.getBackgroundFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + question.getBackgroundFilePath() + " was not provided", ENTITY_NAME, null);
        }

        question.setBackgroundFilePath(
                saveDragAndDropImage(FilePathConverter.getDragAndDropBackgroundFilePath(), file, FilePathType.DRAG_AND_DROP_BACKGROUND, questionId).toString());
    }

    /**
     * Saves the picture of a drag item without saving the drag item itself
     *
     * @param dragItem the drag item
     * @param files    all provided files
     * @param entityId The entity id connected to this file, can be question id for background, or the drag item id
     *                     for drag item images
     */
    public void saveDndDragItemPicture(DragItem dragItem, Map<String, MultipartFile> files, @Nullable Long entityId) throws IOException {
        MultipartFile file = files.get(dragItem.getPictureFilePath());
        if (file == null) {
            // Should not be reached as the file is validated before
            throw new BadRequestAlertException("The file " + dragItem.getPictureFilePath() + " was not provided", ENTITY_NAME, null);
        }

        dragItem.setPictureFilePath(saveDragAndDropImage(FilePathConverter.getDragItemFilePath(), file, FilePathType.DRAG_ITEM, entityId).toString());
    }

    /**
     * Saves an image for an DragAndDropQuestion. Either a background image or a drag item image.
     *
     * @return the public path of the saved image
     */
    private URI saveDragAndDropImage(Path basePath, MultipartFile file, FilePathType filePathType, @Nullable Long entityId) throws IOException {
        String sanitizedFilename = FileUtil.checkAndSanitizeFilename(file.getOriginalFilename());
        Path savePath = basePath.resolve(FileUtil.generateFilename("dnd_image_", sanitizedFilename, true));
        FileUtils.copyToFile(file.getInputStream(), savePath.toFile());
        return FilePathConverter.externalUriForFileSystemPath(savePath, filePathType, entityId);
    }

    /**
     * Reset the invalid status of questions of given quizExercise to false
     *
     * @param quizExercise The quiz exercise which questions to be reset
     */
    private void resetInvalidQuestions(QuizExercise quizExercise) {
        for (QuizQuestion question : quizExercise.getQuizQuestions()) {
            question.setInvalid(false);
        }
    }

    @Override
    public QuizExercise save(QuizExercise quizExercise) {
        quizExercise.setMaxPoints(quizExercise.getOverallQuizPoints());

        // create a quizPointStatistic if it does not yet exist
        if (quizExercise.getQuizPointStatistic() == null) {
            QuizPointStatistic quizPointStatistic = new QuizPointStatistic();
            quizExercise.setQuizPointStatistic(quizPointStatistic);
            quizPointStatistic.setQuiz(quizExercise);
        }

        // make sure the pointers in the statistics are correct
        quizExercise.recalculatePointCounters();

        QuizExercise savedQuizExercise = exerciseService.saveWithCompetencyLinks(quizExercise, super::save);

        if (savedQuizExercise.isCourseExercise()) {
            // only schedule quizzes for course exercises, not for exam exercises
            instanceMessageSendService.sendQuizExerciseStartSchedule(savedQuizExercise.getId());
        }

        return savedQuizExercise;
    }

    @Override
    protected QuizExercise saveAndFlush(QuizExercise quizExercise) {
        if (quizExercise.getQuizBatches() != null) {
            for (QuizBatch quizBatch : quizExercise.getQuizBatches()) {
                quizBatch.setQuizExercise(quizExercise);
                if (quizExercise.getQuizMode() == QuizMode.SYNCHRONIZED) {
                    if (quizBatch.getStartTime() != null) {
                        quizExercise.setDueDate(quizBatch.getStartTime().plusSeconds(quizExercise.getDuration() + Constants.QUIZ_GRACE_PERIOD_IN_SECONDS));
                    }
                }
                else {
                    quizBatch.setStartTime(quizBatchService.quizBatchStartDate(quizExercise, quizBatch.getStartTime()));
                }
            }
        }

        // Note: save will automatically remove deleted questions from the exercise and deleted answer options from
        // the questions
        // and delete the now orphaned entries from the database
        log.debug("Save quiz exercise to database: {}", quizExercise);
        return quizExerciseRepository.saveAndFlush(quizExercise);
    }

    /**
     * @param newQuizExercise the newly created quiz exercise, after importing basis of imported exercise
     * @param files           the new files to be added to the newQuizExercise which do not have a previous path and
     *                            need to be saved in the server
     * @return the new exercise with the updated file paths which have been created and saved
     * @throws IOException throws IO exception if corrupted files
     */
    public QuizExercise uploadNewFilesToNewImportedQuiz(QuizExercise newQuizExercise, List<MultipartFile> files) throws IOException {
        Map<String, MultipartFile> fileMap = files.stream().collect(Collectors.toMap(MultipartFile::getOriginalFilename, Function.identity()));
        for (var question : newQuizExercise.getQuizQuestions()) {
            if (question instanceof DragAndDropQuestion dragAndDropQuestion) {
                URI publicPathUri = URI.create(dragAndDropQuestion.getBackgroundFilePath());
                if (!Files.exists(FilePathConverter.fileSystemPathForExternalUri(publicPathUri, FilePathType.DRAG_AND_DROP_BACKGROUND))) {
                    saveDndQuestionBackground(dragAndDropQuestion, fileMap, dragAndDropQuestion.getId());
                }
                for (DragItem dragItem : dragAndDropQuestion.getDragItems()) {
                    if (dragItem.getPictureFilePath() != null
                            && !Files.exists(FilePathConverter.fileSystemPathForExternalUri(URI.create(dragItem.getPictureFilePath()), FilePathType.DRAG_ITEM))) {
                        saveDndDragItemPicture(dragItem, fileMap, dragItem.getId());
                    }
                }
            }
        }
        return newQuizExercise;
    }

    /**
     * Retrieves a {@link QuizExerciseCalendarEventDTO} for each {@link QuizExercise} associated to the given courseId.
     * Each DTO encapsulates the quizMode, title, releaseDate, dueDate, quizBatches and duration of the respective QuizExercise.
     * <p>
     * The method then derives a set of {@link CalendarEventDTO}s from the DTOs. Whether events are included in the result
     * depends on the quizMode of the given exercise and whether the logged-in user is a student of the {@link Course}.
     *
     * @param courseId      the ID of the course
     * @param userIsStudent indicates whether the logged-in user is a student of the course
     * @return the set of results
     */
    public Set<CalendarEventDTO> getCalendarEventDTOsFromQuizExercises(long courseId, boolean userIsStudent) {
        Set<QuizExerciseCalendarEventDTO> daos = quizExerciseRepository.getQuizExerciseCalendarEventDAOsForCourseId(courseId);
        return daos.stream().flatMap(dao -> deriveCalendarEventDTOs(dao, userIsStudent).stream()).collect(Collectors.toSet());
    }

    private Set<CalendarEventDTO> deriveCalendarEventDTOs(QuizExerciseCalendarEventDTO dao, boolean userIsStudent) {
        if (dao.quizMode() == QuizMode.SYNCHRONIZED) {
            return deriveCalendarEventDTOForSynchronizedQuizExercise(dao, userIsStudent).map(Set::of).orElseGet(Collections::emptySet);
        }
        else {
            return deriveCalendarEventDTOsForIndividualAndBatchedQuizExercises(dao, userIsStudent);
        }
    }

    /**
     * Derives one event represents the working time period of the {@link QuizExercise} represented by the given DTO.
     * <p>
     * The events are only derived given that either the exercise is visible to students or the logged-in user is a course
     * staff member (either tutor, editor ot student of the {@link Course} associated to the exam).
     * <p>
     * Context: <br>
     * The startDate and dueDate properties of {@link QuizExercise}s in {@code QuizMode.SYNCHRONIZED} are always null. Instead, such quizzes have exactly one {@link QuizBatch}
     * for which the startTime property is set. The end of the quiz can be calculated by adding the duration property of the exercise to the startTime of the batch.
     *
     * @param dto           the DAO from which to derive the event
     * @param userIsStudent indicates whether the logged-in user is a student of the course related to the exercise
     * @return one event representing the working time period of the exercise
     */
    private Optional<CalendarEventDTO> deriveCalendarEventDTOForSynchronizedQuizExercise(QuizExerciseCalendarEventDTO dto, boolean userIsStudent) {
        if (userIsStudent && dto.releaseDate() != null && ZonedDateTime.now().isBefore(dto.releaseDate())) {
            return Optional.empty();
        }

        QuizBatch synchronizedBatch = dto.quizBatch();
        if (synchronizedBatch == null || synchronizedBatch.getStartTime() == null || dto.duration() == null) {
            return Optional.empty();
        }

        return Optional.of(new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.START_AND_END_DATE, dto.title(), synchronizedBatch.getStartTime(),
                synchronizedBatch.getStartTime().plusSeconds(dto.duration()), null, null));
    }

    /**
     * Derives one event for start/end of the duration during which the user can choose to participate in the {@link QuizExercise} represented by the given DAO.
     * <p>
     * The events are only derived given that either the exercise is visible to students or the logged-in user is a course
     * staff member (either tutor, editor ot student of the {@link Course} associated to the exam).
     * <p>
     * Context: <br>
     * For {@link QuizExercise}s in {@code QuizMode.INDIVIDUAL} the user can decide when to start the quiz himself.
     * For {@link QuizExercise}s in {@code QuizMode.BATCHED} the user can join a quiz by using a password. The instructor can then start the quiz manually.
     * For both modes, the period in which the quiz can be held may be constrained by releaseDate (defining a start of the period) or dueDate (defining an end of the period).
     * The dueDate and startDate can be set independent of each other.
     *
     * @param dao           the DAO from which to derive the events
     * @param userIsStudent indicates whether the logged-in user is a student of the course associated to the quizExercise
     * @return the derived events
     */
    private Set<CalendarEventDTO> deriveCalendarEventDTOsForIndividualAndBatchedQuizExercises(QuizExerciseCalendarEventDTO dao, boolean userIsStudent) {
        Set<CalendarEventDTO> events = new HashSet<>();
        boolean userIsCourseStaff = !userIsStudent;
        if (userIsCourseStaff || dao.releaseDate() == null || dao.releaseDate().isBefore(now())) {
            if (dao.releaseDate() != null) {
                events.add(new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.RELEASE_DATE, dao.title(), dao.releaseDate(), null, null, null));
            }
            if (dao.dueDate() != null) {
                events.add(new CalendarEventDTO(CalendarEventRelatedEntity.QUIZ_EXERCISE, CalendarEventSemantics.DUE_DATE, dao.title(), dao.dueDate(), null, null, null));
            }
        }
        return events;
    }
}
