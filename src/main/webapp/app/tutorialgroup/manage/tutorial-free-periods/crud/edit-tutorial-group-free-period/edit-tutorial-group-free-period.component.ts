import { ChangeDetectionStrategy, Component, Input, OnDestroy, inject } from '@angular/core';
import { TutorialGroupsConfiguration } from 'app/tutorialgroup/shared/entities/tutorial-groups-configuration.model';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { TutorialGroupFreePeriod } from 'app/tutorialgroup/shared/entities/tutorial-group-free-day.model';
import { TutorialGroupFreePeriodFormData } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/tutorial-free-period-form/tutorial-group-free-period-form.component';
import { Subject, finalize } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { takeUntil } from 'rxjs/operators';
import { CreateTutorialGroupFreePeriodComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/crud/create-tutorial-group-free-period/create-tutorial-group-free-period.component';
import { TutorialGroupFreePeriodsManagementComponent } from 'app/tutorialgroup/manage/tutorial-free-periods/tutorial-free-periods-management/tutorial-group-free-periods-management.component';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupFreePeriodFormComponent } from '../tutorial-free-period-form/tutorial-group-free-period-form.component';
import { captureException } from '@sentry/angular';
import { TutorialGroupFreePeriodDTO, TutorialGroupFreePeriodService } from 'app/tutorialgroup/shared/service/tutorial-group-free-period.service';

@Component({
    selector: 'jhi-edit-tutorial-group-free-period',
    templateUrl: './edit-tutorial-group-free-period.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [LoadingIndicatorContainerComponent, TranslateDirective, TutorialGroupFreePeriodFormComponent],
})
export class EditTutorialGroupFreePeriodComponent implements OnDestroy {
    private activeModal = inject(NgbActiveModal);
    private tutorialGroupFreePeriodService = inject(TutorialGroupFreePeriodService);
    private alertService = inject(AlertService);

    // Need to stick to @Input due to modelRef see https://github.com/ng-bootstrap/ng-bootstrap/issues/4688
    @Input() tutorialGroupFreePeriod: TutorialGroupFreePeriod;
    @Input() tutorialGroupsConfiguration: TutorialGroupsConfiguration;
    @Input() course: Course;

    isLoading = false;

    isInitialized = false;

    ngUnsubscribe = new Subject<void>();
    formData: TutorialGroupFreePeriodFormData;

    /**
     * Initializes the component by setting up the form data based on the tutorial group free period, course, and tutorial groups configuration.
     * If any of these are not provided, it logs an error and returns early.
     * It determines whether the tutorial group free period is a freePeriod, a freeDay, or a freePeriodWithinDay.
     * Based on these determinations, it sets up the form data accordingly.
     */
    initialize() {
        if (!this.tutorialGroupFreePeriod || !this.course || !this.tutorialGroupsConfiguration) {
            captureException('Error: Component not fully configured');
            return;
        }

        const isFreePeriod = TutorialGroupFreePeriodsManagementComponent.isFreePeriod(this.tutorialGroupFreePeriod);
        const isFreePeriodWithinDay = TutorialGroupFreePeriodsManagementComponent.isFreePeriodWithinDay(this.tutorialGroupFreePeriod);

        this.formData = {
            startDate: this.tutorialGroupFreePeriod.start?.tz(this.course.timeZone).toDate(),
            endDate: isFreePeriod ? this.tutorialGroupFreePeriod.end?.tz(this.course.timeZone).toDate() : undefined,
            startTime: isFreePeriodWithinDay ? this.tutorialGroupFreePeriod.start?.tz(this.course.timeZone).toDate() : undefined,
            endTime: isFreePeriodWithinDay ? this.tutorialGroupFreePeriod.end?.tz(this.course.timeZone).toDate() : undefined,
            reason: this.tutorialGroupFreePeriod.reason,
        };

        if (isFreePeriodWithinDay) {
            const tutorialGroupFreePeriodStart = this.tutorialGroupFreePeriod.start;
            const tutorialGroupFreePeriodEnd = this.tutorialGroupFreePeriod.end;
            if (this.formData.startTime && tutorialGroupFreePeriodStart) {
                this.formData.startTime.setHours(tutorialGroupFreePeriodStart.tz(this.course.timeZone).hour());
            }
            if (this.formData.endTime && tutorialGroupFreePeriodEnd) {
                this.formData.endTime.setHours(tutorialGroupFreePeriodEnd.tz(this.course.timeZone).hour());
            }
        }

        this.isInitialized = true;
    }

    updateTutorialGroupFreePeriod(formData: TutorialGroupFreePeriodFormData) {
        const { startDate, endDate, startTime, endTime, reason } = formData;

        const tutorialGroupFreePeriodDto = new TutorialGroupFreePeriodDTO();
        tutorialGroupFreePeriodDto.startDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(startDate, startTime, undefined);
        tutorialGroupFreePeriodDto.endDate = CreateTutorialGroupFreePeriodComponent.combineDateAndTimeWithAlternativeDate(endDate, endTime, startDate);
        tutorialGroupFreePeriodDto.reason = reason;

        this.isLoading = true;
        this.tutorialGroupFreePeriodService
            .update(this.course.id!, this.tutorialGroupsConfiguration.id!, this.tutorialGroupFreePeriod.id!, tutorialGroupFreePeriodDto)
            .pipe(
                finalize(() => {
                    this.isLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            )
            .subscribe({
                next: () => {
                    this.activeModal.close();
                },
                error: (res: HttpErrorResponse) => {
                    this.clear();
                    onError(this.alertService, res);
                },
            });
    }

    clear() {
        this.activeModal.dismiss();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }
}
