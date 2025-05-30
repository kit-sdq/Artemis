import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ActivatedRoute, Router, RouterOutlet } from '@angular/router';
import { Subscription } from 'rxjs';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseStorageService } from 'app/core/course/manage/services/course-storage.service';
import { LtiService } from 'app/shared/service/lti.service';
import { NgClass, NgStyle } from '@angular/common';
import { SidebarComponent } from 'app/shared/sidebar/sidebar.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CourseOverviewService } from 'app/core/course/overview/services/course-overview.service';
import { AccordionGroups, CollapseState, SidebarCardElement, SidebarData, SidebarItemShowAlways } from 'app/shared/types/sidebar';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { forkJoin } from 'rxjs';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    dueSoon: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

const DEFAULT_COLLAPSE_STATE: CollapseState = {
    future: true,
    current: false,
    dueSoon: false,
    past: true,
    noDate: true,
};

const DEFAULT_SHOW_ALWAYS: SidebarItemShowAlways = {
    future: false,
    current: false,
    dueSoon: false,
    past: false,
    noDate: false,
};

@Component({
    selector: 'jhi-course-exercises',
    templateUrl: './course-exercises.component.html',
    styleUrls: ['../course-overview/course-overview.scss'],
    imports: [NgClass, SidebarComponent, NgStyle, RouterOutlet, TranslateDirective],
})
export class CourseExercisesComponent implements OnInit, OnDestroy {
    private courseStorageService = inject(CourseStorageService);
    private route = inject(ActivatedRoute);
    private programmingSubmissionService = inject(ProgrammingSubmissionService);
    private router = inject(Router);
    private courseOverviewService = inject(CourseOverviewService);
    private ltiService = inject(LtiService);
    private exerciseService = inject(ExerciseService);

    private parentParamSubscription: Subscription;
    private courseUpdatesSubscription: Subscription;
    private ltiSubscription: Subscription;
    private multiLaunchSubscription: Subscription;
    private queryParamsSubscription: Subscription;

    course?: Course;
    courseId: number;
    sortedExercises?: Exercise[];

    exerciseSelected = true;
    accordionExerciseGroups: AccordionGroups = DEFAULT_UNIT_GROUPS;
    sidebarData: SidebarData;
    sidebarExercises: SidebarCardElement[] = [];
    isCollapsed = false;
    isShownViaLti = false;
    isMultiLaunch = false;
    multiLaunchExerciseIDs: number[] = [];

    protected readonly DEFAULT_COLLAPSE_STATE = DEFAULT_COLLAPSE_STATE;
    protected readonly DEFAULT_SHOW_ALWAYS = DEFAULT_SHOW_ALWAYS;

    ngOnInit() {
        this.isCollapsed = this.courseOverviewService.getSidebarCollapseStateFromStorage('exercise');
        this.parentParamSubscription = this.route.parent!.params.subscribe((params) => {
            this.courseId = Number(params.courseId);
        });

        this.queryParamsSubscription = this.route.queryParams.subscribe((params) => {
            if (params['exerciseIDs']) {
                this.multiLaunchExerciseIDs = params['exerciseIDs'].split(',').map((id: string) => Number(id));
            }
        });

        this.course = this.courseStorageService.getCourse(this.courseId);
        this.onCourseLoad();
        this.prepareSidebarData();

        this.courseUpdatesSubscription = this.courseStorageService.subscribeToCourseUpdates(this.courseId).subscribe((course: Course) => {
            this.course = course;
            this.prepareSidebarData();
            this.onCourseLoad();
        });

        this.ltiSubscription = this.ltiService.isShownViaLti$.subscribe((isShownViaLti) => {
            this.isShownViaLti = isShownViaLti;
        });

        this.multiLaunchSubscription = this.ltiService.isMultiLaunch$.subscribe((isMultiLaunch) => {
            this.isMultiLaunch = isMultiLaunch;
        });

        // If no exercise is selected navigate to the lastSelected or upcoming exercise
        this.navigateToExercise();
    }

    navigateToExercise() {
        const upcomingExercise = this.courseOverviewService.getUpcomingExercise(this.course?.exercises);
        const lastSelectedExercise = this.getLastSelectedExercise();
        let exerciseId = this.route.firstChild?.snapshot?.params.exerciseId;

        if (!exerciseId) {
            // Get the exerciseId from the URL
            const url = this.router.url;
            const urlParts = url.split('/');
            const indexOfExercise = urlParts.indexOf('exercises');
            if (indexOfExercise !== -1 && urlParts.length === indexOfExercise + 2) {
                exerciseId = urlParts[indexOfExercise + 1];
            }
        }

        if (!exerciseId && lastSelectedExercise) {
            this.router.navigate([lastSelectedExercise], { relativeTo: this.route, replaceUrl: true });
        } else if (!exerciseId && upcomingExercise) {
            this.router.navigate([upcomingExercise.id], { relativeTo: this.route, replaceUrl: true });
        } else {
            this.exerciseSelected = !!exerciseId;
        }
    }

    toggleSidebar() {
        this.isCollapsed = !this.isCollapsed;
        this.courseOverviewService.setSidebarCollapseState('exercise', this.isCollapsed);
    }

    getLastSelectedExercise(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedItem.exercise.byCourse.' + this.courseId);
    }

    prepareSidebarData() {
        const exercises: Exercise[] = [];

        if (this.multiLaunchExerciseIDs?.length > 0) {
            const exerciseObservables = this.multiLaunchExerciseIDs.map((exerciseId) => this.exerciseService.find(exerciseId));

            forkJoin(exerciseObservables).subscribe((exerciseResponses) => {
                exerciseResponses.forEach((response) => {
                    exercises.push(response.body!);
                });

                this.processExercises(exercises);
            });
        } else {
            if (!this.course?.exercises) {
                return;
            }
            this.processExercises(this.course.exercises);
        }
    }

    processExercises(exercises: Exercise[]): void {
        this.sortedExercises = this.courseOverviewService.sortExercises(exercises);
        this.sidebarExercises = this.courseOverviewService.mapExercisesToSidebarCardElements(this.sortedExercises);
        this.accordionExerciseGroups = this.courseOverviewService.groupExercisesByDueDate(this.sortedExercises);
        this.updateSidebarData();
    }

    updateSidebarData() {
        this.sidebarData = {
            groupByCategory: true,
            sidebarType: 'exercise',
            storageId: 'exercise',
            groupedData: this.accordionExerciseGroups,
            ungroupedData: this.sidebarExercises,
        };
    }

    private onCourseLoad() {
        this.programmingSubmissionService.initializeCacheForStudent(this.course?.exercises, true);
    }

    onSubRouteDeactivate() {
        if (this.route.firstChild) {
            return;
        }
        this.navigateToExercise();
    }

    ngOnDestroy(): void {
        this.courseUpdatesSubscription?.unsubscribe();
        this.parentParamSubscription?.unsubscribe();
        this.ltiSubscription?.unsubscribe();
        this.multiLaunchSubscription?.unsubscribe();
        this.queryParamsSubscription?.unsubscribe();
    }
}
