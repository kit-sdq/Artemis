import { Component, Input, OnChanges, OnInit, ViewEncapsulation } from '@angular/core';
import dayjs from 'dayjs/esm';
import { Exercise, IncludedInOverallScore, getCourseFromExercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ButtonType } from 'app/shared/components/buttons/button/button.component';
import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { getExerciseDueDate, hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgClass, NgStyle } from '@angular/common';
import { DifficultyBadgeComponent } from '../difficulty-badge/difficulty-badge.component';
import { IncludedInScoreBadgeComponent } from '../included-in-score-badge/included-in-score-badge.component';
import { SubmissionResultStatusComponent } from 'app/core/course/overview/submission-result-status/submission-result-status.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { getLatestResultOfStudentParticipation } from 'app/exercise/participation/participation.utils';

@Component({
    selector: 'jhi-header-participation-page',
    templateUrl: './header-participation-page.component.html',
    styleUrls: ['./header-participation-page.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        NgClass,
        NgStyle,
        DifficultyBadgeComponent,
        IncludedInScoreBadgeComponent,
        SubmissionResultStatusComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
        ArtemisTimeAgoPipe,
    ],
})
export class HeaderParticipationPageComponent implements OnInit, OnChanges {
    readonly ButtonType = ButtonType;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    @Input() title: string;
    @Input() exercise: Exercise;
    @Input() participation: StudentParticipation;

    public exerciseStatusBadge = 'bg-success';
    public exerciseCategories: ExerciseCategory[];
    public achievedPoints?: number;

    dueDate?: dayjs.Dayjs;
    getIcon = getIcon;

    /**
     * Sets the status badge and categories of the exercise on init
     */
    ngOnInit(): void {
        this.ngOnChanges();
    }

    /**
     * Returns false if it is an exam exercise and the publishResultsDate is in the future, true otherwise
     */
    get resultsPublished(): boolean {
        if (this.exercise?.exerciseGroup?.exam) {
            if (this.exercise.exerciseGroup.exam.publishResultsDate) {
                return dayjs().isAfter(this.exercise.exerciseGroup.exam.publishResultsDate);
            }
            // default to false if it is an exam exercise but the publishResultsDate is not set
            return false;
        }
        return true;
    }

    /**
     * Sets the status badge and categories of the exercise on changes
     */
    ngOnChanges() {
        if (this.exercise) {
            this.exerciseStatusBadge = hasExerciseDueDatePassed(this.exercise, this.participation) ? 'bg-danger' : 'bg-success';
            this.exerciseCategories = this.exercise.categories || [];
            this.dueDate = getExerciseDueDate(this.exercise, this.participation);
            const result = getLatestResultOfStudentParticipation(this.participation, false, true);
            if (result?.rated) {
                this.achievedPoints = roundValueSpecifiedByCourseSettings((result.score! * this.exercise.maxPoints!) / 100, getCourseFromExercise(this.exercise));
            }
        }
    }
}
