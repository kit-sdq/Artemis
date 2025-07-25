import { HttpHeaders, HttpResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { Location } from '@angular/common';
import { TranslateService } from '@ngx-translate/core';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { CourseForDashboardDTO } from 'app/core/course/shared/entities/course-for-dashboard-dto';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { CoursesForDashboardDTO } from 'app/core/course/shared/entities/courses-for-dashboard-dto';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseCardComponent } from 'app/core/course/overview/course-card/course-card.component';
import { CourseExerciseRowComponent } from 'app/core/course/overview/course-exercises/course-exercise-row/course-exercise-row.component';
import { CourseExercisesComponent } from 'app/core/course/overview/course-exercises/course-exercises.component';
import { CourseRegistrationComponent } from 'app/core/course/overview/course-registration/course-registration.component';
import { CoursesComponent } from 'app/core/course/overview/courses/courses.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { AlertService } from 'app/shared/service/alert.service';
import { Component } from '@angular/core';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { SearchFilterPipe } from 'app/shared/pipes/search-filter.pipe';
import { SearchFilterComponent } from 'app/shared/search-filter/search-filter.component';
import { CourseAccessStorageService } from 'app/core/course/shared/services/course-access-storage.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

const endDate1 = dayjs().add(1, 'days');
const visibleDate1 = dayjs().subtract(1, 'days');
const endDate2 = dayjs().subtract(1, 'days');
const visibleDate2 = dayjs().subtract(2, 'days');
const dueDateStat1: DueDateStat = { inTime: 1, late: 0, total: 1 };
const exercise1: Exercise = {
    id: 5,
    numberOfAssessmentsOfCorrectionRounds: [dueDateStat1],
    studentAssignedTeamIdComputed: false,
    dueDate: dayjs().add(2, 'hours'),
    secondCorrectionEnabled: true,
};
const exercise2: Exercise = {
    id: 6,
    numberOfAssessmentsOfCorrectionRounds: [dueDateStat1],
    studentAssignedTeamIdComputed: false,
    dueDate: dayjs().add(1, 'hours'),
    secondCorrectionEnabled: true,
};
const exercise3: Exercise = {
    id: 7,
    numberOfAssessmentsOfCorrectionRounds: [dueDateStat1],
    studentAssignedTeamIdComputed: false,
    dueDate: dayjs().add(3, 'hours'),
    secondCorrectionEnabled: true,
};
const courseEmpty: Course = {};

const exam1: Exam = { id: 3, endDate: endDate1, visibleDate: visibleDate1, course: courseEmpty };
const exam2: Exam = { id: 4, endDate: endDate2, visibleDate: visibleDate2, course: courseEmpty };
const exams = [exam1, exam2];
const course1: Course = { id: 1, exams, exercises: [exercise1, exercise3] };
const course1Dashboard = { course: course1 } as CourseForDashboardDTO;
const course2: Course = { id: 2, exercises: [exercise2], testCourse: true };
const course2Dashboard = { course: course2 } as CourseForDashboardDTO;
const coursesInDashboard: CourseForDashboardDTO[] = [course1Dashboard, course2Dashboard];
const courses: Course[] = [course1, course2];
const coursesDashboard = { courses: coursesInDashboard } as CoursesForDashboardDTO;

@Component({
    template: '',
})
class DummyComponent {}

