import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ExamScoresAverageScoresGraphComponent } from 'app/exam/manage/exam-scores/average-scores-graph/exam-scores-average-scores-graph.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AggregatedExerciseGroupResult, AggregatedExerciseResult } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { NgxChartsSingleSeriesDataEntry } from 'app/shared/chart/ngx-charts-datatypes';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LocaleConversionService } from 'app/shared/service/locale-conversion.service';
import { RouterModule } from '@angular/router';

describe('ExamScoresAverageScoresGraphComponent', () => {
    let fixture: ComponentFixture<ExamScoresAverageScoresGraphComponent>;
    let component: ExamScoresAverageScoresGraphComponent;
    let navigateToExerciseMock: jest.SpyInstance;

    const returnValue = {
        exerciseGroupId: 1,
        title: 'Patterns',
        averagePoints: 5,
        averagePercentage: 50,
        maxPoints: 10,
        exerciseResults: [
            {
                exerciseId: 2,
                title: 'StrategyPattern',
                maxPoints: 10,
                averagePoints: 6,
                averagePercentage: 60,
            } as AggregatedExerciseResult,
            {
                exerciseId: 3,
                title: 'BridgePattern',
                maxPoints: 10,
                averagePoints: 4,
                averagePercentage: 40,
            } as AggregatedExerciseResult,
            {
                exerciseId: 4,
                title: 'ProxyPattern',
                maxPoints: 10,
                averagePoints: 2,
                averagePercentage: 20,
            } as AggregatedExerciseResult,
        ],
    } as AggregatedExerciseGroupResult;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [BrowserAnimationsModule, RouterModule.forRoot([])],
            providers: [
                MockProvider(CourseManagementService, {
                    find: () => {
                        return of(new HttpResponse({ body: { accuracyOfScores: 1 } }));
                    },
                }),
                MockProvider(LocaleConversionService, {
                    toLocaleString: (score: number) => {
                        return score.toString();
                    },
                }),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamScoresAverageScoresGraphComponent);
        component = fixture.componentInstance;
        navigateToExerciseMock = jest.spyOn(component, 'navigateToExercise').mockImplementation();

        fixture.componentRef.setInput('averageScores', returnValue);
        fixture.detectChanges();
    });

    it('should set ngx data objects and bar colors correctly', () => {
        const expectedData = [
            { name: 'Patterns', value: 50 },
            { name: '2 StrategyPattern', value: 60 },
            { name: '3 BridgePattern', value: 40 },
            { name: '4 ProxyPattern', value: 20 },
        ];
        const expectedColorDomain = [GraphColors.BLUE, GraphColors.DARK_BLUE, GraphColors.YELLOW, GraphColors.RED];

        executeExpectStatements(expectedData, expectedColorDomain);

        adaptExpectedData(3, GraphColors.YELLOW, expectedColorDomain, expectedData);

        adaptExpectedData(2, GraphColors.RED, expectedColorDomain, expectedData);
    });

    const adaptExpectedData = (averagePoints: number, newColor: string, expectedColorDomain: string[], expectedData: NgxChartsSingleSeriesDataEntry[]) => {
        component.averageScores().averagePoints = averagePoints;
        component.averageScores().averagePercentage = averagePoints * 10;

        expectedColorDomain[0] = newColor;
        expectedData[0].value = averagePoints * 10;
        component.ngxColor.domain = [];
        component.ngxData = [];

        component.ngOnInit();

        executeExpectStatements(expectedData, expectedColorDomain);
    };

    const executeExpectStatements = (expectedData: NgxChartsSingleSeriesDataEntry[], expectedColorDomain: string[]) => {
        expect(component.ngxData).toEqual(expectedData);
        expect(component.ngxColor.domain).toEqual(expectedColorDomain);
    };

    describe('test exercise navigation', () => {
        const event = { name: 'test', value: 3 };
        it('should navigate if event is valid', () => {
            component.lookup['test'] = { exerciseId: 42, exerciseType: ExerciseType.QUIZ };

            component.onSelect(event);

            expect(navigateToExerciseMock).toHaveBeenCalledOnce();
            expect(navigateToExerciseMock).toHaveBeenCalledWith(42, ExerciseType.QUIZ);
        });

        it('should not navigate if exercise id is missing', () => {
            component.lookup['test'] = { exerciseType: ExerciseType.QUIZ };

            component.onSelect(event);

            expect(navigateToExerciseMock).not.toHaveBeenCalled();
        });

        it('should not navigate if exercise type is missing', () => {
            component.lookup['test'] = { exerciseId: 42 };

            component.onSelect(event);

            expect(navigateToExerciseMock).not.toHaveBeenCalled();
        });
    });

    it('should look up absolute value', () => {
        const roundAndPerformLocalConversionSpy = jest.spyOn(component, 'roundAndPerformLocalConversion');
        const updatedCourse = {
            accuracyOfScores: 2,
        };
        fixture.componentRef.setInput('course', updatedCourse);
        component.lookup['test'] = { absoluteValue: 40 };

        const result = component.lookupAbsoluteValue('test');

        expect(result).toBe('40');
        expect(roundAndPerformLocalConversionSpy).toHaveBeenCalledOnce();
        expect(roundAndPerformLocalConversionSpy).toHaveBeenCalledWith(40);
    });
});
