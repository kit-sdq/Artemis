import { Component, ViewEncapsulation, inject } from '@angular/core';
import { DragItemComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-item/drag-item.component';
import { QuizStatisticUtil } from 'app/quiz/shared/service/quiz-statistic-util.service';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { ArtemisMarkdownService } from 'app/shared/service/markdown.service';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragAndDropQuestionStatistic } from 'app/quiz/shared/entities/drag-and-drop-question-statistic.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { QuestionStatisticComponent, blueColor, greenColor } from 'app/quiz/manage/statistics/question-statistic.component';
import { faCheckCircle, faSync, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { BarChartModule } from '@swimlane/ngx-charts';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { NgStyle } from '@angular/common';
import { QuizStatisticsFooterComponent } from '../quiz-statistics-footer/quiz-statistics-footer.component';

@Component({
    selector: 'jhi-drag-and-drop-question-statistic',
    templateUrl: './drag-and-drop-question-statistic.component.html',
    providers: [QuizStatisticUtil, DragAndDropQuestionUtil],
    styleUrls: [
        '../../../../shared/chart/vertical-bar-chart.scss',
        '../quiz-point-statistic/quiz-point-statistic.component.scss',
        './drag-and-drop-question-statistic.component.scss',
    ],
    encapsulation: ViewEncapsulation.None,
    imports: [TranslateDirective, BarChartModule, FaIconComponent, SecuredImageComponent, NgStyle, DragItemComponent, QuizStatisticsFooterComponent],
})
export class DragAndDropQuestionStatisticComponent extends QuestionStatisticComponent {
    private dragAndDropQuestionUtil = inject(DragAndDropQuestionUtil);
    private artemisMarkdown = inject(ArtemisMarkdownService);

    declare question: DragAndDropQuestion;

    // Icons
    faSync = faSync;
    faCheckCircle = faCheckCircle;
    faTimesCircle = faTimesCircle;

    loadQuiz(quiz: QuizExercise, refresh: boolean) {
        const updatedQuestion = super.loadQuizCommon(quiz);
        if (!updatedQuestion) {
            return;
        }
        // load Layout only at the opening (not if the websocket refreshed the data)
        if (!refresh) {
            this.questionTextRendered = this.artemisMarkdown.safeHtmlForMarkdown(this.question.text);
            this.loadLayout();
        }
        this.loadData();
    }

    /**
     * build the Chart-Layout based on the Json-entity (questionStatistic)
     */
    loadLayout() {
        this.orderDropLocationByPos();
        this.resetLabelsColors();

        // set label and background color based on the dropLocations
        this.question.dropLocations!.forEach((_dropLocation, i) => {
            this.labels.push(this.getLetter(i) + '.');
            this.solutionLabels.push(this.getLetter(i) + '.');
            this.backgroundColors.push(blueColor);
            this.backgroundSolutionColors.push(greenColor);
        });

        this.addLastBarLayout(this.question.dropLocations!.length);
        this.loadInvalidLayout(this.question.dropLocations!);
    }

    /**
     * load the Data from the Json-entity to the chart: myChart
     */
    loadData() {
        this.resetData();

        // set data based on the dropLocations for each dropLocation
        this.question.dropLocations!.forEach((dropLocation) => {
            // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
            const dropLocationCounter = (this.questionStatistic as DragAndDropQuestionStatistic).dropLocationCounters?.find(
                (dlCounter) => dropLocation.id === dlCounter.dropLocation!.id,
            )!;
            this.addData(dropLocationCounter.ratedCounter!, dropLocationCounter.unRatedCounter!);
        });
        this.updateData();
    }

    /**
     * order DropLocations by Position
     */
    orderDropLocationByPos() {
        let change = true;
        while (change) {
            change = false;
            if (this.question.dropLocations && this.question.dropLocations.length > 0) {
                for (let i = 0; i < this.question.dropLocations.length - 1; i++) {
                    if (this.question.dropLocations[i].posX! > this.question.dropLocations[i + 1].posX!) {
                        // switch DropLocations
                        const temp = this.question.dropLocations[i];
                        this.question.dropLocations[i] = this.question.dropLocations[i + 1];
                        this.question.dropLocations[i + 1] = temp;
                        change = true;
                    }
                }
            }
        }
    }

    /**
     * Get the drag item that was mapped to the given drop location in the sample solution
     *
     * @param dropLocation the drop location that the drag item should be mapped to
     * @return the mapped drag item, or undefined if no drag item has been mapped to this location
     */
    correctDragItemForDropLocation(dropLocation: DropLocation) {
        const currMapping = this.dragAndDropQuestionUtil.solve(this.question, undefined).filter((mapping) => mapping.dropLocation!.id === dropLocation.id)[0];
        return currMapping.dragItem;
    }
}
