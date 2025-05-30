import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConversationOptionsComponent } from 'app/shared/sidebar/conversation-options/conversation-options.component';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { generateExampleChannelDTO, generateExampleGroupChatDTO, generateOneToOneChatDTO } from 'test/helpers/sample/conversationExampleModels';
import { ChannelDTO, ChannelSubType } from 'app/communication/shared/entities/conversation/channel.model';
import { CourseLectureDetailsComponent } from 'app/lecture/overview/course-lectures/details/course-lecture-details.component';
import { CourseExerciseDetailsComponent } from 'app/core/course/overview/exercise-details/course-exercise-details.component';
import { ExamDetailComponent } from 'app/exam/manage/exams/detail/exam-detail.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MetisService } from 'app/communication/service/metis.service';
import { MockMetisService } from '../../../../../../test/javascript/spec/helpers/mocks/service/mock-metis-service.service';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { defaultFirstLayerDialogOptions } from 'app/communication/course-conversations-components/other/conversation.util';
import { MetisConversationService } from 'app/communication/service/metis-conversation.service';
import { isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ConversationDetailDialogComponent } from 'app/communication/course-conversations-components/dialogs/conversation-detail-dialog/conversation-detail-dialog.component';

const examples: (() => ConversationDTO)[] = [
    () => generateOneToOneChatDTO({}),
    () => generateExampleGroupChatDTO({}),
    () => generateExampleChannelDTO({} as ChannelDTO),
    () => generateExampleChannelDTO({ subType: ChannelSubType.EXERCISE, subTypeReferenceId: 1 } as ChannelDTO),
    () => generateExampleChannelDTO({ subType: ChannelSubType.LECTURE, subTypeReferenceId: 1 } as ChannelDTO),
    () => generateExampleChannelDTO({ subType: ChannelSubType.EXAM, subTypeReferenceId: 1 } as ChannelDTO),
];

