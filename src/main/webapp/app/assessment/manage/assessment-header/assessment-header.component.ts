import { Component, EventEmitter, HostListener, Input, Output, inject } from '@angular/core';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextAssessmentAnalytics } from 'app/text/manage/assess/analytics/text-assessment-analytics.service';
import { TextAssessmentEventType } from 'app/text/shared/entities/text-assesment-event.model';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TranslateService } from '@ngx-translate/core';
import { faSave, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { faSquareCaretRight } from '@fortawesome/free-regular-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbAlert, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AssessmentWarningComponent } from '../assessment-warning/assessment-warning.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

/**
 * The <jhi-assessment-header> component is used in the shared assessment layout.
 * It displays a header bar above the assessment editor with information of locking, as well as offering save/submit/etc buttons.
 * This guarantees a unified look and feel for both interfaces.
 * Depending Components need to perform actions based on the save/submit/cancel/nextSubmission/navigateBack outputs.
 */
@Component({
    selector: 'jhi-assessment-header',
    templateUrl: './assessment-header.component.html',
    styleUrls: ['./assessment-header.component.scss'],
    imports: [TranslateDirective, NgbAlert, NgbTooltip, FaIconComponent, RouterLink, AssessmentWarningComponent, ArtemisTranslatePipe],
})
export class AssessmentHeaderComponent {
    textAssessmentAnalytics = inject(TextAssessmentAnalytics);
    protected route = inject(ActivatedRoute);
    private translateService = inject(TranslateService);

    @Input() isLoading: boolean;
    @Input() saveBusy: boolean;
    @Input() submitBusy: boolean;
    @Input() cancelBusy: boolean;
    @Input() nextSubmissionBusy: boolean;
    @Input() correctionRound = 0; // correctionRound defaults to 0

    @Input() isTeamMode: boolean;
    @Input() isAssessor: boolean;
    @Input() isTestRun = false;
    @Input() exerciseDashboardLink: string[];
    @Input() canOverride: boolean;

    @Input() exercise?: Exercise;
    @Input() result?: Result;
    @Input() hasComplaint = false;
    @Input() hasMoreFeedbackRequest = false;
    @Input() complaintHandled = false;
    @Input() complaintType?: ComplaintType;
    @Input() assessmentsAreValid: boolean;
    @Input() hasAssessmentDueDatePassed: boolean;
    @Input() isProgrammingExercise = false; // remove once diff view activated for programming exercises

    @Output() save = new EventEmitter<void>();
    @Output() onSubmit = new EventEmitter<void>();
    @Output() onCancel = new EventEmitter<void>();
    @Output() nextSubmission = new EventEmitter<void>();
    @Output() highlightDifferencesChange = new EventEmitter<boolean>();
    @Output() useAsExampleSubmission = new EventEmitter<void>();

    private _highlightDifferences: boolean;
    readonly ExerciseType = ExerciseType;
    readonly ComplaintType = ComplaintType;
    readonly AssessmentType = AssessmentType;

    // Icons
    faSpinner = faSpinner;
    faSave = faSave;
    faSquareCaretRight = faSquareCaretRight;

    @Input() set highlightDifferences(highlightDifferences: boolean) {
        this._highlightDifferences = highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }

    constructor() {
        this.textAssessmentAnalytics.setComponentRoute(this.route);
    }

    get highlightDifferences() {
        return this._highlightDifferences;
    }

    get overrideVisible() {
        return this.result?.completionDate && this.canOverride;
    }

    get assessNextVisible() {
        return this.result?.completionDate && (this.isAssessor || this.exercise?.isAtLeastInstructor) && !this.hasComplaint && !this.isTeamMode && !this.isTestRun;
    }

    get saveDisabled() {
        // if there is no 'save' button
        if (this.result?.completionDate) {
            return true;
        } else if (Result.hasNonEmptyAssessmentNote(this.result)) {
            return this.saveDisabledWithAssessmentNotePresent;
        } else {
            return this.saveDisabledWithoutAssessmentNotePresent;
        }
    }

    get saveDisabledWithAssessmentNotePresent() {
        // this is almost identical to submitDisabled, but without the assessmentsAreValid check
        // otherwise, we wouldn't be able to save the assessment note without making prior changes to the feedback
        return !this.isAssessor || this.saveBusy || this.submitBusy || this.cancelBusy;
    }

    get saveDisabledWithoutAssessmentNotePresent() {
        return !this.assessmentsAreValid || this.saveDisabledWithAssessmentNotePresent;
    }

    get submitDisabled() {
        return !this.assessmentsAreValid || !this.isAssessor || this.saveBusy || this.submitBusy || this.cancelBusy;
    }

    get overrideDisabled() {
        if (this.overrideVisible) {
            return !this.assessmentsAreValid || this.submitBusy;
        } else {
            return true;
        }
    }

    get assessNextDisabled() {
        if (this.assessNextVisible) {
            return this.nextSubmissionBusy || this.submitBusy;
        } else {
            return true;
        }
    }

    @HostListener('document:keydown.control.s', ['$event'])
    saveOnControlAndS(event: KeyboardEvent) {
        event.preventDefault();
        if (!this.saveDisabled) {
            this.save.emit();
        }
    }

    @HostListener('document:keydown.control.enter', ['$event'])
    submitOnControlAndEnter(event: KeyboardEvent) {
        event.preventDefault();
        if (!this.overrideDisabled) {
            this.onSubmit.emit();
        } else if (!this.submitDisabled) {
            this.onSubmit.emit();
            this.sendSubmitAssessmentEventToAnalytics();
        }
    }

    @HostListener('document:keydown.control.shift.arrowRight', ['$event'])
    assessNextOnControlShiftAndArrowRight(event: KeyboardEvent) {
        event.preventDefault();
        if (!this.assessNextDisabled) {
            this.nextSubmission.emit();
            this.sendAssessNextEventToAnalytics();
        }
    }

    /**
     * In ExamMode:
     * Highlight the difference between the first and second correction round
     */
    public toggleHighlightDifferences() {
        this.highlightDifferences = !this.highlightDifferences;
        this.highlightDifferencesChange.emit(this.highlightDifferences);
    }

    /**
     * Sends and assessment event for the submit button using the analytics service in case the exercise type is TEXT
     */
    sendSubmitAssessmentEventToAnalytics() {
        if (this.exercise?.type === ExerciseType.TEXT) {
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.SUBMIT_ASSESSMENT);
        }
    }

    /**
     * Sends and assessment event for the assess next button using the analytics service in case the exercise type is TEXT
     */
    sendAssessNextEventToAnalytics() {
        if (this.exercise?.type === ExerciseType.TEXT) {
            this.textAssessmentAnalytics.sendAssessmentEvent(TextAssessmentEventType.ASSESS_NEXT_SUBMISSION);
        }
    }

    /**
     * Opens dialog to verify that instructor wants to use current submission as example submission
     */
    onUseAsExampleSolutionClicked() {
        const verificationMessage = this.translateService.instant('artemisApp.assessment.useAsExampleSubmissionVerificationQuestion');
        if (confirm(verificationMessage)) {
            this.useAsExampleSubmission.emit();
        }
    }
}
