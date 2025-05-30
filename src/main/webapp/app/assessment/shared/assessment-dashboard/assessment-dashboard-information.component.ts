import { Component, Input, OnChanges, OnDestroy, OnInit, inject } from '@angular/core';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { LegendPosition, PieChartModule } from '@swimlane/ngx-charts';
import { TranslateService } from '@ngx-translate/core';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { SidePanelComponent } from 'app/shared/side-panel/side-panel.component';
import { Subscription } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { RouterLink } from '@angular/router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

export class AssessmentDashboardInformationEntry {
    constructor(
        public total: number,
        public tutor: number,
        public done?: number,
    ) {}

    /**
     * Computes the percentage of done/total ratio and returns it as a string
     */
    get doneToTotalPercentage(): string {
        if (this.done == undefined) {
            return '';
        }

        if (this.total === 0) {
            return '100%';
        }

        return `${((100 * this.done) / this.total).toFixed().toString()}%`;
    }
}

@Component({
    selector: 'jhi-assessment-dashboard-information',
    templateUrl: './assessment-dashboard-information.component.html',
    styleUrls: ['./assessment-dashboard-information.component.scss'],
    imports: [TranslateDirective, PieChartModule, RouterLink, ArtemisTranslatePipe, SidePanelComponent],
})
export class AssessmentDashboardInformationComponent implements OnInit, OnChanges, OnDestroy {
    private translateService = inject(TranslateService);

    @Input() isExamMode: boolean;
    @Input() course: Course;
    @Input() examId?: number;
    @Input() tutorId: number;

    @Input() complaintsEnabled: boolean;
    @Input() feedbackRequestEnabled: boolean;

    @Input() numberOfCorrectionRounds: number;
    @Input() numberOfAssessmentsOfCorrectionRounds: DueDateStat[];

    @Input() totalNumberOfAssessments: DueDateStat;
    @Input() numberOfSubmissions: DueDateStat;
    @Input() numberOfTutorAssessments: number;
    @Input() totalAssessmentPercentage: number;

    @Input() complaints: AssessmentDashboardInformationEntry;
    @Input() moreFeedbackRequests: AssessmentDashboardInformationEntry;
    @Input() assessmentLocks: AssessmentDashboardInformationEntry;
    @Input() ratings: AssessmentDashboardInformationEntry;

    // Graph data.
    completedAssessmentsTitle: string;
    openedAssessmentsTitle: string;
    assessments: any[];
    customColors: any[];
    view: [number, number] = [320, 150];
    legendPosition = LegendPosition.Below;

    complaintsLink: any[];
    moreFeedbackRequestsLink: any[];
    assessmentLocksLink: any[];
    ratingsLink: any[];

    themeSubscription: Subscription;

    ngOnInit(): void {
        this.setup();
        this.translateService.onLangChange.subscribe(() => {
            this.setupGraph();
        });

        this.customColors = [
            {
                name: this.openedAssessmentsTitle,
                value: GraphColors.RED,
            },
            {
                name: this.completedAssessmentsTitle,
                value: GraphColors.BLUE,
            },
        ];
    }

    ngOnChanges() {
        this.setup();
    }

    ngOnDestroy() {
        this.themeSubscription?.unsubscribe();
    }

    setup() {
        this.setupLinks();
        this.setupGraph();
    }

    setupLinks() {
        const examRouteIfNeeded = this.isExamMode ? ['exams', this.examId!] : [];

        this.complaintsLink = ['/course-management', this.course.id].concat(examRouteIfNeeded).concat(['complaints']);
        this.moreFeedbackRequestsLink = ['/course-management', this.course.id].concat(examRouteIfNeeded).concat(['more-feedback-requests']);
        this.assessmentLocksLink = ['/course-management', this.course.id].concat(examRouteIfNeeded).concat(['assessment-locks']);
        this.ratingsLink = ['/course-management', this.course.id, 'ratings'];
    }

    setupGraph() {
        this.completedAssessmentsTitle = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.closedAssessments');
        this.openedAssessmentsTitle = this.translateService.instant('artemisApp.exerciseAssessmentDashboard.openAssessments');

        this.assessments = [
            {
                name: this.openedAssessmentsTitle,
                value: this.numberOfSubmissions.total - this.totalNumberOfAssessments.total / this.numberOfCorrectionRounds,
            },
            {
                name: this.completedAssessmentsTitle,
                value: this.totalNumberOfAssessments.total / this.numberOfCorrectionRounds,
            },
        ];
    }
}
