package de.tum.cit.aet.artemis.plagiarism.api;

import java.util.Set;

import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;

import de.tum.cit.aet.artemis.communication.domain.AnswerPost;
import de.tum.cit.aet.artemis.communication.domain.ConversationNotificationRecipientSummary;
import de.tum.cit.aet.artemis.communication.domain.Post;
import de.tum.cit.aet.artemis.communication.dto.PostDTO;
import de.tum.cit.aet.artemis.core.domain.Course;
import de.tum.cit.aet.artemis.core.domain.User;
import de.tum.cit.aet.artemis.plagiarism.config.PlagiarismEnabled;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismAnswerPostService;
import de.tum.cit.aet.artemis.plagiarism.service.PlagiarismPostService;

@Conditional(PlagiarismEnabled.class)
@Controller
@Lazy
public class PlagiarismPostApi extends AbstractPlagiarismApi {

    private final PlagiarismPostService plagiarismPostService;

    private final PlagiarismAnswerPostService plagiarismAnswerPostService;

    public PlagiarismPostApi(PlagiarismPostService plagiarismPostService, PlagiarismAnswerPostService plagiarismAnswerPostService) {
        this.plagiarismPostService = plagiarismPostService;
        this.plagiarismAnswerPostService = plagiarismAnswerPostService;
    }

    public AnswerPost findAnswerPostOrAnswerMessageById(Long messageId) {
        return plagiarismAnswerPostService.findAnswerPostOrAnswerMessageById(messageId);
    }

    public Post findPostOrMessagePostById(Long postOrMessageId) {
        return plagiarismPostService.findPostOrMessagePostById(postOrMessageId);
    }

    public void preparePostForBroadcast(Post post) {
        plagiarismAnswerPostService.preparePostForBroadcast(post);
    }

    public void preparePostAndBroadcast(AnswerPost updatedAnswerPost, Course course) {
        plagiarismAnswerPostService.preparePostAndBroadcast(updatedAnswerPost, course);
    }

    public void broadcastForPost(PostDTO postDTO, Long courseId, Set<ConversationNotificationRecipientSummary> recipients) {
        plagiarismPostService.broadcastForPost(postDTO, courseId, recipients);
    }

    public void preCheckUserAndCourseForCommunicationOrMessaging(User user, Course course) {
        plagiarismPostService.preCheckUserAndCourseForCommunicationOrMessaging(user, course);
    }
}
