package de.tum.cit.aet.artemis.quiz.dto.question;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import de.tum.cit.aet.artemis.quiz.domain.MultipleChoiceQuestion;
import de.tum.cit.aet.artemis.quiz.dto.AnswerOptionWithSolutionDTO;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record MultipleChoiceQuestionWithSolutionDTO(List<AnswerOptionWithSolutionDTO> answerOptions, boolean singleChoice) {

    public static MultipleChoiceQuestionWithSolutionDTO of(MultipleChoiceQuestion multipleChoiceQuestion) {
        return new MultipleChoiceQuestionWithSolutionDTO(multipleChoiceQuestion.getAnswerOptions().stream().map(AnswerOptionWithSolutionDTO::of).toList(),
                multipleChoiceQuestion.isSingleChoice());
    }

}
