import { Component, Input, inject } from '@angular/core';
import { faCheckCircle, faCircleDot, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ProgrammingExerciseInstructionService, TestCaseState } from 'app/programming/shared/instructions-render/services/programming-exercise-instruction.service';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SafeHtmlPipe } from 'app/shared/pipes/safe-html.pipe';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';

@Component({
    selector: 'jhi-programming-exercise-instructions-task-status',
    templateUrl: './programming-exercise-instruction-task-status.component.html',
    styleUrls: ['./programming-exercise-instruction-task-status.scss'],
    imports: [FaIconComponent, ArtemisTranslatePipe, SafeHtmlPipe],
})
export class ProgrammingExerciseInstructionTaskStatusComponent {
    private programmingExerciseInstructionService = inject(ProgrammingExerciseInstructionService);
    private modalService = inject(NgbModal);

    TestCaseState = TestCaseState;
    translationBasePath = 'artemisApp.editor.testStatusLabels.';

    @Input() taskName: string;

    /**
     * array of test ids
     */
    @Input()
    get testIds() {
        return this.testIdsValue;
    }
    @Input() exercise: Exercise;
    @Input() latestResult?: Result;
    @Input() participation: Participation;

    testIdsValue: number[];
    testCaseState: TestCaseState;

    /**
     * Arrays of test case ids, grouped by their status in the given result.
     */
    successfulTests: number[];
    notExecutedTests: number[];
    failedTests: number[];

    hasMessage: boolean;

    // Icons
    faCircleDot = faCircleDot;
    farCheckCircle = faCheckCircle;
    farTimesCircle = faTimesCircle;

    set testIds(testIds: number[]) {
        this.testIdsValue = testIds;
        const {
            testCaseState,
            detailed: { successfulTests, notExecutedTests, failedTests },
        } = this.programmingExerciseInstructionService.testStatusForTask(this.testIds, this.latestResult);
        this.testCaseState = testCaseState;
        this.successfulTests = successfulTests;
        this.notExecutedTests = notExecutedTests;
        this.failedTests = failedTests;
        this.hasMessage = this.hasTestMessage(testIds);
    }

    /**
     * Checks if any of the feedbacks have a detailText associated to them.
     * @param testIds the test case ids that should be checked for
     */
    private hasTestMessage(testIds: number[]): boolean {
        if (!this.latestResult?.feedbacks) {
            return false;
        }
        const feedbacks = this.latestResult.feedbacks;
        return testIds.some((testId: number) => feedbacks.find((feedback) => feedback.testCase?.id === testId && feedback.detailText));
    }

    /**
     * Opens the FeedbackComponent as popup. Displays test results.
     */
    public showDetailsForTests() {
        if (!this.latestResult) {
            return;
        }
        const modalRef = this.modalService.open(FeedbackComponent, { keyboard: true, size: 'lg' });
        const componentInstance = modalRef.componentInstance as FeedbackComponent;
        componentInstance.exercise = this.exercise;
        componentInstance.result = this.latestResult;
        componentInstance.participation = this.participation;
        componentInstance.feedbackFilter = this.testIds;
        componentInstance.exerciseType = ExerciseType.PROGRAMMING;
        componentInstance.taskName = this.taskName;
        componentInstance.numberOfNotExecutedTests = this.notExecutedTests.length;
    }
}
