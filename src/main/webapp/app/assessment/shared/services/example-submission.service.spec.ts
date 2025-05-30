import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { take } from 'rxjs/operators';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Submission, getLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { StringCountService } from 'app/text/overview/service/string-count.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { MockProvider } from 'ng-mocks';

describe('Example Submission Service', () => {
    let httpMock: HttpTestingController;
    let service: ExampleSubmissionService;
    let expectedResult: any;
    let elemDefault: ExampleSubmission;
    let studentSubmission: Submission;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: ExerciseService, useClass: MockExerciseService }, MockProvider(StringCountService)],
        });
        service = TestBed.inject(ExampleSubmissionService);
        httpMock = TestBed.inject(HttpTestingController);

        expectedResult = {} as HttpResponse<ExampleSubmission[]>;
        elemDefault = new ExampleSubmission();
        elemDefault.id = 1;
        elemDefault.usedForTutorial = false;
        elemDefault.exercise = {
            id: 1,
            problemStatement: 'problem statement',
            title: 'title',
            shortName: 'titleShort',
        } as unknown as Exercise;
        elemDefault.submission = {
            id: 1,
            submitted: true,
            type: 'AUTOMATIC',
            text: 'Test\n\nTest\n\nTest',
        } as unknown as TextSubmission;
        elemDefault.submission.results = [
            {
                id: 2374,
                score: 8,
                rated: true,
                hasComplaint: false,
            } as unknown as Result,
        ];
        getLatestSubmissionResult(elemDefault.submission)!.feedbacks = [
            {
                id: 2,
                detailText: 'Feedback',
                credits: 1,
            } as Feedback,
        ];
        elemDefault.assessmentExplanation = 'exampleSubmissionTest';
        studentSubmission = elemDefault.submission;
    });

    describe('Service methods', () => {
        it('should create an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault, id: 0 };
            const expected = { ...returnedFromService };
            service
                .create(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));

        it('should update an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .update(elemDefault, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'PUT' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));

        it('should delete an example submission', fakeAsync(() => {
            const exampleSubmissionId = 1;
            service.delete(exampleSubmissionId).subscribe((resp) => (expectedResult = resp.ok));

            const req = httpMock.expectOne({ method: 'DELETE' });
            req.flush({ status: 200 });
            tick();
            expect(expectedResult).toBeTrue();
        }));

        it('should return an example submission', fakeAsync(() => {
            const exampleSubmissionId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .get(exampleSubmissionId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'GET' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));

        it('should import an example submission', fakeAsync(() => {
            const exerciseId = 1;
            const returnedFromService = { ...elemDefault };
            const expected = { ...returnedFromService };
            service
                .import(studentSubmission.id!, exerciseId)
                .pipe(take(1))
                .subscribe((resp) => (expectedResult = resp));
            const req = httpMock.expectOne({ method: 'POST' });
            req.flush(returnedFromService);
            tick();
            expect(expectedResult.body).toEqual(expected);
        }));
    });
});
