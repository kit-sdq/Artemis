import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from 'test/helpers/mocks/service/mock-metis-service.service';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockModule, MockPipe } from 'ng-mocks';
import { PostCreateEditModalComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-create-edit-modal.component';
import { FormBuilder, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { PostingMarkdownEditorComponent } from 'app/communication/posting-markdown-editor/posting-markdown-editor.component';
import { PostingButtonComponent } from 'app/communication/posting-button/posting-button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { PostTagSelectorComponent } from 'app/communication/posting-create-edit-modal/post-create-edit-modal/post-tag-selector/post-tag-selector.component';
import { PageType } from 'app/communication/metis.util';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { PostComponent } from 'app/communication/post/post.component';
import { metisCourse, metisExercise, metisPostLectureUser1, metisPostTechSupport, metisPostToCreateUser1 } from 'test/helpers/sample/metis-sample-data';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { provideHttpClient } from '@angular/common/http';

describe('PostCreateEditModalComponent', () => {
    let component: PostCreateEditModalComponent;
    let fixture: ComponentFixture<PostCreateEditModalComponent>;
    let metisService: MetisService;
    let modal: NgbModal;
    let metisServiceGetPageTypeMock: jest.SpyInstance;
    let metisServiceIsAtLeastInstructorStub: jest.SpyInstance;
    let metisServiceCreateStub: jest.SpyInstance;
    let metisServiceUpdateStub: jest.SpyInstance;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule), MockModule(ReactiveFormsModule)],
            declarations: [
                PostCreateEditModalComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(PostComponent),
                MockComponent(PostingMarkdownEditorComponent),
                MockComponent(PostingButtonComponent),
                MockComponent(HelpIconComponent),
                MockComponent(PostTagSelectorComponent),
            ],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                FormBuilder,
                { provide: MetisService, useClass: MockMetisService },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(PostCreateEditModalComponent);
                component = fixture.componentInstance;
                metisService = TestBed.inject(MetisService);
                modal = TestBed.inject(NgbModal);
                metisServiceGetPageTypeMock = jest.spyOn(metisService, 'getPageType');
                metisServiceIsAtLeastInstructorStub = jest.spyOn(metisService, 'metisUserIsAtLeastInstructorInCourse');
                metisServiceIsAtLeastInstructorStub.mockReturnValue(false);
                metisServiceCreateStub = jest.spyOn(metisService, 'createPost');
                metisServiceUpdateStub = jest.spyOn(metisService, 'updatePost');
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should init modal with correct context, title and content for post without id', () => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = { ...metisPostToCreateUser1 };
        component.ngOnInit();
        component.ngOnChanges();
        expect(component.pageType).toEqual(PageType.OVERVIEW);
        expect(component.modalTitle).toBe('artemisApp.metis.createModalTitlePost');

        // mock metis service will return a course with a default exercise as well as a default lecture
        expect(component.course).not.toBeNull();
        expect(component.lectures).toHaveLength(metisCourse.lectures!.length);
        expect(component.exercises).toHaveLength(metisCourse.exercises!.length);
        expect(component.similarPosts).toHaveLength(0);
        // currently the default selection when opening the model in the overview for creating a new post is the course-wide context TECH_SUPPORT
        expect(component.currentContextSelectorOption).toEqual({});
        expect(component.tags).toEqual([]);
    });

    it('should reset context selection on changes', () => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = { ...metisPostTechSupport };
        component.ngOnInit();
        component.currentContextSelectorOption.conversation = { id: 1 } as Channel;
        component.ngOnChanges();
        // change to Organization as course-wide topic should be reset to Tech Support
        expect(component.currentContextSelectorOption).toEqual({ conversation: metisPostTechSupport.conversation });
        expect(component.tags).toEqual([]);
    });

    it('should invoke metis service with created post in overview', fakeAsync(() => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostToCreateUser1;
        component.ngOnChanges();
        // provide some input before creating the post
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: {},
        });
        // debounce time of title input field
        tick(800);
        expect(component.similarPosts).toEqual([]);
        // trigger the method that is called on clicking the save button
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: newContent,
            title: newTitle,
        });
        tick();
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    }));

    it('should invoke metis service with created announcement in overview', fakeAsync(() => {
        metisServiceIsAtLeastInstructorStub.mockReturnValue(true);
        metisServiceGetPageTypeMock.mockReturnValue(PageType.OVERVIEW);
        component.posting = metisPostToCreateUser1;
        component.ngOnInit();
        component.ngOnChanges();
        // provide some input before creating the post
        const newContent = 'New Content';
        const newTitle = 'New Title';
        const onCreateSpy = jest.spyOn(component.onCreate, 'emit');
        component.formGroup.setValue({
            title: newTitle,
            content: newContent,
            context: { conversationId: metisPostToCreateUser1.conversation?.id, exercise: undefined, undefined },
        });
        // trigger the method that is called on clicking the save button
        component.confirm();
        expect(metisServiceCreateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: newContent,
            title: newTitle,
        });
        // debounce time of title input field
        tick(800);
        expect(component.isLoading).toBeFalse();
        expect(onCreateSpy).toHaveBeenCalledOnce();
    }));

    it('should invoke metis service with updated post in page section', fakeAsync(() => {
        metisServiceGetPageTypeMock.mockReturnValue(PageType.PAGE_SECTION);
        component.posting = metisPostLectureUser1;
        component.ngOnChanges();
        expect(component.pageType).toEqual(PageType.PAGE_SECTION);
        expect(component.modalTitle).toBe('artemisApp.metis.editPosting');
        // provide some updated input before creating the post
        const updatedContent = 'Updated Content';
        const updatedTitle = 'Updated Title';
        component.formGroup.setValue({
            content: updatedContent,
            title: updatedTitle,
            context: { exerciseId: metisExercise.id },
        });
        // debounce time of title input field
        tick(800);
        component.confirm();
        expect(metisServiceUpdateStub).toHaveBeenCalledWith({
            ...component.posting,
            content: updatedContent,
            title: updatedTitle,
        });
        tick();
        expect(component.isLoading).toBeFalse();
    }));

    it('should invoke the modalService', () => {
        const componentInstance = { title: String, content: String };
        const result = new Promise((resolve) => resolve(true));
        const modalServiceOpenMock = jest.spyOn(modal, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });

        component.open();
        expect(modalServiceOpenMock).toHaveBeenCalledOnce();
    });
});