describe('CoursesComponent', () => {
    let component: CoursesComponent;
    let fixture: ComponentFixture<CoursesComponent>;
    let courseService: CourseManagementService;
    let serverDateService: ArtemisServerDateService;
    let courseAccessStorageService: CourseAccessStorageService;
    let router: Router;
    let location: Location;
    let httpMock: HttpTestingController;

    const route = { data: of({ courseId: course1.id }), children: [] } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([{ path: 'courses/:courseId/exams/:examId', component: DummyComponent }]), FaIconComponent],
            declarations: [
                CoursesComponent,
                MockDirective(MockHasAnyAuthorityDirective),
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortDirective),
                MockDirective(SortByDirective),
                MockPipe(ArtemisDatePipe),
                MockPipe(SearchFilterPipe),
                MockComponent(CourseExerciseRowComponent),
                MockComponent(CourseExercisesComponent),
                MockComponent(CourseRegistrationComponent),
                MockComponent(CourseCardComponent),
                MockComponent(SearchFilterComponent),
            ],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: CourseExerciseRowComponent },
                MockProvider(AlertService),
                MockProvider(CourseAccessStorageService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(CoursesComponent);
                component = fixture.componentInstance;
                courseService = TestBed.inject(CourseManagementService);
                courseAccessStorageService = TestBed.inject(CourseAccessStorageService);
                location = TestBed.inject(Location);
                serverDateService = TestBed.inject(ArtemisServerDateService);
                TestBed.inject(AlertService);
                httpMock = TestBed.inject(HttpTestingController);
                fixture.detectChanges();
            });
        router = TestBed.inject(Router);
    });

    afterEach(() => {
        component.ngOnDestroy();
        jest.restoreAllMocks();
    });

    describe('onInit', () => {
        it('should call loadAndFilterCourses on init', () => {
            const loadAndFilterCoursesSpy = jest.spyOn(component, 'loadAndFilterCourses');

            component.ngOnInit();

            expect(loadAndFilterCoursesSpy).toHaveBeenCalledOnce();
        });

        it('should load courses on init', () => {
            const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
            const serverDateServiceSpy = jest.spyOn(serverDateService, 'now');
            findAllForDashboardSpy.mockReturnValue(of(new HttpResponse({ body: coursesDashboard, headers: new HttpHeaders() })));
            serverDateServiceSpy.mockReturnValue(dayjs());

            component.ngOnInit();

            expect(findAllForDashboardSpy).toHaveBeenCalledOnce();
            expect(component.courses).toEqual(courses);
            expect(component.nextRelevantExams).toHaveLength(0);
        });

        it('should handle an empty response body correctly when fetching all courses for dashboard', () => {
            const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');

            const req = httpMock.expectOne({ method: 'GET', url: `api/core/courses/for-dashboard` });
            component.ngOnInit();

            expect(findAllForDashboardSpy).toHaveBeenCalledOnce();
            req.flush(null);
            expect(component.courses).toBeUndefined();
        });

        it('should load exercises on init', () => {
            const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
            const serverDateServiceSpy = jest.spyOn(serverDateService, 'now');

            findAllForDashboardSpy.mockReturnValue(of(new HttpResponse({ body: coursesDashboard, headers: new HttpHeaders() })));
            serverDateServiceSpy.mockReturnValue(dayjs());

            component.ngOnInit();

            expect(component.nextRelevantCourse).toEqual(exercise1.course);
        });

        it('should sort courses into regular and recently accessed after loading', () => {
            const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
            const sortCoursesInRecentlyAccessedAndRegularCoursesSpy = jest.spyOn(component, 'sortCoursesInRecentlyAccessedAndRegularCourses');
            findAllForDashboardSpy.mockReturnValue(of(new HttpResponse({ body: coursesDashboard, headers: new HttpHeaders() })));

            component.ngOnInit();

            expect(findAllForDashboardSpy).toHaveBeenCalledOnce();
            expect(sortCoursesInRecentlyAccessedAndRegularCoursesSpy).toHaveBeenCalledOnce();

            const lastAccessedCourses = [1, 2];
            const recentCoursesSpy = jest.spyOn(courseAccessStorageService, 'getLastAccessedCourses').mockReturnValue(lastAccessedCourses);

            // Test for less than 5 courses
            const courses = [];
            for (let i = 1; i <= 3; i++) {
                const course = { id: i };
                courses.push(course);
            }

            component.courses = courses;
            component.sortCoursesInRecentlyAccessedAndRegularCourses();
            expect(component.regularCourses).toEqual(courses);
            expect(component.recentlyAccessedCourses).toEqual([]);
            expect(recentCoursesSpy).not.toHaveBeenCalled();

            // Test for more than 5 courses
            for (let i = 4; i <= 7; i++) {
                const course = { id: i };
                courses.push(course);
            }
            component.courses = courses;
            component.sortCoursesInRecentlyAccessedAndRegularCourses();
            expect(component.regularCourses).toEqual(courses.slice(2));
            expect(component.recentlyAccessedCourses).toEqual(courses.slice(0, 2));
            expect(recentCoursesSpy).toHaveBeenCalledOnce();
        });
    });

    it('should load next relevant exam', fakeAsync(() => {
        const navigateSpy = jest.spyOn(router, 'navigate');
        component.nextRelevantCourseForExam = course1;
        component.nextRelevantExams = [exam1];
        component.openExam();
        tick();

        expect(navigateSpy).toHaveBeenCalledWith(['courses', 1, 'exams', 3]);
        expect(location.path()).toBe('/courses/1/exams/3');
    }));

    it('should load next relevant exam ignoring test exams', fakeAsync(() => {
        const testExam1 = {
            id: 5,
            startDate: dayjs().add(1, 'hour'),
            endDate: endDate1,
            visibleDate: visibleDate1.subtract(10, 'minutes'),
            course: courseEmpty,
            workingTime: 3600,
            testExam: true,
        };
        const course6 = { id: 3, exams: [testExam1], exercises: [exercise1] };
        const coursesForDashboard = new CoursesForDashboardDTO();
        const courseForDashboard1 = new CourseForDashboardDTO();
        courseForDashboard1.course = course1;
        const courseForDashboard2 = new CourseForDashboardDTO();
        courseForDashboard2.course = course2;
        const courseForDashboard6 = new CourseForDashboardDTO();
        courseForDashboard6.course = course6;
        coursesForDashboard.courses = [courseForDashboard1, courseForDashboard2, courseForDashboard6];

        const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
        const serverDateServiceSpy = jest.spyOn(serverDateService, 'now');
        findAllForDashboardSpy.mockReturnValue(
            of(
                new HttpResponse({
                    body: coursesForDashboard,
                    headers: new HttpHeaders(),
                }),
            ),
        );
        serverDateServiceSpy.mockReturnValue(dayjs());

        component.ngOnInit();
        tick(1000);

        expect(findAllForDashboardSpy).toHaveBeenCalledOnce();
        expect(component.courses).toEqual([course1, course2, course6]);
        expect(component.nextRelevantExams).toEqual([]);
    }));

    it('should initialize search course text correctly', () => {
        const searchedCourse = 'Test Course';
        component.setSearchValue('Test Course');
        expect(searchedCourse).toBe(component.searchCourseText);
    });

    it('should adjust sort direction by clicking on sort icon', () => {
        const findAllForDashboardSpy = jest.spyOn(courseService, 'findAllForDashboard');
        findAllForDashboardSpy.mockReturnValue(of(new HttpResponse({ body: coursesDashboard, headers: new HttpHeaders() })));
        component.ngOnInit();

        expect(findAllForDashboardSpy).toHaveBeenCalledOnce();
        expect(component.courses).toEqual(courses);
        expect(component.isSortAscending).toBeTrue();

        const onSortSpy = jest.spyOn(component, 'onSort');
        const button = fixture.debugElement.nativeElement.querySelector('#test-sort');
        button.click();
        expect(onSortSpy).toHaveBeenCalledOnce();
        expect(component.isSortAscending).toBeFalse();
    });
});
