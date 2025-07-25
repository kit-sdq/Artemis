import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { TutorialGroupFormComponent, TutorialGroupFormData } from 'app/tutorialgroup/manage/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { of } from 'rxjs';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { User } from 'app/core/user/user.model';
import { NgbTimepickerModule, NgbTypeaheadModule } from '@ng-bootstrap/ng-bootstrap';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ScheduleFormComponent, ScheduleFormData } from 'app/tutorialgroup/manage/tutorial-groups/crud/tutorial-group-form/schedule-form/schedule-form.component';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import '@angular/localize/init';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from 'test/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';
import { ArtemisDateRangePipe } from 'app/shared/pipes/artemis-date-range.pipe';
import { runOnPushChangeDetection } from 'test/helpers/on-push-change-detection.helper';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';

@Component({ selector: 'jhi-markdown-editor-monaco', template: '' })
class MarkdownEditorStubComponent {
    @Input() markdown: string;
    @Input() enableResize = false;
    @Output() markdownChange = new EventEmitter<string>();
}

describe('TutorialGroupFormComponent', () => {
    let fixture: ComponentFixture<TutorialGroupFormComponent>;
    let component: TutorialGroupFormComponent;
    const course = { id: 1, title: 'Example', isAtLeastInstructor: true };
    // group
    const validTitle = 'ExampleTitle';
    let validTeachingAssistant: User;
    const validCampus = 'ExampleCampus';
    const validCapacity = 10;
    const validIsOnline = true;
    const validLanguage = 'GERMAN';
    const validAdditionalInformation = 'ExampleAdditionalInformation';
    const validNotificationText = 'ExampleNotificationText';
    // schedule
    const validDayOfWeek = 1;
    const validStartTime = '10:00';
    const validEndTime = '11:00';
    const validRepetitionFrequency = 1;
    const validPeriodStart = new Date(Date.UTC(2021, 1, 1));
    const validPeriodEnd = new Date(Date.UTC(2021, 2, 1));
    const validPeriod = [validPeriodStart, validPeriodEnd];
    const validLocation = 'ExampleLocation';

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string, subFormName?: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTypeaheadModule, NgbTimepickerModule, OwlDateTimeModule, OwlNativeDateTimeModule, FaIconComponent],
            declarations: [
                TutorialGroupFormComponent,
                ScheduleFormComponent,
                MarkdownEditorStubComponent,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisDateRangePipe),
            ],
            providers: [
                MockProvider(CourseManagementService, {
                    getAllUsersInCourseGroup: () => {
                        return of(new HttpResponse({ body: [] }));
                    },
                }),
                MockProvider(TutorialGroupsService, {
                    getUniqueCampusValues: () => {
                        return of(new HttpResponse({ body: [] }));
                    },
                    getUniqueLanguageValues: () => {
                        return of(new HttpResponse({ body: [] }));
                    },
                }),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                { provide: ThemeService, useClass: MockThemeService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupFormComponent);
        validTeachingAssistant = new User();
        validTeachingAssistant.login = 'testLogin';
        component = fixture.componentInstance;
        fixture.componentRef.setInput('course', course);
        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('with schedule', () => {
        beforeEach(() => {
            component.configureSchedule = true;
            runOnPushChangeDetection(fixture);
            clickSubmit = generateClickSubmitButton(component, fixture, {
                title: validTitle,
                teachingAssistant: validTeachingAssistant,
                capacity: validCapacity,
                isOnline: validIsOnline,
                language: validLanguage,
                campus: validCampus,
                additionalInformation: validAdditionalInformation,
                schedule: {
                    dayOfWeek: validDayOfWeek,
                    startTime: validStartTime,
                    endTime: validEndTime,
                    repetitionFrequency: validRepetitionFrequency,
                    period: validPeriod,
                    location: validLocation,
                },
            });
            testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
        });

        it('should block submit when required property is missing', fakeAsync(() => {
            jest.spyOn(console, 'warn').mockImplementation(() => {});

            const requiredGroupControlNames = ['title', 'teachingAssistant', 'isOnline', 'language'];
            const requiredScheduleControlNames = ['dayOfWeek', 'startTime', 'endTime', 'repetitionFrequency', 'period', 'location'];

            for (const controlName of requiredGroupControlNames) {
                testFormIsInvalidOnMissingRequiredProperty(controlName);
            }
            for (const controlName of requiredScheduleControlNames) {
                testFormIsInvalidOnMissingRequiredProperty(controlName, 'schedule');
            }
        }));

        it('should block submit when time range is invalid', fakeAsync(() => {
            setValidFormValues();
            runOnPushChangeDetection(fixture);
            expect(component.form.valid).toBeTrue();
            expect(component.isSubmitPossible).toBeTrue();

            component.form.get('schedule')!.get('endTime')!.setValue('11:00:00');
            component.form.get('schedule')!.get('startTime')!.setValue('12:00:00');
            runOnPushChangeDetection(fixture);
            expect(component.form.invalid).toBeTrue();
            expect(component.isSubmitPossible).toBeFalse();

            clickSubmit(false);
        }));

        it('should correctly set form values in edit mode', () => {
            fixture.componentRef.setInput('isEditMode', true);
            runOnPushChangeDetection(fixture);
            const formData: TutorialGroupFormData = {
                title: validTitle,
                teachingAssistant: validTeachingAssistant,
                additionalInformation: validAdditionalInformation,
                campus: validCampus,
                capacity: validCapacity,
                isOnline: validIsOnline,
                language: validLanguage,
                schedule: {
                    dayOfWeek: validDayOfWeek,
                    startTime: validStartTime,
                    endTime: validEndTime,
                    repetitionFrequency: validRepetitionFrequency,
                    period: validPeriod,
                    location: validLocation,
                },
            };
            fixture.componentRef.setInput('formData', formData);
            component.ngOnChanges();

            const groupFormControlNames: Array<keyof TutorialGroupFormData> = ['title', 'teachingAssistant', 'campus', 'capacity', 'isOnline', 'language'];
            for (const controlName of groupFormControlNames) {
                expect(component.form.get(controlName)?.value).toEqual(formData[controlName]);
            }
            expect(component.additionalInformation).toEqual(formData.additionalInformation);

            const scheduleFormControlNames: Array<keyof ScheduleFormData> = ['dayOfWeek', 'startTime', 'endTime', 'repetitionFrequency', 'period', 'location'];
            for (const controlName of scheduleFormControlNames) {
                expect(component.form.get('schedule')!.get(controlName)!.value).toEqual(formData.schedule![controlName]);
            }
        });

        it('should submit valid form', fakeAsync(() => {
            setValidFormValues();
            runOnPushChangeDetection(fixture);
            expect(component.form.valid).toBeTrue();
            expect(component.isSubmitPossible).toBeTrue();

            clickSubmit(true);
        }));
    });

    describe('without schedule', () => {
        beforeEach(() => {
            component.configureSchedule = false;
            runOnPushChangeDetection(fixture);
            clickSubmit = generateClickSubmitButton(component, fixture, {
                title: validTitle,
                teachingAssistant: validTeachingAssistant,
                capacity: validCapacity,
                isOnline: validIsOnline,
                language: validLanguage,
                campus: validCampus,
                additionalInformation: validAdditionalInformation,
            });
            testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);
        });

        it('should block submit when required property is missing', fakeAsync(() => {
            jest.spyOn(console, 'warn').mockImplementation(() => {});

            const requiredGroupControlNames = ['title', 'teachingAssistant', 'isOnline', 'language'];
            for (const controlName of requiredGroupControlNames) {
                testFormIsInvalidOnMissingRequiredProperty(controlName);
            }
        }));

        it('should correctly set form values in edit mode', () => {
            fixture.componentRef.setInput('isEditMode', true);
            runOnPushChangeDetection(fixture);
            const formData: TutorialGroupFormData = {
                title: validTitle,
                teachingAssistant: validTeachingAssistant,
                additionalInformation: validAdditionalInformation,
                campus: validCampus,
                capacity: validCapacity,
                isOnline: validIsOnline,
                language: validLanguage,
                schedule: undefined,
            };

            fixture.componentRef.setInput('formData', formData);
            component.ngOnChanges();

            const formControlNames: Array<keyof TutorialGroupFormData> = ['title', 'teachingAssistant', 'campus', 'capacity', 'isOnline', 'language'];
            for (const controlName of formControlNames) {
                expect(component.form.get(controlName)!.value).toEqual(formData[controlName]);
            }
            expect(component.additionalInformation).toEqual(formData.additionalInformation);
        });

        it('should submit valid form', fakeAsync(() => {
            setValidFormValues();
            runOnPushChangeDetection(fixture);
            fixture.whenStable().then(() => {
                expect(component.form.valid).toBeTrue();
                expect(component.isSubmitPossible).toBeTrue();
                clickSubmit(true);
            });
        }));
    });

    // === helper functions ===

    const setValidFormValues = () => {
        component.titleControl!.setValue(validTitle);
        component.teachingAssistantControl!.setValue(validTeachingAssistant);
        component.capacityControl!.setValue(validCapacity);
        component.isOnlineControl!.setValue(validIsOnline);
        component.languageControl!.setValue(validLanguage);
        component.campusControl!.setValue(validCampus);
        component.additionalInformation = validAdditionalInformation;
        if (component.notificationControl) {
            component.notificationControl?.setValue(validNotificationText);
        }

        if (component.form.get('schedule')) {
            component.form.get('schedule')!.get('dayOfWeek')!.setValue(validDayOfWeek);
            component.form.get('schedule')!.get('startTime')!.setValue(validStartTime);
            component.form.get('schedule')!.get('endTime')!.setValue(validEndTime);
            component.form.get('schedule')!.get('repetitionFrequency')!.setValue(validRepetitionFrequency);
            component.form.get('schedule')!.get('period')!.setValue(validPeriod);
            component.form.get('schedule')!.get('location')!.setValue(validLocation);
        }
    };
});
