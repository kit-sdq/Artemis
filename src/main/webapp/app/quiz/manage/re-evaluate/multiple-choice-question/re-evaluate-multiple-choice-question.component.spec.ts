import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockBuilder, MockProvider } from 'ng-mocks';
import { ReEvaluateMultipleChoiceQuestionComponent } from 'app/quiz/manage/re-evaluate/multiple-choice-question/re-evaluate-multiple-choice-question.component';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('ReEvaluateMultipleChoiceQuestionComponent', () => {
    let fixture: ComponentFixture<ReEvaluateMultipleChoiceQuestionComponent>;
    let component: ReEvaluateMultipleChoiceQuestionComponent;

    const answer1 = { id: 1 } as AnswerOption;
    const answerBackup = { id: 1, text: 'backup' } as AnswerOption;

    beforeEach(async () => {
        await MockBuilder(ReEvaluateMultipleChoiceQuestionComponent).keep(FaIconComponent).provide(MockProvider(TranslateService));

        fixture = TestBed.createComponent(ReEvaluateMultipleChoiceQuestionComponent);
        component = fixture.componentInstance;

        // Provide the @Inputs
        const question = {
            title: 'Test Question',
            answerOptions: [answer1],
        } as MultipleChoiceQuestion;
        const backupQuestion = {
            title: 'Backup Test Question',
            text: 'Backup Text',
            explanation: 'Backup Explanation',
            hint: 'Backup Hint',
            answerOptions: [answerBackup],
        } as MultipleChoiceQuestion;

        component.question = question;
        component.backupQuestion = backupQuestion;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize component', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
    });

    describe('should react to button presses', () => {
        it('move-up', () => {
            const emitSpy = jest.spyOn(component.questionMoveUp, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('#move-up-button');
            expect(button).not.toBeNull();

            button.click();
            fixture.detectChanges();

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('move-down', () => {
            const emitSpy = jest.spyOn(component.questionMoveDown, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('#move-down-button');
            expect(button).not.toBeNull();

            button.click();
            fixture.detectChanges();

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('delete', () => {
            const emitSpy = jest.spyOn(component.questionDeleted, 'emit');
            const button = fixture.debugElement.nativeElement.querySelector('#delete-button');
            expect(button).not.toBeNull();

            button.click();
            fixture.detectChanges();

            expect(emitSpy).toHaveBeenCalledOnce();
        });

        it('reset', () => {
            const button = fixture.debugElement.nativeElement.querySelector('#reset-button');
            expect(button).not.toBeNull();

            button.click();
            fixture.detectChanges();

            expect(component.question.title).toBe(component.backupQuestion.title);
            expect(component.question.text).toBe(component.backupQuestion.text);
            expect(component.question.explanation).toBe(component.backupQuestion.explanation);
            expect(component.question.hint).toBe(component.backupQuestion.hint);
            expect(component.question.answerOptions).toEqual(component.backupQuestion.answerOptions);
        });
    });

    it('should delete an answer', () => {
        component.deleteAnswer(answer1);
        fixture.detectChanges();

        expect(component.question.answerOptions).toHaveLength(0);
    });

    it('should reset an answer', () => {
        component.resetAnswer(answer1);
        fixture.detectChanges();

        expect(component.question.answerOptions).toEqual(component.backupQuestion.answerOptions);
    });

    it('should invalidate answers', () => {
        component.setAnswerInvalid(answer1);
        fixture.detectChanges();

        expect(component.question.answerOptions).toHaveLength(1);

        const answer = component.question.answerOptions![0];
        expect(answer.invalid).toBeTrue();
        expect(component.isAnswerInvalid(answer)).toBeTrue();
    });

    it('should react to answer option changes', () => {
        const answer = component.question.answerOptions![0];
        component.onAnswerOptionChange('solution[wrong]answer', answer1);
        fixture.detectChanges();

        expect(component.question.answerOptions).toHaveLength(1);

        expect(answer.isCorrect).toBeUndefined();
    });

    it('should react to question changes', () => {
        const questionText = 'new text';

        component.onQuestionChange(questionText);
        fixture.detectChanges();

        expect(component.question.text).toBe(questionText);
    });

    it('should get question text', () => {
        const fakeText = '';

        const text = component.getQuestionText(component.question);
        fixture.detectChanges();

        expect(text).toBe(fakeText);
    });

    it('should change answer isCorrect to true if text is set to correct', () => {
        const answer = component.question.answerOptions![0];
        component.onAnswerOptionChange('[correct] correct option', answer1);
        fixture.detectChanges();
        expect(answer.isCorrect).toBeTrue();
    });

    it('should change answer isCorrect to false if text is set to wrong', () => {
        const answer = component.question.answerOptions![0];
        component.onAnswerOptionChange('[wrong] wrong option', answer1);
        fixture.detectChanges();
        expect(answer.isCorrect).toBeFalse();
    });

    it('should not change answer isCorrect if text is not set to either correct or wrong', () => {
        const answer = component.question.answerOptions![0];
        component.onAnswerOptionChange('[some text] wrong option', answer1);
        fixture.detectChanges();
        expect(component.question.answerOptions).toHaveLength(1);
        expect(answer.isCorrect).toBeUndefined();
    });
});
