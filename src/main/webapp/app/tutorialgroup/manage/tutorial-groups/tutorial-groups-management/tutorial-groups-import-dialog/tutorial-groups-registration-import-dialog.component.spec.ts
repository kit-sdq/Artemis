import { ComponentFixture, TestBed } from '@angular/core/testing';

import { TutorialGroupsRegistrationImportDialogComponent } from 'app/tutorialgroup/manage/tutorial-groups/tutorial-groups-management/tutorial-groups-import-dialog/tutorial-groups-registration-import-dialog.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { AlertService } from 'app/shared/service/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ParseError, ParseResult, ParseWorkerConfig, parse } from 'papaparse';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TutorialGroupRegistrationImport } from 'app/openapi/model/tutorialGroupRegistrationImport';
import { Student } from 'app/openapi/model/student';
import ErrorEnum = TutorialGroupRegistrationImport.ErrorEnum;
jest.mock('papaparse', () => {
    const original = jest.requireActual('papaparse');
    return {
        ...original,
        parse: jest.fn(),
    };
});
const mockedParse = parse as jest.MockedFunction<typeof parse>;

describe('TutorialGroupsRegistrationImportDialog', () => {
    let component: TutorialGroupsRegistrationImportDialogComponent;
    let fixture: ComponentFixture<TutorialGroupsRegistrationImportDialogComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FormsModule, ReactiveFormsModule, FaIconComponent],
            declarations: [TutorialGroupsRegistrationImportDialogComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [MockProvider(TranslateService), MockProvider(AlertService), MockProvider(TutorialGroupsService), MockProvider(NgbActiveModal)],
        }).compileComponents();

        fixture = TestBed.createComponent(TutorialGroupsRegistrationImportDialogComponent);
        component = fixture.componentInstance;
        component.selectedFile = generateDummyFile();
        fixture.detectChanges();
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should reset dialog when selecting a new file', () => {
        // given
        setExampleState();
        const exampleFile = generateDummyFile('example.csv');
        const event = { target: { files: [exampleFile] } } as unknown as Event;
        const resetSpy = jest.spyOn(component, 'resetDialog');

        // when
        component.onCSVFileSelected(event);

        // then
        expect(component.allRegistrations).toEqual([]);
        expect(component.registrationsDisplayedInTable).toEqual([]);
        expect(component.notImportedRegistrations).toEqual([]);
        expect(component.importedRegistrations).toEqual([]);
        expect(component.validationErrors).toEqual([]);
        expect(component.numberOfNotImportedRegistration).toBe(0);
        expect(component.numberOfImportedRegistrations).toBe(0);
        expect(component.selectedFile).toEqual(exampleFile);
        expect(resetSpy).toHaveBeenCalled();
    });

    it('clear should close the modal with cancel reason', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        // given
        const dismissSpy = jest.spyOn(activeModal, 'dismiss');

        // when
        component.clear();

        // then
        expect(dismissSpy).toHaveBeenCalledOnce();
        expect(dismissSpy).toHaveBeenCalledWith('cancel');
    });

    it('onFinish should close the modal', () => {
        const activeModal = TestBed.inject(NgbActiveModal);
        // given
        const closeSpy = jest.spyOn(activeModal, 'close');

        // when
        component.onFinish();

        // then
        expect(closeSpy).toHaveBeenCalledOnce();
        expect(closeSpy).toHaveBeenCalledWith();
    });

    it('should read registrations from csv string', async () => {
        // given
        const exampleDTO = generateImportDTO();
        mockParserWithDTOs([exampleDTO], []);

        // when
        await component.onParseClicked();
        // then
        expect(component.registrationsDisplayedInTable).toEqual([exampleDTO]);
        expect(component.validationErrors).toEqual([]);
        expect(component.isCSVParsing).toBeFalse();
    });

    it('should read registrations without student from csv string', async () => {
        // given
        const rawRow = {
            group: 'group',
            registrationnumber: '',
            firstname: '',
            lastname: '',
            login: '',
        } as ExampleRawCSVRow;

        mockParserWithRawCSVRows([rawRow], []);
        // when
        await component.onParseClicked();

        // then
        expect(component.registrationsDisplayedInTable).toHaveLength(1);
        const registration = component.registrationsDisplayedInTable[0];
        expect(registration.student).toEqual(generateStudentDTO('', '', '', ''));
        expect(registration.title).toBe('group');
        expect(component.validationErrors).toEqual([]);
        expect(component.isCSVParsing).toBeFalse();
    });

    it('should filter out unconfirmed registrations', async () => {
        const exampleOne = generateImportDTO('Tutorial Group 1');
        const exampleTwo = generateImportDTO('Tutorial Group 2');

        component.statusHeaderControl?.setValue('status');
        component.fixedPlaceValueControl?.setValue('confirmed');

        mockParserWithRawCSVRows([generateRowObjectFromDTO(exampleOne, 'confirmed'), generateRowObjectFromDTO(exampleTwo, 'unconfirmed')], []);

        // when
        await component.onParseClicked();

        expect(component.registrationsDisplayedInTable).toEqual([exampleOne]);
        expect(component.validationErrors).toEqual([]);
        expect(component.isCSVParsing).toBeFalse();
    });

    it('should fail csv validation when csv is malformed', async () => {
        // given
        const mockParserError = {
            type: 'FieldMismatch',
            code: 'TooManyFields',
            message: 'Expected 3 fields, but parsed 4',
            row: 1,
        } as ParseError;

        mockParserWithDTOs([], [mockParserError]);

        // when
        await component.onParseClicked();
        // then
        assertStateAfterValidationError(mockParserError.message);
    });

    it('should fail when parser throws exception', async () => {
        // given
        mockedParse.mockImplementation(() => {
            throw new Error('testError');
        });
        // when
        await component.onParseClicked();
        // then
        assertStateAfterValidationError('testError');
    });

    it('should fail when no title value', async () => {
        await validationTest([generateImportDTO('')], 'artemisApp.tutorialGroupImportDialog.errorMessages.withoutTitle');
    });

    it('should fail when title contains invalid character', async () => {
        await validationTest([generateImportDTO('$title')], 'artemisApp.tutorialGroupImportDialog.errorMessages.invalidTitle');
    });

    it('should fail when title too long', async () => {
        await validationTest([generateImportDTO('this is a very long title that should not be accepted')], 'artemisApp.tutorialGroupImportDialog.errorMessages.invalidTitle');
    });

    it('should fail when no identifier information', async () => {
        const invalidStudentDto = generateStudentDTO('', 'ipsum', 'lorem', '');
        await validationTest([generateImportDTO('Title', invalidStudentDto)], 'artemisApp.tutorialGroupImportDialog.errorMessages.noIdentificationInformation');
    });

    it('should fail when same registration number is mapped to two groups', async () => {
        const registrationOne = generateImportDTO('Group A', generateStudentDTO('123456', 'ipsum', 'lorem', ''));
        const registrationTwo = generateImportDTO('Group B', generateStudentDTO('123456', 'ipsum', 'lorem', ''));
        await validationTest([registrationOne, registrationTwo], 'artemisApp.tutorialGroupImportDialog.errorMessages.duplicatedRegistrationNumbers', '123456');
    });

    it('should fail when the same login is mapped to two groups', async () => {
        const registrationOne = generateImportDTO('Group A', generateStudentDTO('', 'ipsum', 'lorem', 'aba'));
        const registrationTwo = generateImportDTO('Group B', generateStudentDTO('', 'ipsum', 'lorem', 'aba'));
        await validationTest([registrationOne, registrationTwo], 'artemisApp.tutorialGroupImportDialog.errorMessages.duplicatedLogins', 'aba');
    });

    it('onFilterChange should set table data to filtered data', () => {
        const exampleOne = generateImportDTO('Tutorial Group 1');
        const exampleTwo = generateImportDTO('Tutorial Group 2');
        const exampleThree = generateImportDTO('Tutorial Group 3');

        component.allRegistrations = [exampleOne, exampleTwo, exampleThree];
        component.registrationsDisplayedInTable = [exampleOne, exampleTwo, exampleThree];
        component.notImportedRegistrations = [exampleOne];
        component.importedRegistrations = [exampleTwo, exampleThree];
        component.selectedFilter = 'all';
        component.onFilterChange('onlyNotImported');
        expect(component.registrationsDisplayedInTable).toEqual([exampleOne]);
        component.onFilterChange('onlyImported');
        expect(component.registrationsDisplayedInTable).toEqual([exampleTwo, exampleThree]);
        component.onFilterChange('all');
        expect(component.registrationsDisplayedInTable).toEqual([exampleOne, exampleTwo, exampleThree]);
    });

    it('wasImported should check the import status set by the server', () => {
        component.isImportDone = true;
        const failedExample = generateImportDTO();
        failedExample.importSuccessful = false;
        expect(component.wasImported(failedExample)).toBeFalse();
    });

    it('should reset fixed place inputs when the respective checkbox is switched', () => {
        const fixedPlaceResetSpy = jest.spyOn(component.fixedPlaceValueControl!, 'reset');
        const statusHeaderResetSpy = jest.spyOn(component.statusHeaderControl!, 'reset');

        component.specifyFixedPlaceControl?.setValue(false);
        expect(fixedPlaceResetSpy).toHaveBeenCalledTimes(2);
        expect(statusHeaderResetSpy).toHaveBeenCalledOnce();
        expect(component.fixedPlaceValueControl?.disabled).toBeTrue();
        expect(component.statusHeaderControl?.disabled).toBeTrue();

        fixedPlaceResetSpy.mockClear();
        statusHeaderResetSpy.mockClear();
        component.specifyFixedPlaceControl?.setValue(true);
        expect(fixedPlaceResetSpy).toHaveBeenCalledTimes(2);
        expect(statusHeaderResetSpy).toHaveBeenCalledOnce();
        expect(component.fixedPlaceValueControl?.disabled).toBeFalse();
        expect(component.statusHeaderControl?.disabled).toBeFalse();
    });

    it('should reset fixed place input when fixed place header input is cleared', () => {
        const fixedPlaceResetSpy = jest.spyOn(component.fixedPlaceValueControl!, 'reset');

        component.statusHeaderControl?.setValue('');
        expect(fixedPlaceResetSpy).toHaveBeenCalledOnce();
        expect(component.fixedPlaceValueControl?.disabled).toBeTrue();

        fixedPlaceResetSpy.mockClear();
        component.statusHeaderControl?.setValue('status');
        expect(fixedPlaceResetSpy).not.toHaveBeenCalled();
        expect(component.fixedPlaceValueControl?.disabled).toBeFalse();
    });

    it('should call the import service when the import button is clicked', async () => {
        const exampleOne = generateImportDTO('Tutorial Group 1');
        const exampleTwo = generateImportDTO('Tutorial Group 2');
        component.registrationsDisplayedInTable = [exampleOne, exampleTwo];
        component.isImportDone = false;
        fixture.componentRef.setInput('courseId', 1);

        const tutorialGroupService = TestBed.inject(TutorialGroupsService);

        const returnedDTOOne = { ...exampleOne, importSuccessful: true };
        const returnedDTOTwo = { ...exampleTwo, importSuccessful: false, errorMessage: 'error' };

        const importSpy = jest.spyOn(tutorialGroupService, 'import').mockReturnValue(of(new HttpResponse({ body: [returnedDTOOne, returnedDTOTwo], status: 200 })));

        component.import();

        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, [exampleOne, exampleTwo]);
        expect(component.isImporting).toBeFalse();
        expect(component.isImportDone).toBeTrue();
        expect(component.importedRegistrations).toEqual([returnedDTOOne]);
        expect(component.notImportedRegistrations).toEqual([returnedDTOTwo]);
        expect(component.allRegistrations).toEqual([returnedDTOOne, returnedDTOTwo]);
        expect(component.numberOfImportedRegistrations).toBe(1);
        expect(component.numberOfNotImportedRegistration).toBe(1);
    });

    it('should read registrations from csv string with additional headers', async () => {
        // given
        const exampleDTO = generateImportDTO('Tutorial Group 1', generateStudentDTO('123456', 'John', 'Doe', 'john-doe'), 'Main Campus', 'German', undefined, 25, undefined);
        mockParserWithDTOs([exampleDTO], []);

        // when
        await component.onParseClicked();
        // then
        expect(component.registrationsDisplayedInTable).toEqual([exampleDTO]);
        expect(component.validationErrors).toEqual([]);
        expect(component.isCSVParsing).toBeFalse();
        expect(component.registrationsDisplayedInTable[0].campus).toBe('Main Campus');
        expect(component.registrationsDisplayedInTable[0].language).toBe('German');
        expect(component.registrationsDisplayedInTable[0].additionalInformation).toBe('');
        expect(component.registrationsDisplayedInTable[0].capacity).toBe(25);
        expect(component.registrationsDisplayedInTable[0].isOnline).toBeUndefined();
    });
    it('should remove spaces from header names correctly', () => {
        const headerWithSpaces = ' Header Name ';
        const headerWithUnderscores = 'Header_Name';
        const headerWithMixed = ' Header_Name With  Spaces ';

        expect(component.removeWhitespacesAndUnderscoresFromHeaderName(headerWithSpaces)).toBe('headername');
        expect(component.removeWhitespacesAndUnderscoresFromHeaderName(headerWithUnderscores)).toBe('header-name');
        expect(component.removeWhitespacesAndUnderscoresFromHeaderName(headerWithMixed)).toBe('header-namewithspaces');
    });

    it('should generate and download CSV when generateCSV is called', () => {
        const createElementSpy = jest.spyOn(document, 'createElement').mockReturnValue(document.createElement('a'));
        const appendChildSpy = jest.spyOn(document.body, 'appendChild');
        const removeChildSpy = jest.spyOn(document.body, 'removeChild');

        component.generateCSV(1);

        const expectedContent = 'data:text/csv;charset=utf-8,Title\n';
        const encodedUri = encodeURI(expectedContent);

        expect(createElementSpy).toHaveBeenCalledWith('a');
        const link = createElementSpy.mock.results[0].value as HTMLAnchorElement;
        expect(link.getAttribute('href')).toBe(encodedUri);
        expect(link.getAttribute('download')).toBe('example1.csv');
        expect(appendChildSpy).toHaveBeenCalledWith(link);
        expect(removeChildSpy).toHaveBeenCalledWith(link);
    });

    async function validationTest(data: TutorialGroupRegistrationImport[], translationKey: string, errorAddition?: string) {
        // given
        const translateService = TestBed.inject(TranslateService);
        const instantSpy = jest.spyOn(translateService, 'instant').mockReturnValue('testError:');

        mockParserWithDTOs(data, []);
        // when
        await component.onParseClicked();

        // then
        assertStateAfterValidationError('testError:' + (errorAddition ?? '2'));
        expect(instantSpy).toHaveBeenCalledOnce();
        expect(instantSpy).toHaveBeenCalledWith(translationKey);
    }
    function assertStateAfterValidationError(expectedError: string) {
        expect(component.registrationsDisplayedInTable).toEqual([]);
        expect(component.validationErrors).toEqual([expectedError]);
        expect(component.isCSVParsing).toBeFalse();
        expect(component.selectedFile).toBeUndefined();
    }

    const generateDummyFile = (name?: string) => {
        let blob = new Blob(['']);
        blob = { ...blob, name: name ?? 'filename', lastModifiedDate: '' } as unknown as Blob;
        return <File>blob;
    };

    function setExampleState() {
        component.allRegistrations = [generateImportDTO(), generateImportDTO('Another title')];
        component.registrationsDisplayedInTable = component.allRegistrations;
        component.notImportedRegistrations = [generateImportDTO()];
        component.importedRegistrations = [generateImportDTO('Another title')];
        component.validationErrors = ['error'];
        component.numberOfNotImportedRegistration = 1;
        component.numberOfImportedRegistrations = 1;
    }

    const generateImportDTO = (
        title?: string,
        student?: Student,
        campus?: string,
        language?: string,
        additionalInformation?: string,
        capacity?: number,
        isOnline?: boolean,
        importSuccessful?: boolean,
        error?: ErrorEnum,
    ) => {
        const dto = {} as TutorialGroupRegistrationImport;
        dto.title = title ?? 'Mo 12-13';
        dto.student = student ?? generateStudentDTO();
        dto.importSuccessful = importSuccessful ?? undefined;
        dto.error = error ?? undefined;
        dto.campus = campus ?? 'Campus';
        dto.language = language ?? 'English';
        dto.additionalInformation = additionalInformation ?? '';
        dto.capacity = capacity ?? 20;
        dto.isOnline = isOnline ?? undefined;
        return dto;
    };

    const generateStudentDTO = (registrationNumber?: string, firstName?: string, lastName?: string, login?: string) => {
        const dto = {} as Student;
        dto.registrationNumber = registrationNumber ?? '123456';
        dto.firstName = firstName ?? 'John';
        dto.lastName = lastName ?? 'Doe';
        dto.login = login ?? 'john-doe';
        return dto;
    };

    function mockParserWithDTOs(data: TutorialGroupRegistrationImport[], errors: ParseError[]) {
        mockParserWithRawCSVRows(
            data.map((dto) => generateRowObjectFromDTO(dto)),
            errors,
        );
    }

    function mockParserWithRawCSVRows(data: ExampleRawCSVRow[], errors: ParseError[]) {
        // @ts-ignore
        mockedParse.mockImplementation((csvString: string, config: ParseWorkerConfig<unknown>) => {
            const result = {
                data,
                errors,
            };
            config.complete(result as unknown as ParseResult<unknown>);
        });
    }

    interface ExampleRawCSVRow {
        group: string;
        registrationnumber: string;
        login: string;
        firstname: string;
        lastname: string;
        campus?: string;
        language?: string;
        additionalInformation?: string;
        capacity?: number;
        isOnline?: boolean;
        status?: string;
    }

    const generateRowObjectFromDTO = (dto: TutorialGroupRegistrationImport, status?: string) => {
        return {
            group: dto.title,
            registrationnumber: dto.student!.registrationNumber,
            login: dto.student!.login,
            firstname: dto.student!.firstName,
            lastname: dto.student!.lastName,
            campus: dto.campus,
            language: dto.language,
            additionalInformation: dto.additionalInformation,
            capacity: dto.capacity,
            isOnline: dto.isOnline,
            status: status ?? '',
        } as ExampleRawCSVRow;
    };
});
