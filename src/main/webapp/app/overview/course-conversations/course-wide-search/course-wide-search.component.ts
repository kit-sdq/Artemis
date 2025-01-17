import {
    AfterViewInit,
    ChangeDetectorRef,
    Component,
    ElementRef,
    EventEmitter,
    Input,
    OnDestroy,
    OnInit,
    Output,
    QueryList,
    ViewChild,
    ViewChildren,
    ViewEncapsulation,
    inject,
} from '@angular/core';
import { faChevronLeft, faCircleNotch, faEnvelope, faFilter, faLongArrowAltDown, faLongArrowAltUp, faPlus, faTimes } from '@fortawesome/free-solid-svg-icons';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { Subject, takeUntil } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { ChannelDTO, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { Post } from 'app/entities/metis/post.model';
import { MetisService } from 'app/shared/metis/metis.service';
import { MetisConversationService } from 'app/shared/metis/metis-conversation.service';
import { PostContextFilter, PostSortCriterion, SortDirection } from 'app/shared/metis/metis.util';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { CourseSidebarService } from 'app/overview/course-sidebar.service';
import { NgClass } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { InfiniteScrollDirective } from 'ngx-infinite-scroll';
import { PostingThreadComponent } from 'app/shared/metis/posting-thread/posting-thread.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-course-wide-search',
    templateUrl: './course-wide-search.component.html',
    styleUrls: ['./course-wide-search.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, TranslateDirective, FaIconComponent, FormsModule, ReactiveFormsModule, NgbTooltip, InfiniteScrollDirective, PostingThreadComponent, ArtemisTranslatePipe],
})
export class CourseWideSearchComponent implements OnInit, AfterViewInit, OnDestroy {
    metisService = inject(MetisService);
    metisConversationService = inject(MetisConversationService);
    private formBuilder = inject(FormBuilder);
    cdr = inject(ChangeDetectorRef);

    @Input()
    courseWideSearchConfig: CourseWideSearchConfig;

    @ViewChildren('postingThread')
    messages: QueryList<any>;

    @ViewChild('container')
    content: ElementRef;

    @Output() openThread = new EventEmitter<Post>();

    course: Course;
    currentPostContextFilter?: PostContextFilter;
    // as set for the css class '.posting-infinite-scroll-container'
    messagesContainerHeight = 700;

    faPlus = faPlus;
    faFilter = faFilter;
    faLongArrowAltUp = faLongArrowAltUp;
    faLongArrowAltDown = faLongArrowAltDown;
    faTimes = faTimes;
    faEnvelope = faEnvelope;
    faCircleNotch = faCircleNotch;
    faChevronLeft = faChevronLeft;

    readonly SortDirection = SortDirection;
    sortingOrder = SortDirection.ASCENDING;

    private ngUnsubscribe = new Subject<void>();
    public isFetchingPosts = true;
    totalNumberOfPosts = 0;
    posts: Post[] = [];
    previousScrollDistanceFromTop: number;
    page = 1;

    formGroup: FormGroup;

    getAsChannel = getAsChannelDTO;

    private courseSidebarService: CourseSidebarService = inject(CourseSidebarService);

    ngOnInit() {
        this.subscribeToMetis();
        this.resetFormGroup();
        this.cdr.detectChanges();
        this.onSearch();
    }

    ngAfterViewInit() {
        this.messages.changes.pipe(takeUntil(this.ngUnsubscribe)).subscribe(this.handleScrollOnNewMessage);
    }

