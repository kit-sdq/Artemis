import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ExerciseImportFromFileComponent } from 'app/exercise/import/from-file/exercise-import-from-file.component';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import JSZip from 'jszip';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { QuizExercise } from 'app/quiz/shared/entities/quiz-exercise.model';
import { UMLDiagramType } from '@ls1intum/apollon';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('ExerciseImportFromFileComponent', () => {
    let component: ExerciseImportFromFileComponent;
    let fixture: ComponentFixture<ExerciseImportFromFileComponent>;
    let activeModal: NgbActiveModal;
    let alertService: AlertService;
    let alertServiceSpy: jest.SpyInstance;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockComponent(HelpIconComponent), ButtonComponent, FaIconComponent],
            declarations: [ExerciseImportFromFileComponent, MockDirective(TranslateDirective)],
            providers: [MockProvider(NgbActiveModal), { provide: TranslateService, useClass: MockTranslateService }, MockProvider(FeatureToggleService)],
        }).compileComponents();

        fixture = TestBed.createComponent(ExerciseImportFromFileComponent);
        component = fixture.componentInstance;
        activeModal = TestBed.inject(NgbActiveModal);
        alertService = TestBed.inject(AlertService);

        fixture.detectChanges();
    });

    it('should close the active modal with result', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'close');
        const exercise = { id: undefined } as ProgrammingExercise;
        // WHEN
        component.openImport(exercise);

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith(exercise);
    });

    // using fakeasync and tick didn't work here, that's why I used whenStable and async
    it.each([ExerciseType.QUIZ, ExerciseType.MODELING, ExerciseType.TEXT, ExerciseType.FILE_UPLOAD])(
        'should raise error alert if not supported exercise type',
        async (exerciseType) => {
            // GIVEN
            const alertServiceSpy = jest.spyOn(alertService, 'error');
            component.exerciseType = exerciseType;
            fixture.detectChanges();
            component.fileForImport = (await generateValidTestZipFileWithExerciseType(exerciseType)) as File;
            await fixture.whenStable();
            fixture.detectChanges();
            // WHEN
            await component.uploadExercise();
            await fixture.whenStable();
            fixture.detectChanges();
            // THEN
            expect(alertServiceSpy).toHaveBeenCalledOnce();
            expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.notSupportedExerciseType', { exerciseType: exerciseType });
            expect(component.exercise).toBeUndefined();
        },
    );

    it('should raise error alert if not one json file at the root level', async () => {
        //
        alertServiceSpy = jest.spyOn(alertService, 'error');
        fixture.detectChanges();
        component.fileForImport = (await generateTestZipFileWithoutJsonFile()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
        component.fileForImport = (await generateTestZipFileWithTwoJsonFiles()) as File;
        await assertErrorAlertIsRaisedWithoutOneValidJsonFile();
    });

    it('should raise error alert if exercise type does not match exercise type of imported exercise', async () => {
        alertServiceSpy = jest.spyOn(alertService, 'error');
        fixture.detectChanges();
        component.exerciseType = ExerciseType.TEXT;
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File;
        await fixture.whenStable();
        fixture.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.detectChanges();
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.exercise.importFromFile.exerciseTypeDoesntMatch');
    });

    it('should set exercise attributes and open import dialog', async () => {
        const openImportSpy = jest.spyOn(component, 'openImport');
        component.exerciseType = ExerciseType.PROGRAMMING;
        component.fileForImport = (await generateValidTestZipFileWithExerciseType(ExerciseType.PROGRAMMING)) as File;
        await fixture.whenStable();
        fixture.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.detectChanges();
        // THEN
        expect(component.exercise.id).toBeUndefined();
        expect(component.exercise.zipFileForImport).toBe(component.fileForImport);
        expect(component.exercise).toMatchObject({ type: 'programming', id: undefined, title: 'Test exercise' });
        expect(openImportSpy).toHaveBeenCalledOnce();
        expect(openImportSpy).toHaveBeenCalledWith(component.exercise);
    });

    it('should disable upload button as long as no file is selected', () => {
        component.fileForImport = undefined;
        fixture.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled).toBeTrue();
    });

    it('should enable upload button once file is selected', () => {
        component.fileForImport = new File([''], 'test.zip', { type: 'application/zip' });
        fixture.detectChanges();

        const uploadButton = fixture.debugElement.query(By.css('#upload-exercise-btn'));
        expect(uploadButton.componentInstance.disabled).toBeFalse();
    });

    async function assertErrorAlertIsRaisedWithoutOneValidJsonFile() {
        await fixture.whenStable();
        fixture.detectChanges();
        // WHEN
        await component.uploadExercise();
        await fixture.whenStable();
        fixture.detectChanges();
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.noExerciseDetailsJsonAtRootLevel');
        alertServiceSpy.mockClear();
    }

    it('should raise error alert when more than one file is selected', () => {
        // GIVEN
        alertServiceSpy = jest.spyOn(alertService, 'error');
        const file = new File([''], 'test.zip', { type: 'application/zip' });
        const file2 = new File([''], 'test2.zip', { type: 'application/zip' });
        const event = {
            target: {
                files: [file, file2],
            },
        };
        // WHEN
        component.setFileForExerciseImport(event);
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.fileCountError');
        expect(component.fileForImport).toBeUndefined();
    });

    it('should raise error alert when not a zip file is selected', () => {
        // GIVEN
        alertServiceSpy = jest.spyOn(alertService, 'error');
        const file = new File([''], 'test.txt', { type: 'application/zip' });
        const event = {
            target: {
                files: [file],
            },
        };
        // WHEN
        component.setFileForExerciseImport(event);
        // THEN
        expect(alertServiceSpy).toHaveBeenCalledOnce();
        expect(alertServiceSpy).toHaveBeenCalledWith('artemisApp.programmingExercise.importFromFile.fileExtensionError');
        expect(component.fileForImport).toBeUndefined();
    });

    it('should set file for import if no errors occur', () => {
        // GIVEN
        alertServiceSpy = jest.spyOn(alertService, 'error');
        const file = new File([''], 'test.zip', { type: 'application/zip' });
        const event = {
            target: {
                files: [file],
            },
        };
        // WHEN
        component.setFileForExerciseImport(event);
        // THEN
        expect(component.fileForImport).toEqual(file);
    });
});

