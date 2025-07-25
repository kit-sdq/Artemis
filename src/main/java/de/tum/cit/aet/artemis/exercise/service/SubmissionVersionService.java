package de.tum.cit.aet.artemis.exercise.service;

import static de.tum.cit.aet.artemis.core.config.Constants.PROFILE_CORE;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.core.repository.UserRepository;
import de.tum.cit.aet.artemis.exercise.domain.Submission;
import de.tum.cit.aet.artemis.exercise.domain.SubmissionVersion;
import de.tum.cit.aet.artemis.exercise.repository.SubmissionVersionRepository;
import de.tum.cit.aet.artemis.modeling.domain.ModelingSubmission;
import de.tum.cit.aet.artemis.quiz.domain.QuizSubmission;
import de.tum.cit.aet.artemis.text.domain.TextSubmission;

@Profile(PROFILE_CORE)
@Lazy
@Service
public class SubmissionVersionService {

    private static final Logger log = LoggerFactory.getLogger(SubmissionVersionService.class);

    protected final SubmissionVersionRepository submissionVersionRepository;

    protected final UserRepository userRepository;

    private final ObjectMapper objectMapper;

    public SubmissionVersionService(SubmissionVersionRepository submissionVersionRepository, UserRepository userRepository, ObjectMapper objectMapper) {
        this.submissionVersionRepository = submissionVersionRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Saves a version for the given team submission to track its current content and author
     * <p>
     * If the last version for this submission was made by the same user, update this version.
     * Otherwise, create a new version. This drastically reduces the number of versions that need to be created.
     *
     * @param submission Submission for which to save a version
     * @param user       Author of the submission update
     * @return created/updated submission version
     */
    public SubmissionVersion saveVersionForTeam(Submission submission, User user) {
        return submissionVersionRepository.findLatestVersion(submission.getId()).map(latestVersion -> {
            if (latestVersion.getAuthor().equals(user)) {
                return updateExistingVersion(latestVersion, submission);
            }
            else {
                return saveVersionForIndividual(submission, user);
            }
        }).orElseGet(() -> saveVersionForIndividual(submission, user));
    }

    /**
     * Saves a version for the given individual submission to track its content
     *
     * @param submission Submission for which to save a version
     * @param user       Author of the submission update
     * @return created/updated submission version
     */
    public SubmissionVersion saveVersionForIndividual(Submission submission, User user) {
        SubmissionVersion version = new SubmissionVersion();
        version.setAuthor(user);
        version.setSubmission(submission);
        version.setContent(getSubmissionContent(submission));
        return submissionVersionRepository.save(version);
    }

    private SubmissionVersion updateExistingVersion(SubmissionVersion version, Submission submission) {
        version.setContent(getSubmissionContent(submission));
        return submissionVersionRepository.save(version);
    }

    private String getSubmissionContent(Submission submission) {
        switch (submission) {
            case ModelingSubmission modelingSubmission -> {
                return "Model: " + modelingSubmission.getModel() + "; Explanation: " + modelingSubmission.getExplanationText();
            }
            case TextSubmission textSubmission -> {
                return textSubmission.getText();
            }
            case QuizSubmission quizSubmission -> {
                try {
                    // TODO: it might be nice to remove some question parameters (i.e. SubmittedAnswer -> QuizQuestion) to reduce the json size as those are not really necessary,
                    // however directly manipulating the object is dangerous because it will be returned to the client.
                    return objectMapper.writeValueAsString(quizSubmission.getSubmittedAnswers());
                }
                catch (JsonProcessingException e) {
                    log.error("Error when writing quiz submission {} to json value. Will fall back to string representation", submission, e);
                    return submission.toString();
                }
            }
            default -> throw new IllegalArgumentException("Versioning for this submission type not supported: " + submission.getType());
        }
    }
}
