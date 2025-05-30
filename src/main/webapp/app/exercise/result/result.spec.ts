import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ResultComponent } from 'app/exercise/result/result.component';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisTimeAgoPipe } from 'app/shared/pipes/artemis-time-ago.pipe';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { cloneDeep } from 'lodash-es';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCheckCircle, faQuestionCircle, faTimesCircle } from '@fortawesome/free-regular-svg-icons';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ResultTemplateStatus } from 'app/exercise/result/result.utils';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { MIN_SCORE_GREEN, MIN_SCORE_ORANGE } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';

describe('ResultComponent', () => {
    let fixture: ComponentFixture<ResultComponent>;
    let component: ResultComponent;

    const result: Result = { id: 0, submission: { id: 1, participation: { id: 1, exercise: { type: ExerciseType.PROGRAMMING } as Exercise } } };
    const programmingExercise: ProgrammingExercise = {
        id: 1,
        type: ExerciseType.PROGRAMMING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const programmingParticipation: ProgrammingExerciseStudentParticipation = { id: 2, type: ParticipationType.PROGRAMMING, exercise: programmingExercise };

    const modelingExercise: ModelingExercise = {
        id: 3,
        type: ExerciseType.MODELING,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };
    const modelingParticipation: StudentParticipation = { id: 4, type: ParticipationType.STUDENT, exercise: modelingExercise };

    const textExercise: TextExercise = {
        id: 5,
        type: ExerciseType.TEXT,
        numberOfAssessmentsOfCorrectionRounds: [],
        secondCorrectionEnabled: false,
        studentAssignedTeamIdComputed: false,
    };

    const textParticipation: StudentParticipation = { id: 6, type: ParticipationType.STUDENT, exercise: textExercise };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip)],
            declarations: [ResultComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisTimeAgoPipe), MockPipe(ArtemisDatePipe), MockDirective(TranslateDirective)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ResultComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        component.result = result;
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });
    it('should set results for programming exercise', () => {
        const submission1: Submission = { id: 1, participation: programmingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const participation1 = cloneDeep(programmingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];

        component.participation = participation1;
        component.showUngradedResults = true;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-secondary');
        expect(component.resultIconClass).toEqual(faQuestionCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.programmingShort (artemisApp.result.preliminary)');
    });

    it('should set results for modeling exercise', () => {
        const submission1: Submission = { id: 1, participation: modelingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 1 };
        const participation1 = cloneDeep(modelingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        component.participation = participation1;
        component.showUngradedResults = true;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-danger');
        expect(component.resultIconClass).toEqual(faTimesCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.short');
        expect(component.templateStatus).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it('should set (automatic athena) results for modeling exercise', () => {
        const submission1: Submission = { id: 1, participation: modelingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 0.8, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true };
        const participation1 = cloneDeep(modelingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        component.participation = participation1;
        component.showUngradedResults = true;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-secondary');
        expect(component.resultIconClass).toEqual(faCheckCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.short (artemisApp.result.preliminary)');
        expect(component.templateStatus).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it('should set (automatic athena) results for programming exercise', () => {
        const submission1: Submission = { id: 1, participation: programmingParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 0.8, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true };
        const participation1 = cloneDeep(programmingParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        component.participation = participation1;
        component.showUngradedResults = true;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-secondary');
        expect(component.resultIconClass).toEqual(faCheckCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.automaticAIFeedbackSuccessful (artemisApp.result.preliminary)');
        expect(component.templateStatus).toBe(ResultTemplateStatus.HAS_RESULT);
    });

    it('should set (automatic athena) results for text exercise', () => {
        const submission1: Submission = { id: 1, participation: textParticipation };
        const result1: Result = { id: 1, submission: submission1, score: 1, assessmentType: AssessmentType.AUTOMATIC_ATHENA, successful: true };
        const participation1 = cloneDeep(textParticipation);
        participation1.submissions = [submission1];
        submission1.results = [result1];
        component.participation = participation1;
        component.showUngradedResults = true;

        fixture.detectChanges();

        expect(component.result).toEqual(result1);
        expect(component.submission).toEqual(submission1);
        expect(component.textColorClass).toBe('text-secondary');
        expect(component.resultIconClass).toEqual(faCheckCircle);
        expect(component.resultString).toBe('artemisApp.result.resultString.short (artemisApp.result.preliminary)');
    });

    it.each([
        // never show icon in long format, the text already contains the relevant information
        { short: false, score: MIN_SCORE_ORANGE - 3, codeIssues: 1, iconShown: false },
        { short: false, score: MIN_SCORE_ORANGE, codeIssues: 1, iconShown: false },
        { short: false, score: MIN_SCORE_GREEN, codeIssues: 2, iconShown: false },
        // show independent of score
        { short: true, score: MIN_SCORE_ORANGE - 3, codeIssues: 1, iconShown: true },
        { short: true, score: MIN_SCORE_ORANGE, codeIssues: 1, iconShown: true },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 2, iconShown: true },
        // show only if code issues exist
        { short: true, score: MIN_SCORE_GREEN, codeIssues: undefined, iconShown: false },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 0, iconShown: false },
        { short: true, score: MIN_SCORE_GREEN, codeIssues: 10, iconShown: true },
    ])('should show a warning icon if code issues exist (%s)', ({ short, score, codeIssues, iconShown }) => {
        let submission: Submission = { id: 1 };
        const result: Result = {
            id: 3,
            submission,
            score,
            testCaseCount: 2,
            codeIssueCount: codeIssues,
            completionDate: dayjs().subtract(2, 'minutes'),
        };
        submission = { ...submission, results: [result] };
        const participation = cloneDeep(programmingParticipation);
        participation.submissions = [submission];
        component.short = short;
        component.participation = participation;
        fixture.detectChanges();

        const warningIcon = fixture.debugElement.nativeElement.querySelector('#code-issue-warnings-icon');
        if (iconShown) {
            expect(warningIcon).toBeDefined();
        } else {
            expect(warningIcon).toBeNull();
        }
    });
});
