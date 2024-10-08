package de.tum.cit.aet.artemis.programming.service.hestia.behavioral.knowledgesource;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import de.tum.cit.aet.artemis.programming.domain.hestia.ProgrammingExerciseGitDiffEntry;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.BehavioralBlackboard;
import de.tum.cit.aet.artemis.programming.service.hestia.behavioral.GroupedFile;

/**
 * For each {@link GroupedFile}:
 * Extracts the lines that were changed in the file (of the GroupedFile) from the
 * {@link ProgrammingExerciseGitDiffEntry}s
 */
public class ExtractChangedLines extends BehavioralKnowledgeSource {

    public ExtractChangedLines(BehavioralBlackboard blackboard) {
        super(blackboard);
    }

    @Override
    public boolean executeCondition() {
        return blackboard.getGroupedFiles() != null && blackboard.getGroupedFiles().stream().anyMatch(groupedFile -> groupedFile.getChangedLines() == null);
    }

    @Override
    public boolean executeAction() {
        for (GroupedFile groupedFile : blackboard.getGroupedFiles()) {
            groupedFile.setChangedLines(groupedFile.getGitDiffEntries().stream()
                    .flatMapToInt(gitDiffEntry -> IntStream.range(gitDiffEntry.getStartLine(), gitDiffEntry.getStartLine() + gitDiffEntry.getLineCount())).boxed()
                    .collect(Collectors.toSet()));
        }
        return !blackboard.getGroupedFiles().stream().allMatch(groupedFile -> groupedFile.getChangedLines().isEmpty());
    }
}
