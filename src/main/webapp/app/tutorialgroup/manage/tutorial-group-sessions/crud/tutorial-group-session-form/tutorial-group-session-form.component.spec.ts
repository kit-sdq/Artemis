import { ComponentFixture, TestBed, fakeAsync } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import {
    TutorialGroupSessionFormComponent,
    TutorialGroupSessionFormData,
} from 'app/tutorialgroup/manage/tutorial-group-sessions/crud/tutorial-group-session-form/tutorial-group-session-form.component';
import { NgbTimepickerModule } from '@ng-bootstrap/ng-bootstrap';
import '@angular/localize/init';
import { OwlDateTimeModule, OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { generateClickSubmitButton, generateTestFormIsInvalidOnMissingRequiredProperty } from 'test/helpers/sample/tutorialgroup/tutorialGroupFormsUtils';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { runOnPushChangeDetection } from 'test/helpers/on-push-change-detection.helper';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateModule } from '@ngx-translate/core';

describe('TutorialGroupSessionForm', () => {
    let fixture: ComponentFixture<TutorialGroupSessionFormComponent>;
    let component: TutorialGroupSessionFormComponent;
    const validDate = new Date(Date.UTC(2021, 1, 1));
    const validStartTime = '12:00:00';
    const validEndTime = '13:00:00';
    const validLocation = 'Garching';

    let clickSubmit: (expectSubmitEvent: boolean) => void;
    let testFormIsInvalidOnMissingRequiredProperty: (controlName: string) => void;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FormsModule, NgbTimepickerModule, OwlDateTimeModule, OwlNativeDateTimeModule, TranslateModule.forRoot()],
            declarations: [
                TutorialGroupSessionFormComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockPipe(ArtemisDatePipe),
                MockDirective(TranslateDirective),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupSessionFormComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('timeZone', 'Europe/Berlin');
        fixture.detectChanges();

        clickSubmit = generateClickSubmitButton(component, fixture, {
            date: validDate,
            startTime: validStartTime,
            endTime: validEndTime,
            location: validLocation,
        });

        testFormIsInvalidOnMissingRequiredProperty = generateTestFormIsInvalidOnMissingRequiredProperty(component, fixture, setValidFormValues, clickSubmit);

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).not.toBeNull();
    });

    it('should correctly set form values in edit mode', () => {
        fixture.componentRef.setInput('isEditMode', true);
        const formData: TutorialGroupSessionFormData = {
            date: validDate,
            startTime: validStartTime,
            endTime: validEndTime,
        };
        fixture.componentRef.setInput('formData', formData);
        component.ngOnChanges();

        const formControlNames = ['date', 'startTime', 'endTime'];
        formControlNames.forEach((control) => {
            expect(component.form.get(control)?.value).toEqual(formData[control as keyof TutorialGroupSessionFormData]);
        });
    });

    it('should submit valid form', fakeAsync(() => {
        setValidFormValues();
        runOnPushChangeDetection(fixture);
        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        clickSubmit(true);
    }));

    it('should block submit when time range is invalid', fakeAsync(() => {
        setValidFormValues();
        runOnPushChangeDetection(fixture);

        expect(component.form.valid).toBeTrue();
        expect(component.isSubmitPossible).toBeTrue();

        component.endTimeControl!.setValue('11:00:00');
        component.startTimeControl!.setValue('12:00:00');
        runOnPushChangeDetection(fixture);
        expect(component.form.invalid).toBeTrue();
        expect(component.isSubmitPossible).toBeFalse();

        clickSubmit(false);
    }));

    it('should block submit when required property is missing', fakeAsync(() => {
        const requiredControlNames = ['startTime', 'endTime', 'location'];
        for (const controlName of requiredControlNames) {
            testFormIsInvalidOnMissingRequiredProperty(controlName);
        }
    }));

    // === helper functions ===

    const setValidFormValues = () => {
        component.dateControl!.setValue(validDate);
        component.startTimeControl!.setValue(validStartTime);
        component.endTimeControl!.setValue(validEndTime);
        component.locationControl!.setValue(validLocation);
    };
});
