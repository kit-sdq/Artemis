import { ComponentFixture, TestBed } from '@angular/core/testing';
import { CourseManagementOverviewStatisticsComponent } from 'app/core/course/manage/overview/course-management-overview-statistics.component';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute } from '@angular/router';

describe('CourseManagementOverviewStatisticsComponent', () => {
    let fixture: ComponentFixture<CourseManagementOverviewStatisticsComponent>;
    let component: CourseManagementOverviewStatisticsComponent;

    const amountOfStudentsInCourse = 25;
    const initialStats = [0, 11, 9, 23];
    const course = { startDate: dayjs().subtract(5, 'weeks'), endDate: dayjs().add(5, 'weeks') };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CourseManagementOverviewStatisticsComponent);
                component = fixture.componentInstance;
                component.course = course;
            });
    });

    it('should initialize component and load values', () => {
        // Provide the @Input data
        component.amountOfStudentsInCourse = amountOfStudentsInCourse;
        component.initialStats = initialStats;

        component.ngOnInit();
        component.ngOnChanges();

        expect(component.ngxData).toHaveLength(1);
        expect(component.ngxData[0].name).toBe('active students');
        expect(component.ngxData[0].series[0].value).toBe(0);
        expect(component.ngxData[0].series[1].value).toBe(44);
        expect(component.ngxData[0].series[2].value).toBe(36);
        expect(component.ngxData[0].series[3].value).toBe(92);
    });

    it('should react to changes', () => {
        component.ngOnInit();

        component.initialStats = [];
        component.amountOfStudentsInCourse = 0;

        component.ngOnChanges();

        expect(component.ngxData[0].series[0].value).toBe(0);
        expect(component.ngxData[0].series[1].value).toBe(0);
        expect(component.ngxData[0].series[2].value).toBe(0);
        expect(component.ngxData[0].series[3].value).toBe(0);
    });

    it('should show lettering if course did not start yet', () => {
        component.course = { startDate: dayjs().add(1, 'week') };
        component.initialStats = [];

        component.ngOnInit();

        expect(component.startDateAlreadyPassed).toBeFalse();
    });

    it('should show only 2 weeks if start date is 1 week ago', () => {
        component.course = { startDate: dayjs().subtract(1, 'week') };
        component.initialStats = initialStats.slice(2);
        component.amountOfStudentsInCourse = amountOfStudentsInCourse;

        component.ngOnInit();

        expect(component.ngxData[0].series).toHaveLength(2);
        expect(component.ngxData[0].series[0].value).toBe(36);
        expect(component.ngxData[0].series[1].value).toBe(92);
    });

    it('should adapt labels if end date is passed', () => {
        component.course = { endDate: dayjs().subtract(1, 'week') };

        component.ngOnInit();

        expect(component.ngxData[0].series[3].name).toBe('overview.weekAgo');
    });

    it('should adapt if course phase is smaller than 4 weeks', () => {
        component.course = { startDate: dayjs().subtract(2, 'weeks'), endDate: dayjs().subtract(1, 'weeks') };
        component.amountOfStudentsInCourse = amountOfStudentsInCourse;
        component.initialStats = initialStats.slice(2);

        component.ngOnInit();

        expect(component.ngxData[0].series).toHaveLength(2);
        expect(component.ngxData[0].series[0].value).toBe(36);
        expect(component.ngxData[0].series[1].value).toBe(92);
        expect(component.ngxData[0].series[1].name).toBe('overview.weekAgo');
    });
});
