package de.tum.cit.aet.artemis.plagiarism.service;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.domain.PlagiarismResult;
import de.tum.cit.aet.artemis.plagiarism.exception.ProgrammingLanguageNotSupportedForPlagiarismDetectionException;
import de.tum.cit.aet.artemis.plagiarism.repository.PlagiarismResultRepository;
import de.tum.cit.aet.artemis.programming.domain.ProgrammingExercise;
import de.tum.cit.aet.artemis.programming.service.ProgrammingLanguageFeatureService;
import de.tum.cit.aet.artemis.text.domain.TextExercise;

/**
 * Service for triggering plagiarism checks.
 */
@Conditional(PlagiarismEnabled.class)
@Lazy
@Service
public class PlagiarismDetectionService {

    private static final Logger log = LoggerFactory.getLogger(PlagiarismDetectionService.class);

    private final TextPlagiarismDetectionService textPlagiarismDetectionService;

    private final Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService;

    private final ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService;

    private final PlagiarismResultRepository plagiarismResultRepository;

    @Value("${artemis.plagiarism-checks.plagiarism-results-limit:100}")
    private int plagiarismResultsLimit;

    public PlagiarismDetectionService(TextPlagiarismDetectionService textPlagiarismDetectionService, Optional<ProgrammingLanguageFeatureService> programmingLanguageFeatureService,
            ProgrammingPlagiarismDetectionService programmingPlagiarismDetectionService, PlagiarismResultRepository plagiarismResultRepository) {
        this.textPlagiarismDetectionService = textPlagiarismDetectionService;
        this.programmingLanguageFeatureService = programmingLanguageFeatureService;
        this.programmingPlagiarismDetectionService = programmingPlagiarismDetectionService;
        this.plagiarismResultRepository = plagiarismResultRepository;
    }

    /**
     * Check plagiarism in given text exercise
     *
     * @param exercise exercise to check plagiarism
     * @return result of plagiarism checks
     */
    public PlagiarismResult checkTextExercise(TextExercise exercise) {
        var plagiarismResult = textPlagiarismDetectionService.checkPlagiarism(exercise, exercise.getPlagiarismDetectionConfig().getSimilarityThreshold(),
                exercise.getPlagiarismDetectionConfig().getMinimumScore(), exercise.getPlagiarismDetectionConfig().getMinimumSize());
        log.info("Finished textPlagiarismDetectionService.checkPlagiarism for exercise {} with {} comparisons,", exercise.getId(), plagiarismResult.getComparisons().size());

        trimAndSavePlagiarismResult(plagiarismResult);
        return plagiarismResult;
    }

    /**
     * Check plagiarism in given programing exercise
     *
     * @param exercise exercise to check plagiarism
     * @return result of plagiarism checks
     */
    public PlagiarismResult checkProgrammingExercise(ProgrammingExercise exercise) throws IOException, ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        checkProgrammingLanguageSupport(exercise);

        var plagiarismResult = programmingPlagiarismDetectionService.checkPlagiarism(exercise.getId(), exercise.getPlagiarismDetectionConfig().getSimilarityThreshold(),
                exercise.getPlagiarismDetectionConfig().getMinimumScore(), exercise.getPlagiarismDetectionConfig().getMinimumSize());
        log.info("Finished programmingExerciseExportService.checkPlagiarism call for {} comparisons", plagiarismResult.getComparisons().size());

        // make sure that participation is included in the exercise
        plagiarismResult.setExercise(exercise);
        trimAndSavePlagiarismResult(plagiarismResult);
        return plagiarismResult;
    }

    /**
     * Check plagiarism in given programing exercise and outputs a Jplag report
     *
     * @param exercise exercise to check plagiarism
     * @return Jplag report of plagiarism checks
     */
    public File checkProgrammingExerciseWithJplagReport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        checkProgrammingLanguageSupport(exercise);
        return programmingPlagiarismDetectionService.checkPlagiarismWithJPlagReport(exercise.getId(), exercise.getPlagiarismDetectionConfig().getSimilarityThreshold(),
                exercise.getPlagiarismDetectionConfig().getMinimumScore(), exercise.getPlagiarismDetectionConfig().getMinimumSize());
    }

    private void trimAndSavePlagiarismResult(PlagiarismResult plagiarismResult) {
        // Limit the amount temporarily because of database issues
        plagiarismResult.sortAndLimit(plagiarismResultsLimit);
        plagiarismResultRepository.savePlagiarismResultAndRemovePrevious(plagiarismResult);

        plagiarismResultRepository.prepareResultForClient(plagiarismResult);
    }

    private void checkProgrammingLanguageSupport(ProgrammingExercise exercise) throws ProgrammingLanguageNotSupportedForPlagiarismDetectionException {
        var language = exercise.getProgrammingLanguage();
        var programmingLanguageFeature = programmingLanguageFeatureService.orElseThrow().getProgrammingLanguageFeatures(language);
        if (!programmingLanguageFeature.plagiarismCheckSupported()) {
            throw new ProgrammingLanguageNotSupportedForPlagiarismDetectionException(language);
        }
    }
}