examples.forEach((conversation) => {
    const testDescription = conversation();

    describe('ConversationOptionsComponent with ' + (testDescription instanceof ChannelDTO ? testDescription.subType + ' ' : '') + testDescription.type, () => {
        let component: ConversationOptionsComponent;
        let fixture: ComponentFixture<ConversationOptionsComponent>;
        let conversationService: ConversationService;
        let updateIsFavoriteSpy: jest.SpyInstance;
        let updateIsHiddenSpy: jest.SpyInstance;
        let updateIsMutedSpy: jest.SpyInstance;
        const course = { id: 1 } as any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [MockComponent(FaIconComponent), MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
                providers: [
                    provideRouter([
                        { path: 'courses/:courseId/lectures/:lectureId', component: CourseLectureDetailsComponent },
                        { path: 'courses/:courseId/exercises/:exerciseId', component: CourseExerciseDetailsComponent },
                        { path: 'courses/:courseId/exams/:examId', component: ExamDetailComponent },
                    ]),
                    MockProvider(ConversationService),
                    MockProvider(MetisConversationService),
                    MockProvider(AlertService),
                    MockProvider(NgbModal),
                    { provide: MetisService, useClass: MockMetisService },
                    { provide: TranslateService, useClass: MockTranslateService },
                ],
            }).compileComponents();

            fixture = TestBed.createComponent(ConversationOptionsComponent);
            conversationService = TestBed.inject(ConversationService);
            updateIsFavoriteSpy = jest.spyOn(conversationService, 'updateIsFavorite').mockReturnValue(of(new HttpResponse<void>()));
            updateIsHiddenSpy = jest.spyOn(conversationService, 'updateIsHidden').mockReturnValue(of(new HttpResponse<void>()));
            updateIsMutedSpy = jest.spyOn(conversationService, 'updateIsMuted').mockReturnValue(of(new HttpResponse<void>()));

            component = fixture.componentInstance;
            component.conversation = conversation();
            component.course = course;
            fixture.detectChanges();
        });

        it('should create', () => {
            expect(component).toBeTruthy();
        });

        it('should remove conversation from favorites when hidden', fakeAsync(() => {
            component.conversation.isFavorite = true;
            component.conversation.isHidden = false;

            const hideButton = fixture.debugElement.nativeElement.querySelector('.hide');
            hideButton.click();
            tick(501);

            expect(updateIsFavoriteSpy).toHaveBeenCalledOnce();
            expect(updateIsFavoriteSpy).toHaveBeenCalledWith(course.id, component.conversation.id, false);

            expect(updateIsHiddenSpy).toHaveBeenCalledOnce();
            expect(updateIsHiddenSpy).toHaveBeenCalledWith(course.id, component.conversation.id, true);

            expect(component.conversation.isFavorite).toBeFalse();
            expect(component.conversation.isHidden).toBeTrue();
        }));

        it('should remove conversation from hidden when favorited', fakeAsync(() => {
            component.conversation.isFavorite = false;
            component.conversation.isHidden = true;
            fixture.detectChanges();

            const favoriteButton = fixture.debugElement.nativeElement.querySelector('.favorite');
            favoriteButton.click();
            tick(501);

            expect(updateIsHiddenSpy).toHaveBeenCalledOnce();
            expect(updateIsHiddenSpy).toHaveBeenCalledWith(course.id, component.conversation.id, false);

            expect(updateIsFavoriteSpy).toHaveBeenCalledOnce();
            expect(updateIsFavoriteSpy).toHaveBeenCalledWith(course.id, component.conversation.id, true);

            expect(component.conversation.isHidden).toBeFalse();
            expect(component.conversation.isFavorite).toBeTrue();
        }));

        it('should call updateIsFavorite when button is clicked', fakeAsync(() => {
            const button = fixture.debugElement.nativeElement.querySelector('.favorite');
            button.click();
            tick(501);
            expect(updateIsFavoriteSpy).toHaveBeenCalledOnce();
            expect(updateIsFavoriteSpy).toHaveBeenCalledWith(course.id, component.conversation.id, true);
        }));

        it('should call updateIsHidden when button is clicked', fakeAsync(() => {
            const button = fixture.debugElement.nativeElement.querySelector('.hide');
            button.click();
            tick(501);
            expect(updateIsHiddenSpy).toHaveBeenCalledOnce();
            expect(updateIsHiddenSpy).toHaveBeenCalledWith(course.id, component.conversation.id, true);
        }));

        it('should call updateIsMuted when button is clicked', fakeAsync(() => {
            const button = fixture.debugElement.nativeElement.querySelector('.mute');
            button.click();
            tick(501);
            expect(updateIsMutedSpy).toHaveBeenCalledOnce();
            expect(updateIsMutedSpy).toHaveBeenCalledWith(course.id, component.conversation.id, true);
        }));

        it('should open channel overview dialog when button is pressed', fakeAsync(() => {
            if (isOneToOneChatDTO(component.conversation)) {
                // directMessages do not have a channel overview dialog
                return;
            }
            fixture.detectChanges();
            tick(301);
            const modalService = TestBed.inject(NgbModal);
            const mockModalRef = {
                componentInstance: {
                    course: undefined,
                    createChannelFn: undefined,
                    initialize: () => {},
                },
                result: Promise.resolve([new GroupChatDTO(), true]),
            };
            const openDialogSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef as unknown as NgbModalRef);
            fixture.detectChanges();

            const dialogOpenButton = fixture.debugElement.nativeElement.querySelector('.setting');
            dialogOpenButton.click();
            tick(301);
            fixture.whenStable().then(() => {
                expect(openDialogSpy).toHaveBeenCalledOnce();
                expect(openDialogSpy).toHaveBeenCalledWith(ConversationDetailDialogComponent, defaultFirstLayerDialogOptions);
            });
        }));
    });
});
