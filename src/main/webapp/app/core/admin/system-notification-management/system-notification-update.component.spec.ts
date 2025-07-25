import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { SystemNotificationManagementUpdateComponent } from 'app/core/admin/system-notification-management/system-notification-management-update.component';
import { SystemNotification, SystemNotificationType } from 'app/core/shared/entities/system-notification.model';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe, MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockRouter } from '../../../../../../test/javascript/spec/helpers/mocks/mock-router';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AdminSystemNotificationService } from 'app/core/notification/system-notification/admin-system-notification.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('SystemNotificationManagementUpdateComponent', () => {
    let updateComponentFixture: ComponentFixture<SystemNotificationManagementUpdateComponent>;
    let updateComponent: SystemNotificationManagementUpdateComponent;
    let adminService: AdminSystemNotificationService;

    const route = {
        parent: {
            data: of({ notification: { id: 1, title: 'test', type: 'INFO', notificationDate: dayjs(), expireDate: dayjs().add(1, 'hour') } as SystemNotification }),
        },
    } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ReactiveFormsModule, FaIconComponent],
            declarations: [SystemNotificationManagementUpdateComponent, MockPipe(ArtemisTranslatePipe), MockComponent(FormDateTimePickerComponent)],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                MockProvider(ArtemisNavigationUtilService),
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                updateComponentFixture = TestBed.createComponent(SystemNotificationManagementUpdateComponent);
                updateComponent = updateComponentFixture.componentInstance;
                adminService = TestBed.inject(AdminSystemNotificationService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        updateComponentFixture.detectChanges();
        expect(updateComponent).not.toBeNull();
    });

    it('navigate back if cancel is clicked', fakeAsync(() => {
        const goToOverview = jest.spyOn(updateComponent, 'goToOverview');
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#cancelButton');
        button.click();

        tick();
        expect(goToOverview).toHaveBeenCalledOnce();
    }));

    it('should update / create if save is clicked', fakeAsync(() => {
        const saveSpy = jest.spyOn(updateComponent, 'save');
        jest.spyOn(adminService, 'update').mockReturnValue(of(new HttpResponse<SystemNotification>()));
        updateComponentFixture.detectChanges();

        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#saveButton');
        button.click();

        tick();
        expect(saveSpy).toHaveBeenCalledOnce();
    }));

    it.each([
        { name: 'title', exampleValue: 'A title' },
        { name: 'type', exampleValue: SystemNotificationType.INFO },
        { name: 'notificationDate', exampleValue: dayjs() },
        { name: 'expireDate', exampleValue: dayjs() },
    ])('should require required fields', ({ name, exampleValue }) => {
        updateComponentFixture.detectChanges();

        updateComponent.form.controls[name].setValue(null);
        updateComponentFixture.detectChanges();
        expect(updateComponent.form.controls[name].errors?.required).toBeTrue();

        updateComponent.form.controls[name].setValue(exampleValue);
        updateComponentFixture.detectChanges();
        expect(updateComponent.form.controls[name].errors?.required).toBeUndefined();
    });

    it('should ensure that notification date is before expire date', fakeAsync(() => {
        const saveSpy = jest.spyOn(updateComponent, 'save');
        updateComponentFixture.detectChanges();

        updateComponent.form.controls['notificationDate'].setValue(dayjs());
        updateComponent.form.controls['expireDate'].setValue(dayjs().subtract(1, 'hour'));

        updateComponentFixture.detectChanges();
        const button = updateComponentFixture.debugElement.nativeElement.querySelector('#saveButton');
        button.click();

        tick();
        expect(saveSpy).not.toHaveBeenCalled();

        updateComponent.form.controls['expireDate'].setValue(dayjs().add(1, 'hour'));
        updateComponentFixture.detectChanges();
        button.click();

        tick();
        expect(saveSpy).toHaveBeenCalledOnce();
    }));
});