async function generateValidTestZipFileWithExerciseType(exerciseType: ExerciseType): Promise<Blob> {
    const zip = new JSZip();
    let exercise;

    switch (exerciseType) {
        case ExerciseType.MODELING:
            exercise = new ModelingExercise(UMLDiagramType.ActivityDiagram, undefined, undefined);
            break;
        case ExerciseType.FILE_UPLOAD:
            exercise = new FileUploadExercise(undefined, undefined);
            break;
        case ExerciseType.TEXT:
            exercise = new TextExercise(undefined, undefined);
            break;
        case ExerciseType.QUIZ:
            exercise = new QuizExercise(undefined, undefined);
            break;
        case ExerciseType.PROGRAMMING:
            exercise = new ProgrammingExercise(undefined, undefined);
            break;
        default:
            throw new Error('Unexpected exercise type');
    }
    exercise.id = 1246;
    exercise.title = 'Test exercise';
    const zipFile = zip.file('test.json', JSON.stringify(exercise));
    return zipFile.generateAsync({ type: 'blob' });
}

async function generateTestZipFileWithoutJsonFile(): Promise<Blob> {
    const zip = new JSZip();
    const zipFile = zip.file('test.txt', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
    return zipFile.generateAsync({ type: 'blob' });
}

async function generateTestZipFileWithTwoJsonFiles(): Promise<Blob> {
    const zip = new JSZip();
    const zipFile = zip
        .file('test.json', '{ "type": "text", "id": 1246, "title": "Test exercise" }')
        .file('test2.json', '{ "type": "text", "id": 1246, "title": "Test exercise" }');
    return zipFile.generateAsync({ type: 'blob' });
}
