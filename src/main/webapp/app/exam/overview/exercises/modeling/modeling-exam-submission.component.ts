import { ChangeDetectionStrategy, Component, OnInit, inject, input, output, viewChild } from '@angular/core';
import { UMLModel } from '@ls1intum/apollon';
import dayjs from 'dayjs/esm';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { SafeHtml } from '@angular/platform-browser';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { ExerciseSaveButtonComponent } from '../exercise-save-button/exercise-save-button.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamExerciseUpdateHighlighterComponent } from '../exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FullscreenComponent } from 'app/modeling/shared/fullscreen/fullscreen.component';

@Component({
    selector: 'jhi-modeling-submission-exam',
    templateUrl: './modeling-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: ModelingExamSubmissionComponent }],
    styleUrls: ['./modeling-exam-submission.component.scss'],
    // change deactivation must be triggered manually
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        TranslateDirective,
        IncludedInScoreBadgeComponent,
        ExerciseSaveButtonComponent,
        ResizeableContainerComponent,
        FullscreenComponent,
        ModelingEditorComponent,
        FaIconComponent,
        ExamExerciseUpdateHighlighterComponent,
        ArtemisTranslatePipe,
    ],
})
export class ModelingExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    exerciseType = ExerciseType.MODELING;

    private artemisMarkdown = inject(ArtemisMarkdownService);

    modelingEditor = viewChild.required(ModelingEditorComponent);

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    studentSubmission = input.required<ModelingSubmission>();
    problemStatementHtml: SafeHtml;

    exercise = input.required<ModelingExercise>();
    umlModel: UMLModel; // input model for Apollon+

    // explicitly needed to track if submission.isSynced is changed, otherwise component
    // does not update the state due to onPush strategy
    isSubmissionSynced = input<boolean>();
    saveCurrentExercise = output<void>();

    explanationText: string; // current explanation text

    readonly IncludedInOverallScore = IncludedInOverallScore;

    // Icons
    protected readonly faListAlt = faListAlt;

    ngOnInit(): void {
        // show submission answers in UI
        this.problemStatementHtml = this.artemisMarkdown.safeHtmlForMarkdown(this.exercise()?.problemStatement);
        this.updateViewFromSubmission();
    }

    /**
     * Updates the problem statement html of the currently loaded modeling exercise which is part of the user's student exam.
     * @param newProblemStatementHtml is the updated problem statement html that should be displayed to the user.
     */
    updateProblemStatement(newProblemStatementHtml: SafeHtml): void {
        this.problemStatementHtml = newProblemStatementHtml;
        this.changeDetectorReference.detectChanges();
    }

    getSubmission(): Submission {
        return this.studentSubmission();
    }

    getExerciseId(): number | undefined {
        return this.exercise().id;
    }

    getExercise(): Exercise {
        return this.exercise();
    }

    updateViewFromSubmission(): void {
        if (this.studentSubmission()) {
            if (this.studentSubmission()!.model) {
                // Updates the Apollon editor model state (view) with the latest modeling submission
                this.umlModel = JSON.parse(this.studentSubmission()!.model!);
            }
            // Updates explanation text with the latest submission
            this.explanationText = this.studentSubmission()!.explanationText ?? '';
        }
    }

    /**
     * Updates the model of the submission with the current Apollon editor model state (view)
     * Updates the explanation text of the submission with the current explanation
     */
    public updateSubmissionFromView(): void {
        if (!this.modelingEditor() || !this.modelingEditor().getCurrentModel()) {
            return;
        }
        const currentApollonModel = this.modelingEditor().getCurrentModel();
        const diagramJson = JSON.stringify(currentApollonModel);

        if (this.studentSubmission()) {
            if (diagramJson) {
                this.studentSubmission()!.model = diagramJson;
            }
            this.studentSubmission()!.explanationText = this.explanationText;
        }
    }

    /**
     * Checks whether there are pending changes in the current model. Returns true if there are unsaved changes (i.e. the submission is NOT synced), false otherwise.
     */
    public hasUnsavedChanges(): boolean {
        return !this.studentSubmission()!.isSynced!;
    }

    /**
     * The exercise is still active if it's due date hasn't passed yet.
     */
    get isActive(): boolean {
        return this.exercise() && (!this.exercise().dueDate || dayjs(this.exercise().dueDate).isSameOrAfter(dayjs()));
    }

    modelChanged(_model: UMLModel) {
        this.studentSubmission()!.isSynced = false;
    }

    // Changes isSynced to false and updates explanation text
    explanationChanged(explanation: string) {
        this.studentSubmission()!.isSynced = false;
        this.explanationText = explanation;
    }

    async setSubmissionVersion(submission: SubmissionVersion): Promise<void> {
        this.submissionVersion = submission;
        await this.updateViewFromSubmissionVersion();
    }

    /**
     * Updates the model and explanation text with the latest submission version.
     * It extracts the model and explanation text from the submission version and updates the view.
     */
    private async updateViewFromSubmissionVersion() {
        if (this.submissionVersion?.content) {
            // we need these string operations because we store the string in the database as concatenation of Model: <model>; Explanation: <explanation>
            // and need to remove the content that was added before the string is saved to the db to get valid JSON
            let model = this.submissionVersion.content.substring(0, this.submissionVersion.content.indexOf('; Explanation:'));
            // if we do not wait here for apollon, the redux store might be undefined
            await this.modelingEditor()!.apollonEditor!.nextRender;
            model = model.replace('Model: ', '');
            // updates the Apollon editor model state (view) with the latest modeling submission
            this.umlModel = JSON.parse(model);
            // same as above regarding the string operations
            const numberOfCharactersToSkip = 13; // Explanation:  is 13 characters long
            this.explanationText = this.submissionVersion.content.substring(this.submissionVersion.content.indexOf('Explanation:') + numberOfCharactersToSkip) ?? '';

            // if we do not call this, apollon doesn't show the updated model
            this.changeDetectorReference.detectChanges();
        }
    }

    /**
     * Trigger save action in exam participation component
     */
    notifyTriggerSave() {
        this.saveCurrentExercise.emit();
    }
}
