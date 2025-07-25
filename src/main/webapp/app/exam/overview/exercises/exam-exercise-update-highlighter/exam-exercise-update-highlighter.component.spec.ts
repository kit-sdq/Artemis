import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { BehaviorSubject } from 'rxjs';
import { ExamExerciseUpdate, ExamExerciseUpdateService } from 'app/exam/manage/services/exam-exercise-update.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamExerciseUpdateHighlighterComponent } from 'app/exam/overview/exercises/exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';

describe('ExamExerciseUpdateHighlighterComponent', () => {
    let fixture: ComponentFixture<ExamExerciseUpdateHighlighterComponent>;
    let component: ExamExerciseUpdateHighlighterComponent;

    let examExerciseIdAndProblemStatementSourceMock: BehaviorSubject<ExamExerciseUpdate>;
    let mockExamExerciseUpdateService: any;

    const oldProblemStatement = 'problem statement with errors';
    const updatedProblemStatement = 'new updated ProblemStatement';
    beforeEach(async () => {
        const textExerciseDummy = { id: 42, problemStatement: oldProblemStatement } as Exercise;
        examExerciseIdAndProblemStatementSourceMock = new BehaviorSubject<ExamExerciseUpdate>({ exerciseId: -1, problemStatement: 'initialProblemStatementValue' });
        mockExamExerciseUpdateService = {
            currentExerciseIdAndProblemStatement: examExerciseIdAndProblemStatementSourceMock.asObservable(),
        };

        await TestBed.configureTestingModule({
            declarations: [MockPipe(ArtemisTranslatePipe), ExamExerciseUpdateHighlighterComponent],
            providers: [{ provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamExerciseUpdateHighlighterComponent);
                component = fixture.componentInstance;

                fixture.componentRef.setInput('exercise', textExerciseDummy);
                const exerciseId = component.exercise().id!;
                const update = { exerciseId, problemStatement: updatedProblemStatement };

                fixture.detectChanges();
                examExerciseIdAndProblemStatementSourceMock.next(update);
            });
    });

    it('should update problem statement', () => {
        const result = component.updatedProblemStatement;
        expect(result).toEqual(updatedProblemStatement);
        expect(result).not.toEqual(oldProblemStatement);
    });

    it('should highlight differences', () => {
        const result =
            '<p><del class="diffmod">problem</del><ins class="diffmod">new</ins> <del class="diffmod">statement</del><ins class="diffmod">updated</ins> <del class="diffmod">with errors</del><ins class="diffmod">ProblemStatement</ins></p>';
        expect((component.updatedProblemStatementWithHighlightedDifferencesHTML as any).changingThisBreaksApplicationSecurity).toEqual(result);
    });

    it('should display different problem statement after toggle method is called', () => {
        const mouseEvent = new MouseEvent('click');
        const stopPropagationSpy = jest.spyOn(mouseEvent, 'stopPropagation');
        const problemStatementBeforeClick = htmlForMarkdown(component.exercise().problemStatement);
        expect((component.updatedProblemStatementHTML as any).changingThisBreaksApplicationSecurity).toEqual(problemStatementBeforeClick);

        component.toggleHighlightedProblemStatement(mouseEvent);

        const problemStatementAfterClick = component.exercise().problemStatement;
        expect(problemStatementAfterClick).toEqual(updatedProblemStatement);
        expect(problemStatementAfterClick).not.toEqual(component.updatedProblemStatementWithHighlightedDifferencesHTML);
        expect(problemStatementAfterClick).not.toEqual(problemStatementBeforeClick);
        expect(stopPropagationSpy).toHaveBeenCalledOnce();
    });

    describe('ExamExerciseUpdateHighlighterComponent for programming exercises', () => {
        const programmingExerciseDummy = { id: 42, problemStatement: oldProblemStatement, type: ExerciseType.PROGRAMMING } as Exercise;
        beforeEach(async () => {
            return TestBed.configureTestingModule({
                declarations: [MockPipe(ArtemisTranslatePipe), ExamExerciseUpdateHighlighterComponent],
                providers: [{ provide: ExamExerciseUpdateService, useValue: mockExamExerciseUpdateService }],
            })
                .compileComponents()
                .then(() => {
                    fixture = TestBed.createComponent(ExamExerciseUpdateHighlighterComponent);
                    component = fixture.componentInstance;

                    fixture.componentRef.setInput('exercise', programmingExerciseDummy);
                    const exerciseId = component.exercise().id!;
                    const update = { exerciseId, problemStatement: updatedProblemStatement };

                    fixture.detectChanges();
                    examExerciseIdAndProblemStatementSourceMock.next(update);
                });
        });
        it('should not highlight differences for programming exercise', () => {
            // For programming exercises, the highlighting of differences is handled in the programming-exercise-instruction.component.ts.
            // Therefore, the highlightProblemStatementDifferences method is not called and updatedProblemStatementWithHighlightedDifferencesHTML
            // and updatedProblemStatementHTML remain undefined
            const highlightDifferencesSpy = jest.spyOn(component, 'highlightProblemStatementDifferences');
            expect(highlightDifferencesSpy).not.toHaveBeenCalled();
            expect(component.updatedProblemStatementWithHighlightedDifferencesHTML).toBeUndefined();
            expect(component.updatedProblemStatementHTML).toBeUndefined();
        });
    });
});