    ngOnDestroy() {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    openSidebar() {
        this.courseSidebarService.openSidebar();
    }

    private subscribeToMetis() {
        this.metisService.posts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((posts: Post[]) => {
            this.setPosts(posts);
            this.isFetchingPosts = false;
        });
        this.metisService.totalNumberOfPosts.pipe(takeUntil(this.ngUnsubscribe)).subscribe((totalNumberOfPosts: number) => {
            this.totalNumberOfPosts = totalNumberOfPosts;
        });
    }

    setPosts(posts: Post[]): void {
        if (this.content) {
            this.previousScrollDistanceFromTop = this.content.nativeElement.scrollHeight - this.content.nativeElement.scrollTop;
        }
        this.posts = posts.slice().reverse();
    }

    handleScrollOnNewMessage = () => {
        if ((this.posts.length > 0 && this.content.nativeElement.scrollTop === 0 && this.page === 1) || this.previousScrollDistanceFromTop === this.messagesContainerHeight) {
            this.scrollToBottomOfMessages();
        }
    };

    scrollToBottomOfMessages() {
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollHeight;
    }

    fetchNextPage() {
        const morePostsAvailable = this.posts.length < this.totalNumberOfPosts;
        if (morePostsAvailable) {
            this.page += 1;
            this.commandMetisToFetchPosts();
        }
        this.content.nativeElement.scrollTop = this.content.nativeElement.scrollTop + 50;
    }

    public commandMetisToFetchPosts(forceUpdate = false) {
        this.refreshMetisConversationPostContextFilter();
        if (this.currentPostContextFilter) {
            this.isFetchingPosts = true; // will be set to false in subscription
            this.metisService.getFilteredPosts(this.currentPostContextFilter, forceUpdate);
        }
    }

    private refreshMetisConversationPostContextFilter(): void {
        this.currentPostContextFilter = {
            courseId: this.course?.id,
            searchText: this.courseWideSearchConfig.searchTerm ? this.courseWideSearchConfig.searchTerm.trim() : undefined,
            postSortCriterion: PostSortCriterion.CREATION_DATE,
            filterToUnresolved: this.courseWideSearchConfig.filterToUnresolved,
            filterToOwn: this.courseWideSearchConfig.filterToOwn,
            filterToAnsweredOrReacted: this.courseWideSearchConfig.filterToAnsweredOrReacted,
            sortingOrder: this.courseWideSearchConfig.sortingOrder,
            pagingEnabled: true,
            page: this.page - 1,
            pageSize: 50,
        };
        this.metisConversationService.conversationsOfUser$.pipe(takeUntil(this.ngUnsubscribe)).subscribe((conversations: ConversationDTO[]) => {
            this.currentPostContextFilter!.courseWideChannelIds = conversations
                .filter((conversation) => !(this.currentPostContextFilter?.filterToUnresolved && this.conversationIsAnnouncement(conversation)))
                .map((conversation) => conversation.id!);
        });
    }

    conversationIsAnnouncement(conversation: ConversationDTO) {
        if (conversation.type === 'channel') {
            const channel = conversation as ChannelDTO;
            return channel.isAnnouncementChannel;
        }
        return false;
    }

    postsTrackByFn = (index: number, post: Post): number => post.id!;

    setPostForThread(post: Post) {
        this.openThread.emit(post);
    }

    onSearch() {
        this.commandMetisToFetchPosts(true);
    }

    resetFormGroup(): void {
        this.formGroup = this.formBuilder.group({
            filterToUnresolved: false,
            filterToOwn: false,
            filterToAnsweredOrReacted: false,
        });
    }

    onChangeSortDir(): void {
        this.sortingOrder = this.sortingOrder === SortDirection.DESCENDING ? SortDirection.ASCENDING : SortDirection.DESCENDING;
        this.onSelectContext();
    }

    onSelectContext(): void {
        this.courseWideSearchConfig.filterToUnresolved = this.formGroup.get('filterToUnresolved')?.value;
        this.courseWideSearchConfig.filterToOwn = this.formGroup.get('filterToOwn')?.value;
        this.courseWideSearchConfig.filterToAnsweredOrReacted = this.formGroup.get('filterToAnsweredOrReacted')?.value;
        this.courseWideSearchConfig.sortingOrder = this.sortingOrder;
        this.onSearch();
    }
}

export class CourseWideSearchConfig {
    searchTerm: string;
    filterToUnresolved: boolean;
    filterToOwn: boolean;
    filterToAnsweredOrReacted: boolean;
    sortingOrder: SortDirection;
}
