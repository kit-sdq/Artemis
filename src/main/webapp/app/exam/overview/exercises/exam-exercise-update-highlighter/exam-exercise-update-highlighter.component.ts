import { Component, OnDestroy, OnInit, inject, input, output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import diff from 'html-diff-ts';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-exam-exercise-update-highlighter',
    templateUrl: './exam-exercise-update-highlighter.component.html',
    styleUrls: ['./exam-exercise-update-highlighter.component.scss'],
    imports: [ArtemisTranslatePipe],
})
export class ExamExerciseUpdateHighlighterComponent implements OnInit, OnDestroy {
    private examExerciseUpdateService = inject(ExamExerciseUpdateService);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    subscriptionToLiveExamExerciseUpdates: Subscription;
    themeSubscription: Subscription;
    updatedProblemStatementHTML: SafeHtml;
    updatedProblemStatementWithHighlightedDifferencesHTML: SafeHtml;
    outdatedProblemStatement: string;
    updatedProblemStatement: string;
    showHighlightedDifferences = true;
    isHidden = true;
    exercise = input.required<Exercise>();

    problemStatementUpdateEvent = output<SafeHtml>();

    ngOnInit(): void {
        this.subscriptionToLiveExamExerciseUpdates = this.examExerciseUpdateService.currentExerciseIdAndProblemStatement.subscribe((update) => {
            if (update) {
                this.updateExerciseProblemStatementById(update.exerciseId, update.problemStatement);
            }
        });
    }

    ngOnDestroy(): void {
        this.subscriptionToLiveExamExerciseUpdates?.unsubscribe();
        this.themeSubscription?.unsubscribe();
    }

    /**
     * Switches the view between the new(updated) problem statement without the difference
     * with the view showing the difference between the new and old problem statement and vice versa.
     */
    toggleHighlightedProblemStatement(event: MouseEvent): void {
        // prevents the jhi-resizeable-container from collapsing the right panel on a button click
        event.stopPropagation();
        let problemStatementToEmit: SafeHtml;
        if (this.showHighlightedDifferences) {
            problemStatementToEmit = this.updatedProblemStatementHTML;
        } else {
            problemStatementToEmit = this.updatedProblemStatementWithHighlightedDifferencesHTML;
        }
        this.showHighlightedDifferences = !this.showHighlightedDifferences;
        this.problemStatementUpdateEvent.emit(problemStatementToEmit);
    }

    /**
     * Updates the problem statement of the provided exercises based on its id.
     * Also calls the method to highlight the differences between the old and new problem statement.
     * @param exerciseId is the id of the exercise which problem statement should be updated.
     * @param updatedProblemStatement is the new problem statement that should replace the old one.
     */
    updateExerciseProblemStatementById(exerciseId: number, updatedProblemStatement: string) {
        if (updatedProblemStatement != undefined && exerciseId === this.exercise().id) {
            this.outdatedProblemStatement = this.exercise().problemStatement!;
            this.updatedProblemStatement = updatedProblemStatement;
            this.exercise().problemStatement = updatedProblemStatement;
            this.showHighlightedDifferences = true;
            // Highlighting of the changes in the problem statement of a programming exercise id handled
            // in ProgrammingExerciseInstructionComponent
            if (this.exercise().type !== ExerciseType.PROGRAMMING) {
                this.highlightProblemStatementDifferences();
            }
            this.isHidden = false;
            this.problemStatementUpdateEvent.emit(this.updatedProblemStatementWithHighlightedDifferencesHTML);
        }
    }

    /**
     * Computes the difference between the old and new (updated) problem statement and displays this difference.
     */
    highlightProblemStatementDifferences() {
        const outdatedProblemStatementHTML = htmlForMarkdown(this.outdatedProblemStatement);
        const updatedProblemStatementHTML = htmlForMarkdown(this.updatedProblemStatement);
        this.updatedProblemStatementHTML = this.artemisMarkdown.safeHtmlForMarkdown(this.updatedProblemStatement);
        this.updatedProblemStatementWithHighlightedDifferencesHTML = this.artemisMarkdown.safeHtmlForMarkdown(diff(outdatedProblemStatementHTML, updatedProblemStatementHTML));
    }
}
