import { Component, ElementRef, OnDestroy, OnInit, inject, input, viewChild } from '@angular/core';
import { faBan, faCheck, faCircleNotch, faFileImport, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { cleanString } from 'app/shared/util/utils';
import { ParseResult, parse } from 'papaparse';
import { AlertService } from 'app/shared/service/alert.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Subject } from 'rxjs';
import { StudentDTO } from 'app/core/shared/entities/student-dto.model';
import { HttpResponse } from '@angular/common/http';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { takeUntil } from 'rxjs/operators';
import { CsvDownloadService } from 'app/shared/util/CsvDownloadService';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { titleRegex } from 'app/tutorialgroup/manage/tutorial-groups/crud/tutorial-group-form/tutorial-group-form.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroupRegistrationImport } from 'app/openapi/model/tutorialGroupRegistrationImport';

/**
 * Each row is a object with the structure
 * 	{
 * 		"Column Header 1": "foo",
 * 		"Column Header 2": "bar"
 * 	}
 */
type ParsedCSVRow = { [header: string]: string };

// ToDo: Idea for future: Let the specify the column names / values in the dialog
const POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS = ['gruppe', 'titel', 'group', 'title', 'tutorialgroups'];
const POSSIBLE_REGISTRATION_NUMBER_HEADERS = ['registrationnumber', 'matriculationnumber', 'matrikelnummer', 'number'];
const POSSIBLE_LOGIN_HEADERS = ['login', 'user', 'username', 'benutzer', 'benutzername', 'anmeldename'];
const POSSIBLE_FIRST_NAME_HEADERS = ['firstname', 'firstnameofstudent', 'givenname', 'forename', 'vorname'];
const POSSIBLE_LAST_NAME_HEADERS = ['familyname', 'lastname', 'familynameofstudent', 'surname', 'nachname', 'familienname', 'name'];
const POSSIBLE_CAMPUS_HEADERS = ['campus'];
const POSSIBLE_CAPACITY_HEADERS = ['capacity'];
const POSSIBLE_LANGUAGE_HEADERS = ['language', 'sprache'];
const POSSIBLE_ADDITIONAL_INFO_HEADERS = ['additionalinformation'];
const POSSIBLE_IS_ONLINE_HEADERS = ['isonline', 'ist Online'];

type filterValues = 'all' | 'onlyImported' | 'onlyNotImported';

@Component({
    selector: 'jhi-tutorial-groups-import-dialog',
    templateUrl: './tutorial-groups-registration-import-dialog.component.html',
    styleUrls: ['./tutorial-groups-registration-import-dialog.component.scss'],
    imports: [TranslateDirective, FaIconComponent, FormsModule, ReactiveFormsModule, ArtemisTranslatePipe],
})
export class TutorialGroupsRegistrationImportDialogComponent implements OnInit, OnDestroy {
    private fb = inject(FormBuilder);
    private translateService = inject(TranslateService);
    private activeModal = inject(NgbActiveModal);
    private alertService = inject(AlertService);
    private tutorialGroupService = inject(TutorialGroupsService);
    private csvDownloadService = inject(CsvDownloadService);

    ngUnsubscribe = new Subject<void>();
    fileInput = viewChild.required<ElementRef<HTMLInputElement>>('fileInput');
    selectedFile?: File;

    courseId = input.required<number>();

    registrationsDisplayedInTable: TutorialGroupRegistrationImport[] = [];
    allRegistrations: TutorialGroupRegistrationImport[] = [];
    notImportedRegistrations: TutorialGroupRegistrationImport[] = [];
    importedRegistrations: TutorialGroupRegistrationImport[] = [];

    isCSVParsing = false;
    protected readonly CsvExample = CsvExample;
    validationErrors: string[] = [];
    isImporting = false;
    isImportDone = false;
    numberOfImportedRegistrations = 0;
    numberOfNotImportedRegistration = 0;
    dialogErrorSource = new Subject<string>();
    dialogError$ = this.dialogErrorSource.asObservable();

    supportedTitleHeader = POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS.join(', ');
    supportedRegistrationNumberHeaders = POSSIBLE_REGISTRATION_NUMBER_HEADERS.join(', ');
    supportedLoginHeaders = POSSIBLE_LOGIN_HEADERS.join(', ');
    supportedFirstNameHeaders = POSSIBLE_FIRST_NAME_HEADERS.join(', ');
    supportedLastNameHeaders = POSSIBLE_LAST_NAME_HEADERS.join(', ');

    fixedPlaceForm: FormGroup;

    get statusHeaderControl() {
        return this.fixedPlaceForm.get('statusHeader');
    }
    get fixedPlaceValueControl() {
        return this.fixedPlaceForm.get('fixedPlaceValue');
    }
    get specifyFixedPlaceControl() {
        return this.fixedPlaceForm.get('specifyFixedPlace');
    }

    // Icons
    faBan = faBan;
    faSpinner = faSpinner;
    faCheck = faCheck;
    faCircleNotch = faCircleNotch;
    faFileImport = faFileImport;
    selectedFilter: filterValues = 'all';

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
        this.dialogErrorSource.unsubscribe();
    }

    ngOnInit(): void {
        this.fixedPlaceForm = this.fb.group({
            specifyFixedPlace: [false],
            statusHeader: ['', [Validators.maxLength(255)]],
            fixedPlaceValue: ['', [Validators.maxLength(255)]],
        });
        this.fixedPlaceValueControl?.disable();
        this.statusHeaderControl?.disable();
        this.onStatusChanges();
        this.onFixedPlaceCheckboxChange();
    }

    onStatusChanges() {
        this.statusHeaderControl?.valueChanges.pipe(takeUntil(this.ngUnsubscribe)).subscribe((selectedStatusColumn) => {
            if (!selectedStatusColumn) {
                this.fixedPlaceValueControl?.reset('', { emitEvent: false });
                this.fixedPlaceValueControl?.disable();
            } else {
                this.fixedPlaceValueControl?.enable();
            }
        });
    }

    onFixedPlaceCheckboxChange() {
        this.specifyFixedPlaceControl?.valueChanges.pipe(takeUntil(this.ngUnsubscribe)).subscribe((specifyFixedPlace) => {
            this.fixedPlaceValueControl?.reset('', { emitEvent: false });
            this.statusHeaderControl?.reset('', { emitEvent: false });
            if (specifyFixedPlace) {
                this.statusHeaderControl?.enable();
                this.fixedPlaceValueControl?.enable();
            } else {
                this.statusHeaderControl?.disable();
                this.fixedPlaceValueControl?.disable();
            }
        });
    }

    get isParseDisabled() {
        return (
            this.selectedFile === undefined ||
            this.isCSVParsing ||
            this.isImporting ||
            (this.specifyFixedPlaceControl?.value && (!this.statusHeaderControl?.value || !this.fixedPlaceValueControl?.value)) ||
            this.fixedPlaceForm.invalid
        );
    }

    get isSubmitDisabled(): boolean {
        return this.isImporting || !this.registrationsDisplayedInTable?.length;
    }

    resetDialog() {
        this.registrationsDisplayedInTable = [];
        this.allRegistrations = [];
        this.notImportedRegistrations = [];
        this.importedRegistrations = [];
        this.validationErrors = [];
        this.isImportDone = false;
        this.isImporting = false;
        this.isCSVParsing = false;
        this.numberOfImportedRegistrations = 0;
        this.numberOfNotImportedRegistration = 0;
    }

    onCSVFileSelected(event: Event) {
        const target = event.target as HTMLInputElement;

        if (target.files && target.files.length > 0) {
            this.resetDialog();
            if (target.files[0]) {
                this.selectedFile = target.files[0];
            }
        }
    }

    /**
     * Reads registrations from a csv file
     * The column "title" is mandatory, all other columns are optional
     * @param csvFile File that contains one registration per row
     */
    private async readRegistrationsFromCSVFile(csvFile: File): Promise<TutorialGroupRegistrationImport[]> {
        let csvRows: ParsedCSVRow[] = [];
        try {
            this.isCSVParsing = true;
            this.validationErrors = [];
            const parseResult = await this.parseCSVFile(csvFile);

            if (parseResult.errors.length > 0) {
                const errorMessagesCombined = parseResult.errors.map((error) => error.message).join('|');
                this.validationErrors.push(errorMessagesCombined);
            } else {
                csvRows = parseResult.data as ParsedCSVRow[];
            }
        } catch (error) {
            this.validationErrors.push(error.message);
        } finally {
            this.isCSVParsing = false;
        }

        if (csvRows && csvRows.length > 0) {
            this.performExtraRowValidation(csvRows);
        }
        if (this.validationErrors && this.validationErrors.length > 0) {
            this.resetFileUpload();
            return [];
        }
        // get the used headers from the first csv row object returned by the parser
        let parsedHeaders = Object.keys(csvRows.first() || []);
        parsedHeaders = parsedHeaders.map(this.removeWhitespacesAndUnderscoresFromHeaderName);

        // we find out which of the possible values is used in the csv file for the respective properties
        const usedTitleHeader = parsedHeaders.find((value) => POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS.includes(value)) || '';
        const usedRegistrationNumberHeader = parsedHeaders.find((value) => POSSIBLE_REGISTRATION_NUMBER_HEADERS.includes(value)) || '';
        const usedLoginHeader = parsedHeaders.find((value) => POSSIBLE_LOGIN_HEADERS.includes(value)) || '';
        const usedFirstNameHeader = parsedHeaders.find((value) => POSSIBLE_FIRST_NAME_HEADERS.includes(value)) || '';
        const usedLastNameHeader = parsedHeaders.find((value) => POSSIBLE_LAST_NAME_HEADERS.includes(value)) || '';
        const usedCampusHeader = parsedHeaders.find((value) => POSSIBLE_CAMPUS_HEADERS.includes(value)) || '';
        const usedCapacityHeader = parsedHeaders.find((value) => POSSIBLE_CAPACITY_HEADERS.includes(value)) || '';
        const usedLanguageHeader = parsedHeaders.find((value) => POSSIBLE_LANGUAGE_HEADERS.includes(value)) || '';
        const usedAdditionalInfoHeader = parsedHeaders.find((value) => POSSIBLE_ADDITIONAL_INFO_HEADERS.includes(value)) || '';
        const usedIsOnlineHeader = parsedHeaders.find((value) => POSSIBLE_IS_ONLINE_HEADERS.includes(value)) || '';

        // if status header is used filter out those rows that do not have a fixed place
        const statusColumn = cleanString(this.statusHeaderControl?.value);
        const fixedPlaceValue = cleanString(this.fixedPlaceValueControl?.value);

        const csvFixedPlaceRows = csvRows.filter((row) => !statusColumn || !fixedPlaceValue || cleanString(row[statusColumn]) === fixedPlaceValue);
        // convert the 'raw' csv rows into a list of TutorialGroupImportDTOs
        const registrations = csvFixedPlaceRows
            .map((csvRow) => {
                const registration: TutorialGroupRegistrationImport = {
                    title: csvRow[usedTitleHeader]?.trim() || '',
                } as TutorialGroupRegistrationImport;
                registration.student = {
                    registrationNumber: csvRow[usedRegistrationNumberHeader]?.trim() || '',
                    login: csvRow[usedLoginHeader]?.trim() || '',
                    firstName: csvRow[usedFirstNameHeader]?.trim() || '',
                    lastName: csvRow[usedLastNameHeader]?.trim() || '',
                } as StudentDTO;
                registration.campus = csvRow[usedCampusHeader]?.trim() || '';
                registration.capacity = csvRow[usedCapacityHeader] ? Number(csvRow[usedCapacityHeader]) : undefined;
                registration.language = csvRow[usedLanguageHeader]?.trim() || '';
                registration.additionalInformation = csvRow[usedAdditionalInfoHeader]?.trim() || '';
                registration.isOnline = csvRow[usedIsOnlineHeader] ? Boolean(csvRow[usedIsOnlineHeader]?.trim().toLowerCase()) : undefined;

                return registration;
            })
            .sort((a, b) => this.compareTitle(a, b));

        this.performExtraDTOValidation(registrations);
        if (this.validationErrors && this.validationErrors.length > 0) {
            this.resetFileUpload();
            return [];
        } else {
            return registrations;
        }
    }

    private compareTitle(a: TutorialGroupRegistrationImport, b: TutorialGroupRegistrationImport) {
        const titleA = a.title;
        const titleB = b.title;

        if (titleA && titleB) {
            return titleA.localeCompare(titleB);
        }
        if (titleA && !titleB) {
            return -1; // a comes first
        }
        if (!titleA && titleB) {
            return 1; // b comes first
        }
        return 0;
    }

    removeWhitespacesAndUnderscoresFromHeaderName(header: string) {
        return header.trim().toLowerCase().replace(/_/g, '-').replace(/\s+/g, '');
    }

    resetFileUpload() {
        this.fileInput().nativeElement.value = ''; // remove selected file so user can fix the file and select it again
        this.selectedFile = undefined;
    }

    import() {
        this.isImporting = true;
        this.tutorialGroupService.import(this.courseId(), this.registrationsDisplayedInTable).subscribe({
            next: (res) => this.onSaveSuccess(res),
            error: () => this.onSaveError(),
        });
    }

    generateCSV(example: CsvExample) {
        let csvContent: string;
        switch (example) {
            case CsvExample.Example1:
                csvContent = 'data:text/csv;charset=utf-8,Title\n';
                break;
            case CsvExample.Example2:
                csvContent = 'data:text/csv;charset=utf-8,Title,Matriculation Number,First Name,Last Name\n';
                break;
            case CsvExample.Example3:
                csvContent = 'data:text/csv;charset=utf-8,Title,Login,First Name,Last Name\n';
                break;
            case CsvExample.Example4:
                csvContent = 'data:text/csv;charset=utf-8,Title,Registration Number,First Name,Last Name,Campus,Language,Additional Information,Capacity,Is Online\n';
                break;
            default:
                csvContent = '';
        }
        this.csvDownloadService.downloadCSV(csvContent, `example${example}.csv`);
    }

    /**
     * Performs validations on the parsed csv rows
     * - checks if values for the required column 'tutorial group title' are present
     *
     * @param csvRows Parsed list of rows
     */
    performExtraRowValidation(csvRows: ParsedCSVRow[]): void {
        const titleValidationError = this.withoutTitleValidation(csvRows);
        const titleRegexValidationError = this.titleRegexValidation(csvRows);
        const withoutIdentifierValidationError = this.withoutIdentifierValidation(csvRows);
        const maxLength = 1000;
        if (titleValidationError !== null) {
            this.validationErrors.push(titleValidationError.length <= maxLength ? titleValidationError : titleValidationError.slice(0, maxLength) + '...');
        }
        if (titleRegexValidationError !== null) {
            this.validationErrors.push(titleRegexValidationError.length <= maxLength ? titleRegexValidationError : titleRegexValidationError.slice(0, maxLength) + '...');
        }
        if (withoutIdentifierValidationError !== null) {
            this.validationErrors.push(
                withoutIdentifierValidationError.length <= maxLength ? withoutIdentifierValidationError : withoutIdentifierValidationError.slice(0, maxLength) + '...',
            );
        }
    }

    performExtraDTOValidation(registrations: TutorialGroupRegistrationImport[]): void {
        const duplicatedRegistrationNumbers = this.duplicatedRegistrationNumbers(registrations);
        const maxLength = 1000;
        if (duplicatedRegistrationNumbers !== null) {
            this.validationErrors.push(
                duplicatedRegistrationNumbers.length <= maxLength ? duplicatedRegistrationNumbers : duplicatedRegistrationNumbers.slice(0, maxLength) + '...',
            );
        }
        const duplicatedLogins = this.duplicatedLogins(registrations);
        if (duplicatedLogins !== null) {
            this.validationErrors.push(duplicatedLogins.length <= maxLength ? duplicatedLogins : duplicatedLogins.slice(0, maxLength) + '...');
        }
    }

    withoutTitleValidation(csvRows: ParsedCSVRow[]): string | null {
        const invalidList: number[] = [];
        for (const [i, row] of csvRows.entries()) {
            const hasTutorialGroupTitle = this.checkIfRowContainsKey(row, POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS);
            if (!hasTutorialGroupTitle) {
                // '+ 2' instead of '+ 1' due to the header column in the csv file
                invalidList.push(i + 2);
            }
        }
        return invalidList.length === 0 ? null : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.withoutTitle') + invalidList.join(', ');
    }
    titleRegexValidation(csvRows: ParsedCSVRow[]): string | null {
        const invalidList: number[] = [];
        for (const [i, row] of csvRows.entries()) {
            const hasTutorialGroupTitle = this.checkIfRowContainsKey(row, POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS);
            if (hasTutorialGroupTitle) {
                const titleHeader = POSSIBLE_TUTORIAL_GROUP_TITLE_HEADERS.find((value) => row[value] !== undefined && row[value] !== null && row[value].trim() !== '');
                if (titleHeader) {
                    const title = row[titleHeader];
                    const regex = titleRegex;
                    if (!regex.test(title)) {
                        invalidList.push(i + 2);
                    }
                }
            }
        }
        return invalidList.length === 0 ? null : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.invalidTitle') + invalidList.join(', ');
    }

    withoutIdentifierValidation(csvRows: ParsedCSVRow[]): string | null {
        const invalidList: number[] = [];
        for (const [i, user] of csvRows.entries()) {
            const specifiesAUser = this.checkIfRowContainsKey(user, POSSIBLE_FIRST_NAME_HEADERS) || this.checkIfRowContainsKey(user, POSSIBLE_LAST_NAME_HEADERS);
            const specifiesARegistrationNumber = this.checkIfRowContainsKey(user, POSSIBLE_REGISTRATION_NUMBER_HEADERS);
            const specifiesALogin = this.checkIfRowContainsKey(user, POSSIBLE_LOGIN_HEADERS);

            if (specifiesAUser && !(specifiesARegistrationNumber || specifiesALogin)) {
                // '+ 2' instead of '+ 1' due to the header column in the csv file
                invalidList.push(i + 2);
            }
        }
        return invalidList.length === 0
            ? null
            : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.noIdentificationInformation') + invalidList.join(', ');
    }

    duplicatedRegistrationNumbers(registrations: TutorialGroupRegistrationImport[]): string | null {
        const duplicatedRegistrationNumbers: string[] = [];
        const registrationNumbers = registrations.map((registration) => registration.student?.registrationNumber).filter((registrationNumber) => registrationNumber);

        const uniqueRegistrationNumbers = [...new Set(registrationNumbers)];

        uniqueRegistrationNumbers.forEach((registrationNumber) => {
            if (registrationNumbers.filter((rn) => rn === registrationNumber).length > 1) {
                if (registrationNumber) {
                    duplicatedRegistrationNumbers.push(registrationNumber);
                }
            }
        });

        return duplicatedRegistrationNumbers.length === 0
            ? null
            : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.duplicatedRegistrationNumbers') + duplicatedRegistrationNumbers.join(', ');
    }

    duplicatedLogins(registrations: TutorialGroupRegistrationImport[]): string | null {
        const duplicatedLogins: string[] = [];
        const logins = registrations.map((registration) => registration.student?.login).filter((login) => login);

        const uniqueLogins = [...new Set(logins)];

        uniqueLogins.forEach((login) => {
            if (logins.filter((l) => l === login).length > 1) {
                if (login) {
                    duplicatedLogins.push(login);
                }
            }
        });

        return duplicatedLogins.length === 0
            ? null
            : this.translateService.instant('artemisApp.tutorialGroupImportDialog.errorMessages.duplicatedLogins') + duplicatedLogins.join(', ');
    }

    /**
     * Checks if the csv row contains one of the supplied keys.
     * @param csvRow which should be checked if it contains one of the keys.
     * @param keys that should be checked for in the row.
     */
    checkIfRowContainsKey(csvRow: ParsedCSVRow, keys: string[]): boolean {
        return keys.some((key) => csvRow[key] !== undefined && csvRow[key] !== null && csvRow[key] !== '');
    }

    clear() {
        if (this.isImportDone) {
            this.activeModal.close();
        } else {
            this.activeModal.dismiss('cancel');
        }
    }

    onFinish() {
        this.activeModal.close();
    }

    onSaveSuccess(registrations: HttpResponse<TutorialGroupRegistrationImport[]>) {
        this.isImporting = false;
        this.isImportDone = true;
        this.registrationsDisplayedInTable = registrations.body ?? [];
        this.registrationsDisplayedInTable = this.registrationsDisplayedInTable.sort((a, b) => this.compareTitle(a, b));
        this.allRegistrations = this.registrationsDisplayedInTable;
        this.notImportedRegistrations = this.allRegistrations.filter((registration) => registration.importSuccessful !== true);
        this.importedRegistrations = this.allRegistrations.filter((registration) => registration.importSuccessful === true);
        this.numberOfNotImportedRegistration = this.notImportedRegistrations.length;
        this.numberOfImportedRegistrations = this.importedRegistrations.length;
    }

    onSaveError() {
        this.alertService.error('artemisApp.tutorialGroupImportDialog.errorMessages.genericErrorMessage');
        this.isImporting = false;
    }

    wasImported(registration: TutorialGroupRegistrationImport): boolean {
        return this.isImportDone && registration.importSuccessful === true;
    }

    wasNotImported(registration: TutorialGroupRegistrationImport): boolean {
        return this.isImportDone && registration.importSuccessful !== true;
    }

    /**
     * Parses a csv file and returns a promise with a list of rows
     * @param csvFile File that should be parsed
     */
    async parseCSVFile(csvFile: File): Promise<ParseResult<unknown>> {
        return new Promise((resolve, reject) => {
            parse(csvFile, {
                delimiter: ',',
                header: true,
                transformHeader: (header: string) => cleanString(header),
                skipEmptyLines: true,
                complete: (results) => resolve(results),
                error: (error) => reject(error),
            });
        });
    }

    async onParseClicked() {
        this.resetDialog();
        if (this.selectedFile) {
            this.registrationsDisplayedInTable = await this.readRegistrationsFromCSVFile(this.selectedFile);
        }
    }

    onFilterChange(newFilterValue: filterValues) {
        this.selectedFilter = newFilterValue;
        switch (newFilterValue) {
            case 'all':
                this.registrationsDisplayedInTable = this.allRegistrations;
                break;
            case 'onlyImported':
                this.registrationsDisplayedInTable = this.importedRegistrations;
                break;
            case 'onlyNotImported':
                this.registrationsDisplayedInTable = this.notImportedRegistrations;
        }
    }
}

enum CsvExample {
    Example1 = 1,
    Example2 = 2,
    Example3 = 3,
    Example4 = 5,
}
