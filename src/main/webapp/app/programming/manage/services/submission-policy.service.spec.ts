import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { LockRepositoryPolicy, SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { take } from 'rxjs/operators';
import { provideHttpClient } from '@angular/common/http';

describe('Submission Policy Service', () => {
    let httpMock: HttpTestingController;
    let submissionPolicyService: SubmissionPolicyService;
    let lockRepositoryPolicy: LockRepositoryPolicy;
    let programmingExercise: ProgrammingExercise;
    const expectedUrl = 'api/programming/programming-exercises/1/submission-policy';
    const statusOk = { status: 200 };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: SubmissionPolicyService, useClass: SubmissionPolicyService }],
        });
        httpMock = TestBed.inject(HttpTestingController);
        submissionPolicyService = TestBed.inject(SubmissionPolicyService);
        lockRepositoryPolicy = { type: SubmissionPolicyType.LOCK_REPOSITORY, submissionLimit: 5 } as LockRepositoryPolicy;
        programmingExercise = new ProgrammingExercise(undefined, undefined);
        programmingExercise.id = 1;
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('Invoke submission policy service methods', () => {
        it('should add submission policy to exercise', fakeAsync(() => {
            const addPolicySubscription = submissionPolicyService
                .addSubmissionPolicyToProgrammingExercise(lockRepositoryPolicy, programmingExercise.id!)
                .pipe(take(1))
                .subscribe((submissionPolicy) => {
                    expect(submissionPolicy).toBe(lockRepositoryPolicy);
                });
            tick();

            const request = httpMock.expectOne({ method: 'POST', url: expectedUrl });
            expect(lockRepositoryPolicy.active).toBeFalse();
            request.flush(lockRepositoryPolicy);
            tick();

            addPolicySubscription.unsubscribe();
        }));

        it('should update submission policy of exercise', fakeAsync(() => {
            const addPolicySubscription = submissionPolicyService
                .updateSubmissionPolicyToProgrammingExercise(lockRepositoryPolicy, programmingExercise.id!)
                .pipe(take(1))
                .subscribe((submissionPolicy) => {
                    expect(submissionPolicy).toBe(lockRepositoryPolicy);
                });
            tick();

            const request = httpMock.expectOne({ method: 'PATCH', url: expectedUrl });
            request.flush(lockRepositoryPolicy);
            tick();

            addPolicySubscription.unsubscribe();
        }));

        // Using functions to avoid a serialization error
        it.each([() => ({ input: null, expected: undefined }), () => ({ input: lockRepositoryPolicy, expected: lockRepositoryPolicy })])(
            'should get submission policy from exercise',
            fakeAsync((fun: any) => {
                const { input, expected } = fun();
                const getPolicySubscription = submissionPolicyService
                    .getSubmissionPolicyOfProgrammingExercise(programmingExercise.id!)
                    .pipe(take(1))
                    .subscribe((submissionPolicy) => {
                        expect(submissionPolicy).toBe(expected);
                    });
                tick();

                const request = httpMock.expectOne({ method: 'GET', url: expectedUrl });
                request.flush(input);
                tick();

                getPolicySubscription.unsubscribe();
            }),
        );

        it('should issue delete request', fakeAsync(() => {
            const removePolicySubscription = submissionPolicyService.removeSubmissionPolicyFromProgrammingExercise(programmingExercise.id!).subscribe((response) => {
                expect(response.ok).toBeTrue();
            });
            tick();

            const request = httpMock.expectOne({ method: 'DELETE', url: expectedUrl });
            request.flush(statusOk);
            tick();

            removePolicySubscription.unsubscribe();
        }));

        it('should issue enable request', fakeAsync(() => {
            const removePolicySubscription = submissionPolicyService.enableSubmissionPolicyOfProgrammingExercise(programmingExercise.id!).subscribe((response) => {
                expect(response.ok).toBeTrue();
            });
            tick();

            const request = httpMock.expectOne({ method: 'PUT', url: expectedUrl + '?activate=true' });
            request.flush(statusOk);
            tick();
            removePolicySubscription.unsubscribe();
        }));

        it('should issue disable request', fakeAsync(() => {
            const removePolicySubscription = submissionPolicyService.disableSubmissionPolicyOfProgrammingExercise(programmingExercise.id!).subscribe((response) => {
                expect(response.ok).toBeTrue();
            });
            tick();

            const request = httpMock.expectOne({ method: 'PUT', url: expectedUrl + '?activate=false' });
            request.flush(statusOk);
            tick();
            removePolicySubscription.unsubscribe();
        }));
    });
});
