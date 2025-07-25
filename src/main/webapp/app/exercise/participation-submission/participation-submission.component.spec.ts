import { ComponentFixture, TestBed, fakeAsync, flush, tick } from '@angular/core/testing';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { AccountService } from 'app/core/auth/account.service';
import dayjs from 'dayjs/esm';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { of, throwError } from 'rxjs';
import { UnreferencedFeedbackDetailComponent } from 'app/assessment/manage/unreferenced-feedback-detail/unreferenced-feedback-detail.component';
import { DebugElement } from '@angular/core';
import { By } from '@angular/platform-browser';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { ParticipationSubmissionComponent } from 'app/exercise/participation-submission/participation-submission.component';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { MockComplaintService } from 'test/helpers/mocks/service/mock-complaint.service';
import { ComplaintsForTutorComponent } from 'app/assessment/manage/complaints-for-tutor/complaints-for-tutor.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { Submission, SubmissionExerciseType, SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { TemplateProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/template-programming-exercise-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { SolutionProgrammingExerciseParticipation } from 'app/exercise/shared/entities/participation/solution-programming-exercise-participation.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ResultComponent } from 'app/exercise/result/result.component';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('ParticipationSubmissionComponent', () => {
    let comp: ParticipationSubmissionComponent;
    let fixture: ComponentFixture<ParticipationSubmissionComponent>;
    let participationService: ParticipationService;
    let submissionService: SubmissionService;
    let textAssessmentService: TextAssessmentService;
    let fileUploadAssessmentService: FileUploadAssessmentService;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let modelingAssessmentService: ModelingAssessmentService;
    let deleteFileUploadAssessmentStub: jest.SpyInstance;
    let deleteModelingAssessmentStub: jest.SpyInstance;
    let deleteTextAssessmentStub: jest.SpyInstance;
    let deleteProgrammingAssessmentStub: jest.SpyInstance;
    let exerciseService: ExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;
    let findAllSubmissionsOfParticipationStub: jest.SpyInstance;
    let debugElement: DebugElement;
    let router: Router;
    const route = () => ({ params: of({ participationId: 1, exerciseId: 42 }) });

    const result1 = { id: 44 } as Result;
    const result2 = { id: 45 } as Result;
    const participation1 = { id: 66 } as Participation;
    const submissionWithTwoResults = { id: 77, results: [result1, result2], participation: participation1 } as Submission;
    const submissionWithTwoResults2 = {
        id: 78,
        results: [result1, result2],
        participation: participation1,
    } as Submission;

    const programmingExercise1 = { id: 100, type: ExerciseType.PROGRAMMING } as Exercise;
    const modelingExercise = { id: 100, type: ExerciseType.MODELING } as Exercise;
    const fileUploadExercise = { id: 100, type: ExerciseType.FILE_UPLOAD } as Exercise;
    const textExercise = { id: 100, type: ExerciseType.TEXT } as Exercise;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [NgxDatatableModule, RouterModule.forRoot([]), FaIconComponent],
            declarations: [
                ParticipationSubmissionComponent,
                MockComponent(UpdatingResultComponent),
                MockComponent(UnreferencedFeedbackDetailComponent),
                MockComponent(ComplaintsForTutorComponent),
                MockTranslateValuesDirective,
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisTimeAgoPipe),
                MockDirective(DeleteButtonDirective),
                MockComponent(ResultComponent),
            ],
            providers: [
                JhiLanguageHelper,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: ComplaintService, useClass: MockComplaintService },
                { provide: ActivatedRoute, useValue: route() },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ParticipationSubmissionComponent);
                comp = fixture.componentInstance;
                comp.participationId = 1;
                debugElement = fixture.debugElement;
                router = TestBed.inject(Router);
                participationService = TestBed.inject(ParticipationService);
                submissionService = TestBed.inject(SubmissionService);
                textAssessmentService = TestBed.inject(TextAssessmentService);
                modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
                programmingAssessmentService = TestBed.inject(ProgrammingAssessmentManualResultService);
                fileUploadAssessmentService = TestBed.inject(FileUploadAssessmentService);

                exerciseService = TestBed.inject(ExerciseService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                findAllSubmissionsOfParticipationStub = jest.spyOn(submissionService, 'findAllSubmissionsOfParticipation');

                deleteFileUploadAssessmentStub = jest.spyOn(fileUploadAssessmentService, 'deleteAssessment');
                deleteProgrammingAssessmentStub = jest.spyOn(programmingAssessmentService, 'deleteAssessment');
                deleteModelingAssessmentStub = jest.spyOn(modelingAssessmentService, 'deleteAssessment');
                deleteTextAssessmentStub = jest.spyOn(textAssessmentService, 'deleteAssessment');
                fixture.ngZone!.run(() => router.initialNavigation());
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('Submissions are correctly loaded from server', fakeAsync(() => {
        // set all attributes for comp
        const participation = new StudentParticipation();
        participation.id = 1;
        jest.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation })));
        const submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.TEXT,
                id: 2278,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                text: 'My TextSubmission',
                participation,
            },
        ] as TextSubmission[];
        const exercise = { type: ExerciseType.TEXT } as Exercise;
        exercise.isAtLeastInstructor = true;
        jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));
        findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: submissions }));

        const getBuildJobIdsForResultsOfParticipationStub = jest.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation');
        getBuildJobIdsForResultsOfParticipationStub.mockReturnValue(of({ '4': '2' }));

        fixture.detectChanges();
        tick();

        expect(comp.isLoading).toBeFalse();
        // check if findAllSubmissionsOfParticipationStub() is called and works
        expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
        expect(comp.participation).toEqual(participation);
        expect(comp.submissions).toEqual(submissions);
        expect(comp.participation?.submissions).toEqual(submissions);

        // check if delete button is available
        const deleteButton = debugElement.query(By.css('#deleteButton'));
        expect(deleteButton).not.toBeNull();

        // check if the right amount of rows is visible
        const row = debugElement.query(By.css('#participationSubmissionTable'));
        expect(row.nativeElement.children).toHaveLength(1);

        fixture.destroy();
        flush();
    }));

    it('Template Submission is correctly loaded', fakeAsync(() => {
        TestBed.inject(ActivatedRoute).params = of({ participationId: 2, exerciseId: 42 });
        TestBed.inject(ActivatedRoute).queryParams = of({ isTmpOrSolutionProgrParticipation: 'true' });
        const templateParticipation = new TemplateProgrammingExerciseParticipation();
        templateParticipation.id = 2;
        templateParticipation.submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
                id: 3,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                commitHash: '123456789',
                participation: templateParticipation,
            },
        ] as ProgrammingSubmission[];
        const programmingExercise = {
            type: ExerciseType.PROGRAMMING,
            projectKey: 'SUBMISSION1',
            templateParticipation,
        } as ProgrammingExercise;
        const findWithTemplateAndSolutionParticipationStub = jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation');
        findWithTemplateAndSolutionParticipationStub.mockReturnValue(of(new HttpResponse({ body: programmingExercise })));

        const getBuildJobIdsForResultsOfParticipationStub = jest.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation');
        getBuildJobIdsForResultsOfParticipationStub.mockReturnValue(of({ '4': '2' }));

        fixture.detectChanges();
        tick();

        expect(comp.isLoading).toBeFalse();
        expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
        expect(comp.exercise).toEqual(programmingExercise);
        expect(comp.participation).toEqual(templateParticipation);
        expect(comp.submissions).toEqual(templateParticipation.submissions);

        fixture.destroy();
        flush();
    }));

    it('Solution Submission is correctly loaded', fakeAsync(() => {
        TestBed.inject(ActivatedRoute).params = of({ participationId: 3, exerciseId: 42 });
        TestBed.inject(ActivatedRoute).queryParams = of({ isTmpOrSolutionProgrParticipation: 'true' });
        const solutionParticipation = new SolutionProgrammingExerciseParticipation();
        solutionParticipation.id = 3;
        solutionParticipation.submissions = [
            {
                submissionExerciseType: SubmissionExerciseType.PROGRAMMING,
                id: 4,
                submitted: true,
                type: SubmissionType.MANUAL,
                submissionDate: dayjs('2019-07-09T10:47:33.244Z'),
                commitHash: '123456789',
                participation: solutionParticipation,
            },
        ] as ProgrammingSubmission[];
        const programmingExercise = {
            type: ExerciseType.PROGRAMMING,
            projectKey: 'SUBMISSION1',
            solutionParticipation,
        } as ProgrammingExercise;
        const findWithTemplateAndSolutionParticipationStub = jest.spyOn(programmingExerciseService, 'findWithTemplateAndSolutionParticipation');
        findWithTemplateAndSolutionParticipationStub.mockReturnValue(of(new HttpResponse({ body: programmingExercise })));

        const getBuildJobIdsForResultsOfParticipationStub = jest.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation');
        getBuildJobIdsForResultsOfParticipationStub.mockReturnValue(of({ '4': '2' }));

        fixture.detectChanges();
        tick();

        expect(comp.isLoading).toBeFalse();
        expect(findWithTemplateAndSolutionParticipationStub).toHaveBeenCalledOnce();
        expect(comp.participation).toEqual(solutionParticipation);
        expect(comp.submissions).toEqual(solutionParticipation.submissions);

        fixture.destroy();
        flush();
    }));

    describe('should delete', () => {
        beforeEach(() => {
            deleteFileUploadAssessmentStub.mockReturnValue(of({}));
            deleteTextAssessmentStub.mockReturnValue(of({}));
            deleteModelingAssessmentStub.mockReturnValue(of({}));
            deleteProgrammingAssessmentStub.mockReturnValue(of({}));
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submissionWithTwoResults] }));
            jest.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation1 })));
            const getBuildJobIdsForResultsOfParticipationStub = jest.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation');
            getBuildJobIdsForResultsOfParticipationStub.mockReturnValue(of({ '4': '2' }));
        });

        it('should delete result of fileUploadSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            deleteResult(submissionWithTwoResults, result2);
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(1);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));

        it('should delete result of modelingSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
            deleteResult(submissionWithTwoResults, result2);
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(1);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));

        it('should delete result of programmingSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
            deleteResult(submissionWithTwoResults, result2);
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(1);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));

        it('should delete result of textSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            fixture.detectChanges();
            tick();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            expect(comp.submissions![0].results![0].submission).toEqual(submissionWithTwoResults);
            comp.deleteResult(submissionWithTwoResults, result2);
            tick();
            fixture.destroy();
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(1);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));
    });

    describe('should handle failed delete', () => {
        beforeEach(() => {
            const error = { message: '400 error', error: { message: 'error.hasComplaint' } } as HttpErrorResponse;
            deleteFileUploadAssessmentStub.mockReturnValue(throwError(() => error));
            deleteProgrammingAssessmentStub.mockReturnValue(throwError(() => error));
            deleteModelingAssessmentStub.mockReturnValue(throwError(() => error));
            deleteTextAssessmentStub.mockReturnValue(throwError(() => error));
            findAllSubmissionsOfParticipationStub.mockReturnValue(of({ body: [submissionWithTwoResults2] }));
            jest.spyOn(participationService, 'find').mockReturnValue(of(new HttpResponse({ body: participation1 })));
            const getBuildJobIdsForResultsOfParticipationStub = jest.spyOn(participationService, 'getBuildJobIdsForResultsOfParticipation');
            getBuildJobIdsForResultsOfParticipationStub.mockReturnValue(of({ '4': '2' }));
        });

        it('should not delete result of fileUploadSubmission because of server error', fakeAsync(() => {
            const error2 = { message: '403 error', error: { message: 'error.badAuthentication' } } as HttpErrorResponse;
            deleteFileUploadAssessmentStub.mockReturnValue(throwError(() => error2));
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            deleteResult(submissionWithTwoResults, result2);
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(2);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));

        it('should not delete result of fileUploadSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: fileUploadExercise })));
            deleteResult(submissionWithTwoResults, result2);
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(2);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));

        it('should not delete result of modelingSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: modelingExercise })));
            deleteResult(submissionWithTwoResults, result2);
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(2);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));

        it('should not delete result of programmingSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: programmingExercise1 })));
            deleteResult(submissionWithTwoResults, result2);
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(2);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));

        it('should not delete result of textSubmission', fakeAsync(() => {
            jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: textExercise })));
            fixture.detectChanges();
            tick();
            expect(findAllSubmissionsOfParticipationStub).toHaveBeenCalledOnce();
            expect(comp.submissions![0].results![0].submission).toEqual(submissionWithTwoResults2);
            comp.deleteResult(submissionWithTwoResults, result2);
            tick();
            fixture.destroy();
            flush();
            expect(comp.submissions).toHaveLength(1);
            expect(comp.submissions![0].results).toHaveLength(2);
            expect(comp.submissions![0].results![0]).toEqual(result1);
        }));
    });

    function deleteResult(submission: Submission, resultToDelete: Result) {
        fixture.detectChanges();
        tick();
        comp.deleteResult(submission, resultToDelete);
        tick();
        fixture.destroy();
    }
});
