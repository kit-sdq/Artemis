import { Course } from 'app/core/course/shared/entities/course.model';
import { BehaviorSubject, EMPTY, Observable } from 'rxjs';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { GroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import dayjs from 'dayjs';

export class MockMetisConversationService {
    get course(): Course | undefined {
        return undefined;
    }

    get activeConversation$(): Observable<ConversationDTO | undefined> {
        return EMPTY;
    }

    get isServiceSetup$(): Observable<boolean> {
        return new BehaviorSubject(true).asObservable();
    }

    get conversationsOfUser$(): Observable<ConversationDTO[]> {
        return new BehaviorSubject([new GroupChatDTO()]).asObservable();
    }

    get isLoading$(): Observable<boolean> {
        return new BehaviorSubject(false).asObservable();
    }

    get isCodeOfConductAccepted$(): Observable<boolean> {
        return new BehaviorSubject(true).asObservable();
    }

    get isCodeOfConductPresented$(): Observable<boolean> {
        return new BehaviorSubject(false).asObservable();
    }

    checkIsCodeOfConductAccepted(): void {}

    setActiveConversation(conversationIdentifier: ConversationDTO | number | undefined) {}

    setUpConversationService = (course: Course): Observable<never> => {
        return EMPTY;
    };

    createOneToOneChatWithId = (userId: number): Observable<never> => {
        return EMPTY;
    };

    createOneToOneChat = (userId: number): Observable<never> => {
        return EMPTY;
    };

    handleNewMessage = (conversationId: number | undefined, lastMessageDate: dayjs.Dayjs | undefined): void => {};

    forceRefresh(notifyActiveConversationSubscribers = true, notifyConversationsSubscribers = true): Observable<never> {
        return EMPTY;
    }

    markAsRead(): void {}

    acceptCodeOfConduct(course: Course) {}

    checkForUnreadMessages = (course: Course) => {};
}
