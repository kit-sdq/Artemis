import { Component, OnChanges, OnInit, inject, input, output } from '@angular/core';
import { Exercise, ExerciseType, getIcon, getIconTooltip } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamPageComponent } from 'app/exam/overview/exercises/exam-page.component';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamExerciseOverviewItem } from 'app/exam/shared/entities/exam-exercise-overview-item.model';
import { ButtonTooltipType, ExamParticipationService } from 'app/exam/overview/services/exam-participation.service';
import { faHourglassHalf } from '@fortawesome/free-solid-svg-icons';
import { ExerciseButtonStatus } from 'app/exam/overview/exam-navigation-sidebar/exam-navigation-sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { NgClass } from '@angular/common';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { facSaveSuccess, facSaveWarning } from 'app/shared/icons/icons';

@Component({
    selector: 'jhi-exam-exercise-overview-page',
    templateUrl: './exam-exercise-overview-page.component.html',
    styleUrls: ['./exam-exercise-overview-page.scss', '../../exam-navigation-sidebar/exam-navigation-sidebar.component.scss'],
    imports: [TranslateDirective, FaIconComponent, NgbTooltip, NgClass, UpdatingResultComponent, ArtemisTranslatePipe],
})
export class ExamExerciseOverviewPageComponent extends ExamPageComponent implements OnInit, OnChanges {
    private examParticipationService = inject(ExamParticipationService);

    studentExam = input.required<StudentExam>();
    onPageChanged = output<{
        overViewChange: boolean;
        exercise: Exercise;
        forceSave: boolean;
    }>();
    getIcon = getIcon;
    getIconTooltip = getIconTooltip;
    showResultWidth = 10;

    examExerciseOverviewItems: ExamExerciseOverviewItem[] = [];

    ngOnInit() {
        this.studentExam().exercises?.forEach((exercise) => {
            const item = new ExamExerciseOverviewItem();
            item.exercise = exercise;
            item.icon = faHourglassHalf;
            this.examExerciseOverviewItems.push(item);
        });
    }

    ngOnChanges() {
        this.examExerciseOverviewItems?.forEach((item) => {
            this.setExerciseIconStatus(item);
        });
    }

    updateShowResultWidth() {
        this.showResultWidth = 35;
    }

    openExercise(exercise: Exercise) {
        this.onPageChanged.emit({ overViewChange: false, exercise, forceSave: false });
    }

    getExerciseButtonTooltip(exercise: Exercise): ButtonTooltipType {
        return this.examParticipationService.getExerciseButtonTooltip(exercise);
    }

    /**
     * calculate the exercise status (also see exam-navigation-bar.component.ts --> make sure the logic is consistent)
     * also determines the used icon and its color
     * TODO: we should try to extract a method for the common logic which avoids side effects (i.e. changing this.icon)
     *  this method could e.g. return the sync status and the icon
     *
     * @param item the item for which the exercise status should be calculated
     * @return the sync status of the exercise (whether the corresponding submission is saved on the server or not)
     */
    setExerciseIconStatus(item: ExamExerciseOverviewItem): ExerciseButtonStatus {
        const submission = ExamParticipationService.getSubmissionForExercise(item.exercise);
        // start with exercise not started icon
        item.icon = faHourglassHalf;
        if (!submission) {
            // in case no participation/submission yet exists -> display synced
            // this should only occur for programming exercises
            return ExerciseButtonStatus.Synced;
        }
        if (submission.submitted && submission.isSynced) {
            item.icon = facSaveSuccess;
            return ExerciseButtonStatus.SyncedSaved;
        }
        if (submission.isSynced) {
            // make save icon green
            return ExerciseButtonStatus.Synced;
        } else {
            // make save icon yellow except for programming exercises with only offline IDE
            item.icon = facSaveWarning;
            return ExerciseButtonStatus.NotSynced;
        }
    }

    protected readonly ExerciseType = ExerciseType;
}
