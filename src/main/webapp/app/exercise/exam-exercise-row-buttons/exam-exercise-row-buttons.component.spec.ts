import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockTranslateService, TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import dayjs from 'dayjs/esm';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { of, throwError } from 'rxjs';
import { HttpErrorResponse } from '@angular/common/http';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { MockDirective, MockProvider } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { ExamExerciseRowButtonsComponent } from 'app/exercise/exam-exercise-row-buttons/exam-exercise-row-buttons.component';

describe('ExamExerciseRowButtonsComponent', () => {
    const course = { id: 3 } as Course;
    const exam = { id: 4 } as Exam;

    let modelingExerciseService: ModelingExerciseService;
    let textExerciseService: TextExerciseService;
    let quizExerciseService: QuizExerciseService;
    let fileUploadExerciseService: FileUploadExerciseService;
    let programmingExerciseService: ProgrammingExerciseService;

    const modelingExercise = { id: 123, type: ExerciseType.MODELING } as ModelingExercise;
    const textExercise = { id: 234, type: ExerciseType.TEXT } as TextExercise;
    const quizExercise = { id: 345, type: ExerciseType.QUIZ } as QuizExercise;
    const fileUploadExercise = { id: 456, type: ExerciseType.FILE_UPLOAD } as FileUploadExercise;
    const programmingExercise = { id: 963, type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
    const quizResponse = { body: { id: 789, type: ExerciseType.QUIZ, quizQuestions: {} } as QuizExercise };

    let deleteTextExerciseStub: jest.SpyInstance;
    let deleteModelingExerciseStub: jest.SpyInstance;
    let deleteQuizExerciseStub: jest.SpyInstance;
    let deleteFileUploadExerciseStub: jest.SpyInstance;
    let deleteProgrammingExerciseStub: jest.SpyInstance;
    let quizExerciseServiceFindStub: jest.SpyInstance;

    let onDeleteExerciseEmitSpy: jest.SpyInstance;
    let quizExerciseExportSpy: jest.SpyInstance;

    let fixture: ComponentFixture<ExamExerciseRowButtonsComponent>;
    let component: ExamExerciseRowButtonsComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [ExamExerciseRowButtonsComponent, TranslatePipeMock, MockDirective(DeleteButtonDirective), MockRouterLinkDirective],
            providers: [
                MockProvider(TextExerciseService),
                MockProvider(FileUploadExerciseService),
                MockProvider(ProgrammingExerciseService),
                MockProvider(ModelingExerciseService),
                MockProvider(QuizExerciseService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
                MockProvider(EventManager),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamExerciseRowButtonsComponent);
                component = fixture.debugElement.componentInstance;
                textExerciseService = TestBed.inject(TextExerciseService);
                modelingExerciseService = TestBed.inject(ModelingExerciseService);
                fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
                quizExerciseService = TestBed.inject(QuizExerciseService);
                programmingExerciseService = TestBed.inject(ProgrammingExerciseService);
                component.course = course;
                component.exam = exam;

                deleteTextExerciseStub = jest.spyOn(textExerciseService, 'delete');
                deleteModelingExerciseStub = jest.spyOn(modelingExerciseService, 'delete');
                deleteQuizExerciseStub = jest.spyOn(quizExerciseService, 'delete');
                deleteFileUploadExerciseStub = jest.spyOn(fileUploadExerciseService, 'delete');
                deleteProgrammingExerciseStub = jest.spyOn(programmingExerciseService, 'delete');
                quizExerciseServiceFindStub = jest.spyOn(quizExerciseService, 'find');
                onDeleteExerciseEmitSpy = jest.spyOn(component.onDeleteExercise, 'emit');
                quizExerciseExportSpy = jest.spyOn(quizExerciseService, 'exportQuiz');
            });
    });

    describe('isExamOver', () => {
        it('should return true if over', () => {
            component.latestIndividualEndDate = dayjs().subtract(1, 'hours');
            expect(component.isExamOver()).toBeTrue();
        });
        it('should return false if not yet over', () => {
            component.latestIndividualEndDate = dayjs().add(1, 'hours');
            expect(component.isExamOver()).toBeFalse();
        });
        it('should return false if endDate is undefined', () => {
            expect(component.isExamOver()).toBeFalse();
        });
    });
    describe('hasExamStarted', () => {
        it('should return true if started', () => {
            component.exam.startDate = dayjs().subtract(1, 'hours');
            expect(component.hasExamStarted()).toBeTrue();
        });
        it('should return false if not yet started', () => {
            component.exam.startDate = dayjs().add(1, 'hours');
            expect(component.hasExamStarted()).toBeFalse();
        });
        it('should return false if startDate is undefined', () => {
            expect(component.hasExamStarted()).toBeFalse();
        });
    });
    describe('deleteExercise', () => {
        describe('deleteTextExercise', () => {
            it('should deleteTextExercise', () => {
                deleteTextExerciseStub.mockReturnValue(of({}));
                component.exercise = textExercise;
                component.deleteExercise();
                expect(deleteTextExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for textexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteTextExerciseStub.mockReturnValue(throwError(() => error));
                component.exercise = textExercise;
                component.deleteExercise();
                expect(deleteTextExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
        describe('deleteModelingExercise', () => {
            it('should deleteModelingExercise', () => {
                deleteModelingExerciseStub.mockReturnValue(of({}));
                component.exercise = modelingExercise;
                component.deleteExercise();
                expect(deleteModelingExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for modelingexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteModelingExerciseStub.mockReturnValue(throwError(() => error));
                component.exercise = modelingExercise;
                component.deleteExercise();
                expect(deleteModelingExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
        describe('deleteFileUploadExercise', () => {
            it('should deleteFileUploadExercise', () => {
                deleteFileUploadExerciseStub.mockReturnValue(of({}));
                component.exercise = fileUploadExercise;
                component.deleteExercise();
                expect(deleteFileUploadExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for fileupload exercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteFileUploadExerciseStub.mockReturnValue(throwError(() => error));
                component.exercise = fileUploadExercise;
                component.deleteExercise();
                expect(deleteFileUploadExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
        describe('should deleteQuizExercise', () => {
            it('should deleteQuizExercise', () => {
                deleteQuizExerciseStub.mockReturnValue(of({}));
                component.exercise = quizExercise;
                component.deleteExercise();
                expect(deleteQuizExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
            });
            it('should handle error for quizexercise', () => {
                const error = { message: 'error occurred!' } as HttpErrorResponse;
                deleteQuizExerciseStub.mockReturnValue(throwError(() => error));
                component.exercise = quizExercise;
                component.deleteExercise();
                expect(deleteQuizExerciseStub).toHaveBeenCalledOnce();
                expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
            });
        });
    });
    describe('deleteProgrammingExercise', () => {
        it('should deleteProgrammingExercise', () => {
            deleteProgrammingExerciseStub.mockReturnValue(of({}));
            component.exercise = programmingExercise;
            component.deleteProgrammingExercise({});
            expect(deleteProgrammingExerciseStub).toHaveBeenCalledOnce();
            expect(onDeleteExerciseEmitSpy).toHaveBeenCalledOnce();
        });
        it('should handle error for programmingExercise', () => {
            const error = { message: 'error occurred!' } as HttpErrorResponse;
            deleteProgrammingExerciseStub.mockReturnValue(throwError(() => error));
            component.exercise = programmingExercise;
            component.deleteProgrammingExercise({});
            expect(deleteProgrammingExerciseStub).toHaveBeenCalledOnce();
            expect(onDeleteExerciseEmitSpy).not.toHaveBeenCalled();
        });
    });
    describe('exportQuizById', () => {
        it('should export Quiz, exportAll true', () => {
            quizExerciseServiceFindStub.mockReturnValue(of(quizResponse));
            component.exercise = quizExercise;
            component.exportQuizById(true);
            expect(quizExerciseExportSpy).toHaveBeenCalledOnce();
            expect(quizExerciseExportSpy).toHaveBeenCalledWith({}, true, quizExercise.title);
        });
        it('should export Quiz, exportAll false', () => {
            quizExerciseServiceFindStub.mockReturnValue(of(quizResponse));
            component.exercise = quizExercise;
            component.exportQuizById(false);
            expect(quizExerciseExportSpy).toHaveBeenCalledOnce();
            expect(quizExerciseExportSpy).toHaveBeenCalledWith({}, false, quizExercise.title);
        });
    });
});
