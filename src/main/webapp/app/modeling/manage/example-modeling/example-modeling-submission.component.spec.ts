import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { delay, of, throwError } from 'rxjs';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ActivatedRoute, ActivatedRouteSnapshot, Router, convertToParamMap } from '@angular/router';
import { ChangeDetectorRef } from '@angular/core';
import { MockComponent, MockProvider } from 'ng-mocks';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackCorrectionError, FeedbackCorrectionErrorType, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { UMLDiagramType, UMLModel } from '@ls1intum/apollon';
import { HttpResponse, provideHttpClient } from '@angular/common/http';
import { AlertService } from 'app/shared/service/alert.service';
import { ExampleModelingSubmissionComponent } from 'app/modeling/manage/example-modeling/example-modeling-submission.component';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { ExampleSubmission } from 'app/assessment/shared/entities/example-submission.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { FaLayersComponent } from '@fortawesome/angular-fontawesome';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { FormsModule } from '@angular/forms';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { ThemeService } from 'app/core/theme/shared/theme.service';
import { MockThemeService } from 'test/helpers/mocks/service/mock-theme.service';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('Example Modeling Submission Component', () => {
    let comp: ExampleModelingSubmissionComponent;
    let fixture: ComponentFixture<ExampleModelingSubmissionComponent>;
    let service: ExampleSubmissionService;
    let alertService: AlertService;
    let router: Router;
    let route: ActivatedRoute;

    const participation = new StudentParticipation();
    participation.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, undefined);
    participation.id = 1;
    const submission = { id: 20, submitted: true, participation } as ModelingSubmission;

    const exampleSubmission: ExampleSubmission = {
        submission,
    };

    const exercise = {
        id: 22,
        diagramType: UMLDiagramType.ClassDiagram,
        course: { id: 2 },
        maxPoints: 30,
    } as ModelingExercise;

    const mockFeedbackWithReference: Feedback = {
        text: 'FeedbackWithReference',
        referenceId: 'relationshipId',
        reference: 'reference',
        credits: 30,
        correctionStatus: 'CORRECT',
    };
    const mockFeedbackWithoutReference: Feedback = {
        text: 'FeedbackWithoutReference',
        credits: 30,
        type: FeedbackType.MANUAL_UNREFERENCED,
    };
    const mockFeedbackInvalid: Feedback = {
        text: 'FeedbackInvalid',
        referenceId: '4',
        reference: 'reference',
        correctionStatus: FeedbackCorrectionErrorType.INCORRECT_SCORE,
    };
    const mockFeedbackCorrectionError: FeedbackCorrectionError = {
        reference: 'reference',
        type: FeedbackCorrectionErrorType.INCORRECT_SCORE,
    };

    const routeQueryParam = { readOnly: 0, toComplete: 0 };

    beforeEach(() => {
        routeQueryParam.readOnly = 0;
        routeQueryParam.toComplete = 0;
        route = {
            snapshot: {
                paramMap: convertToParamMap({ exerciseId: '22', exampleSubmissionId: '35' }),
                queryParamMap: convertToParamMap(routeQueryParam),
            },
        } as ActivatedRoute;

        TestBed.configureTestingModule({
            imports: [FormsModule, FaIconComponent],
            declarations: [
                ExampleModelingSubmissionComponent,
                ModelingAssessmentComponent,
                MockComponent(ModelingEditorComponent),
                MockTranslateValuesDirective,
                MockComponent(FaLayersComponent),
                MockComponent(CollapsableAssessmentInstructionsComponent),
                MockComponent(UnreferencedFeedbackComponent),
                MockComponent(ScoreDisplayComponent),
            ],
            providers: [
                MockProvider(ChangeDetectorRef),
                MockProvider(ArtemisTranslatePipe),
                { provide: Router, useClass: MockRouter },
                { provide: ActivatedRoute, useValue: route },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: ThemeService, useClass: MockThemeService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExampleModelingSubmissionComponent);
                comp = fixture.componentInstance;
                service = TestBed.inject(ExampleSubmissionService);
                alertService = TestBed.inject(AlertService);
                router = TestBed.inject(Router);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        // GIVEN
        jest.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const exerciseService = TestBed.inject(ExerciseService);
        jest.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(comp).toBe(comp);
    });

    it('should handle a new submission', () => {
        route.snapshot = {
            ...route.snapshot,
            paramMap: convertToParamMap({ exerciseId: '22', exampleSubmissionId: 'new' }),
        } as ActivatedRouteSnapshot;

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(comp.isNewSubmission).toBeTrue();
        expect(comp.exampleSubmission).toEqual(new ExampleSubmission());
    });

    it('should upsert a new modeling submission', () => {
        // GIVEN
        const alertSpy = jest.spyOn(alertService, 'success');
        const serviceSpy = jest.spyOn(service, 'create').mockImplementation((newExampleSubmission) => of(new HttpResponse({ body: newExampleSubmission })));
        comp.isNewSubmission = true;
        comp.exercise = exercise;
        // WHEN
        fixture.detectChanges(); // Needed for @ViewChild to set fields.
        comp.upsertExampleModelingSubmission();

        // THEN
        expect(comp.isNewSubmission).toBeFalse();
        expect(serviceSpy).toHaveBeenCalledOnce();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.saveSuccessful');
    });

    it('should upsert an existing modeling submission', fakeAsync(() => {
        // GIVEN
        jest.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const alertSpy = jest.spyOn(alertService, 'success');
        const serviceSpy = jest.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })).pipe(delay(1)));

        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        const modelingAssessmentServiceSpy = jest.spyOn(modelingAssessmentService, 'saveExampleAssessment');

        comp.isNewSubmission = false;
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        fixture.detectChanges();
        comp.upsertExampleModelingSubmission();

        // Ensure calls are not concurrent, new one should start after first one ends.
        expect(serviceSpy).toHaveBeenCalledOnce();
        tick(1);

        // This service request should also start after the previous one ends.
        expect(modelingAssessmentServiceSpy).not.toHaveBeenCalled();
        tick(1);

        // THEN
        expect(comp.isNewSubmission).toBeFalse();
        expect(serviceSpy).toHaveBeenCalledTimes(2);
        expect(modelingAssessmentServiceSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingEditor.saveSuccessful');
    }));

    it('should check assessment', () => {
        // GIVEN
        const tutorParticipationService = TestBed.inject(TutorParticipationService);
        const assessExampleSubmissionSpy = jest.spyOn(tutorParticipationService, 'assessExampleSubmission');
        const exerciseId = 5;
        comp.exampleSubmission = exampleSubmission;
        comp.exerciseId = exerciseId;

        // WHEN
        comp.checkAssessment();

        // THEN
        expect(comp.assessmentsAreValid).toBeTrue();
        expect(assessExampleSubmissionSpy).toHaveBeenCalledOnce();
        expect(assessExampleSubmissionSpy).toHaveBeenCalledWith(exampleSubmission, exerciseId);
    });

    it('should check invalid assessment', () => {
        // GIVEN
        const alertSpy = jest.spyOn(alertService, 'error');
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        comp.onReferencedFeedbackChanged([mockFeedbackInvalid]);
        comp.checkAssessment();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
    });

    it('should read and understood', () => {
        // GIVEN
        const tutorParticipationService = TestBed.inject(TutorParticipationService);
        jest.spyOn(tutorParticipationService, 'assessExampleSubmission').mockReturnValue(of(new HttpResponse({ body: {} })));
        const alertSpy = jest.spyOn(alertService, 'success');
        const routerSpy = jest.spyOn(router, 'navigate');
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        fixture.detectChanges();
        comp.readAndUnderstood();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.exampleSubmission.readSuccessfully');
        expect(routerSpy).toHaveBeenCalledOnce();
    });

    it('should handle referenced feedback change', () => {
        // GIVEN
        const feedbacks = [mockFeedbackWithReference];
        comp.exercise = exercise;

        // WHEN
        comp.onReferencedFeedbackChanged(feedbacks);

        // THEN
        expect(comp.feedbackChanged).toBeTrue();
        expect(comp.assessmentsAreValid).toBeTrue();
        expect(comp.referencedFeedback).toEqual(feedbacks);
    });

    it('should handle unreferenced feedback change', () => {
        // GIVEN
        const feedbacks = [mockFeedbackWithoutReference];
        comp.exercise = exercise;

        // WHEN
        comp.onUnReferencedFeedbackChanged(feedbacks);

        // THEN
        expect(comp.feedbackChanged).toBeTrue();
        expect(comp.assessmentsAreValid).toBeTrue();
        expect(comp.unreferencedFeedback).toEqual(feedbacks);
    });

    it('should show submission', () => {
        // GIVEN
        const feedbacks = [mockFeedbackWithReference];
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        comp.onReferencedFeedbackChanged(feedbacks);
        comp.showSubmission();

        // THEN
        expect(comp.feedbackChanged).toBeFalse();
        expect(comp.assessmentMode).toBeFalse();
        expect(comp.totalScore).toBe(mockFeedbackWithReference.credits);
    });

    it('should create error alert if assessment is invalid', () => {
        // GIVEN
        const alertSpy = jest.spyOn(alertService, 'error');
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackInvalid];

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessment.invalidAssessments');
    });

    it('should update assessment explanation and example assessment', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = { ...exampleSubmission, assessmentExplanation: 'Explanation of the assessment' };
        comp.referencedFeedback = [mockFeedbackWithReference];
        comp.unreferencedFeedback = [mockFeedbackWithoutReference];

        const result = { id: 1 } as Result;
        const alertSpy = jest.spyOn(alertService, 'success');
        jest.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(of(result));

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(comp.result).toBe(result);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
    });

    it('should update assessment explanation but create error message on example assessment update failure', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = { ...exampleSubmission, assessmentExplanation: 'Explanation of the assessment' };
        comp.referencedFeedback = [mockFeedbackWithReference, mockFeedbackWithoutReference];

        const alertSpy = jest.spyOn(alertService, 'error');
        jest.spyOn(service, 'update').mockImplementation((updatedExampleSubmission) => of(new HttpResponse({ body: updatedExampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(throwError(() => ({ status: 404 })));

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(comp.result).toBeUndefined();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveFailed');
    });

    it('should mark all feedback correct', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackInvalid];
        comp.assessmentMode = true;

        // WHEN
        comp.showAssessment();
        fixture.detectChanges();
        const resultFeedbacksSetterSpy = jest.spyOn(comp.assessmentEditor, 'resultFeedbacks', 'set');
        comp.markAllFeedbackToCorrect();
        fixture.detectChanges();

        // THEN
        expect(comp.referencedFeedback.every((feedback) => feedback.correctionStatus === 'CORRECT')).toBeTrue();
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledOnce();
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledWith(comp.referencedFeedback);
    });

    it('should mark all feedback wrong', () => {
        // GIVEN
        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackInvalid];
        comp.assessmentMode = true;

        // WHEN
        comp.showAssessment();
        fixture.detectChanges();
        const resultFeedbacksSetterSpy = jest.spyOn(comp.assessmentEditor, 'resultFeedbacks', 'set');
        comp.markWrongFeedback([mockFeedbackCorrectionError]);
        fixture.detectChanges();
        // THEN
        expect(comp.referencedFeedback[0].correctionStatus).toBe(mockFeedbackCorrectionError.type);
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledOnce();
        expect(resultFeedbacksSetterSpy).toHaveBeenCalledWith(comp.referencedFeedback);
    });

    it('should create success alert on example assessment update', () => {
        // GIVEN
        const result = { id: 1 } as Result;
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(of(result));
        const alertSpy = jest.spyOn(alertService, 'success');

        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackWithReference];

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        comp.result = result;
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
    });

    it('should create error alert on example assessment update failure', () => {
        // GIVEN
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        jest.spyOn(modelingAssessmentService, 'saveExampleAssessment').mockReturnValue(throwError(() => ({ status: 404 })));
        const alertSpy = jest.spyOn(alertService, 'error');

        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;
        comp.referencedFeedback = [mockFeedbackWithReference];

        // WHEN
        comp.saveExampleAssessment();

        // THEN
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.modelingAssessmentEditor.messages.saveFailed');
    });

    it('should handle explanation change', () => {
        // GIVEN
        const explanation = 'New Explanation';

        // WHEN
        comp.explanationChanged(explanation);

        // THEN
        expect(comp.explanationText).toBe(explanation);
    });

    it('should show assessment', () => {
        // GIVEN
        const model = {
            version: '3.0.0',
            type: 'ClassDiagram',
            elements: {},
            relationships: {},
            assessments: {},
            interactive: {
                elements: {},
                relationships: {},
            },
            size: { width: 0, height: 0 },
        } as UMLModel;

        const result = { id: 1 } as Result;

        jest.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        const assessmentSpy = jest.spyOn(modelingAssessmentService, 'getExampleAssessment').mockReturnValue(of(result));

        comp.exercise = exercise;
        comp.exampleSubmission = exampleSubmission;

        // WHEN
        fixture.detectChanges();
        jest.spyOn(comp.modelingEditor, 'getCurrentModel').mockReturnValue(model);

        comp.showAssessment();

        // THEN
        expect(assessmentSpy).toHaveBeenCalledOnce();
        expect(comp.assessmentMode).toBeTrue();
        expect(result.feedbacks).toEqual(comp.assessments);
    });

    it('should call get exampleAssessment in toComplete mode', () => {
        routeQueryParam.toComplete = 1;

        const result = { id: 1 } as Result;
        const feedbackOne = { id: 1, type: FeedbackType.MANUAL_UNREFERENCED } as Feedback;
        const feedbackTwo = { id: 2, type: FeedbackType.MANUAL } as Feedback;
        result.feedbacks = [feedbackOne, feedbackTwo];

        jest.spyOn(service, 'get').mockReturnValue(of(new HttpResponse({ body: exampleSubmission })));
        const modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
        const assessmentSpy = jest.spyOn(modelingAssessmentService, 'getExampleAssessment').mockReturnValue(of(result));

        // WHEN
        fixture.detectChanges();

        expect(assessmentSpy).toHaveBeenCalledOnce();
        expect(comp.referencedExampleFeedback).toEqual([feedbackTwo]);
    });
});
