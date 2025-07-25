import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';

import { LegalDocumentUpdateComponent } from 'app/core/admin/legal/legal-document-update.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { UnsavedChangesWarningComponent } from 'app/core/admin/legal/unsaved-changes-warning/unsaved-changes-warning.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { JhiLanguageHelper } from 'app/core/language/shared/language.helper';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { MockLanguageHelper } from 'test/helpers/mocks/service/mock-translate.service';
import { NgbModal, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ModePickerComponent } from 'app/exercise/mode-picker/mode-picker.component';
import { LegalDocumentService } from 'app/core/legal/legal-document.service';
import { LegalDocument, LegalDocumentLanguage, LegalDocumentType } from 'app/core/shared/entities/legal-document.model';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, UrlSegment } from '@angular/router';
import { of } from 'rxjs';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { PrivacyStatement } from 'app/core/shared/entities/privacy-statement.model';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('LegalDocumentUpdateComponent', () => {
    let component: LegalDocumentUpdateComponent;
    let fixture: ComponentFixture<LegalDocumentUpdateComponent>;
    let modalService: NgbModal;
    let legalDocumentService: LegalDocumentService;
    let route: ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MockDirective(NgbTooltip), FaIconComponent],
            declarations: [
                LegalDocumentUpdateComponent,
                MockComponent(UnsavedChangesWarningComponent),
                MockComponent(ButtonComponent),
                MockDirective(TranslateDirective),
                MockComponent(MarkdownEditorMonacoComponent),
                MockComponent(ModePickerComponent),
                MockPipe(ArtemisTranslatePipe),
            ],
            providers: [
                { provide: JhiLanguageHelper, useClass: MockLanguageHelper },
                { provide: NgbModal, useClass: MockNgbModalService },
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({}),
                },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(LegalDocumentUpdateComponent);
        component = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        legalDocumentService = TestBed.inject(LegalDocumentService);
        route = TestBed.inject(ActivatedRoute);
        setupRoutes(LegalDocumentType.PRIVACY_STATEMENT);

        fixture.detectChanges();
    });

    it('should show warning on language change with unsaved changes', async () => {
        const mockReturnValue = {
            result: Promise.resolve({ type: LegalDocumentType.IMPRINT } as LegalDocument),
            componentInstance: {},
        } as NgbModalRef;
        const open = jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        component.unsavedChanges = true;
        component.currentLanguage = LegalDocumentLanguage.ENGLISH;
        component.onLanguageChange(LegalDocumentLanguage.GERMAN);
        fixture.detectChanges();
        expect(open).toHaveBeenCalledOnce();
        expect(open).toHaveBeenCalledWith(UnsavedChangesWarningComponent, { size: 'lg', backdrop: 'static' });
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])('should load legal document in German on init', (documentType: LegalDocumentType) => {
        const returnValue = new LegalDocument(documentType, LegalDocumentLanguage.GERMAN);
        returnValue.text = 'text';
        setupRoutes(documentType);
        let loadFile;
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            loadFile = jest.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate').mockReturnValue(of(returnValue));
        } else {
            loadFile = jest.spyOn(legalDocumentService, 'getImprintForUpdate').mockReturnValue(of(returnValue));
        }
        component.ngOnInit();
        expect(loadFile).toHaveBeenCalledOnce();
        expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.GERMAN);
        expect(component.unsavedChanges).toBeFalse();
        expect(component.legalDocument).toEqual(returnValue);
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should load legal document in selected language on language change',
        (documentType: LegalDocumentType) => {
            const returnValue = new LegalDocument(documentType, LegalDocumentLanguage.GERMAN);
            returnValue.text = 'text';
            setupRoutes(documentType);
            component.ngOnInit();
            let loadFile;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                loadFile = jest.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate').mockReturnValue(of(returnValue));
            } else {
                loadFile = jest.spyOn(legalDocumentService, 'getImprintForUpdate').mockReturnValue(of(returnValue));
            }
            component.currentLanguage = LegalDocumentLanguage.GERMAN;
            component.unsavedChanges = false;
            component.onLanguageChange(LegalDocumentLanguage.ENGLISH);
            fixture.detectChanges();
            expect(loadFile).toHaveBeenCalledOnce();
            expect(loadFile).toHaveBeenCalledWith(LegalDocumentLanguage.ENGLISH);
            expect(component.currentLanguage).toEqual(LegalDocumentLanguage.ENGLISH);
            expect(component.unsavedChanges).toBeFalse();
        },
    );

    it('should correctly determine unsaved changes', () => {
        component.unsavedChanges = false;
        component.legalDocument.text = 'text';
        component.onContentChanged('changed text');
        expect(component.unsavedChanges).toBeTrue();
    });

    it.each([LegalDocumentType.PRIVACY_STATEMENT, LegalDocumentType.IMPRINT])(
        'should call update legal document service method on update',
        fakeAsync((documentType: LegalDocumentType) => {
            const returnValue = new LegalDocument(documentType, LegalDocumentLanguage.GERMAN);
            returnValue.text = 'text';
            setupRoutes(documentType);
            component.ngOnInit();
            let updateFile;
            if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
                updateFile = jest.spyOn(legalDocumentService, 'updatePrivacyStatement').mockReturnValue(of(returnValue));
            } else {
                updateFile = jest.spyOn(legalDocumentService, 'updateImprint').mockReturnValue(of(returnValue));
            }
            component.markdownEditor.markdown = 'text';
            component.onContentChanged('text');
            const expected = new LegalDocument(documentType, LegalDocumentLanguage.GERMAN);
            expected.text = 'text';
            fixture.detectChanges();
            const button = fixture.nativeElement.querySelector('#update-legal-document-btn');
            button.click();
            tick();
            fixture.detectChanges();
            expect(updateFile).toHaveBeenCalledOnce();
            expect(updateFile).toHaveBeenCalledWith(expected);
            expect(component.legalDocument.text).toBe('text');
            expect(component.unsavedChanges).toBeFalse();
        }),
    );
    it('should set the value of the markdown editor when the language is changed while in preview mode', () => {
        setupRoutes(LegalDocumentType.PRIVACY_STATEMENT);
        const returnValue = new PrivacyStatement(LegalDocumentLanguage.GERMAN);
        returnValue.text = 'new content';
        const parseMarkdownStub = jest.spyOn(component.markdownEditor, 'parseMarkdown').mockImplementation();
        component.markdownEditor.markdown = 'text';
        component.unsavedChanges = false;
        component.ngOnInit();
        const loadFile = jest.spyOn(legalDocumentService, 'getPrivacyStatementForUpdate').mockReturnValue(of(returnValue));
        component.currentLanguage = LegalDocumentLanguage.ENGLISH;
        component.onLanguageChange(LegalDocumentLanguage.GERMAN);
        expect(loadFile).toHaveBeenCalledOnce();
        expect(component.legalDocument.text).toBe('new content');
        component.markdownEditor.onEditSelect.emit();
        expect(parseMarkdownStub).toHaveBeenCalledOnce();
    });

    function setupRoutes(documentType: LegalDocumentType) {
        if (documentType === LegalDocumentType.PRIVACY_STATEMENT) {
            // @ts-ignore
            route.parent.url = of([{ path: 'privacy-statement' }] as UrlSegment[]);
        } else {
            // @ts-ignore
            route.parent.url = of([{ path: 'imprint' }] as UrlSegment[]);
        }
    }
});
