import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams, HttpResponse } from '@angular/common/http';
import { Posting } from 'app/communication/shared/entities/posting.model';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { Post } from 'app/communication/shared/entities/post.model';
import { AnswerPost } from 'app/communication/shared/entities/answer-post.model';
import { PostingService } from 'app/communication/service/posting.service';
import { DisplayPriority, PostContextFilter } from 'app/communication/metis.util';
import { convertDateFromServer } from 'app/shared/util/date.utils';

type EntityResponseType = HttpResponse<Post>;
type EntityArrayResponseType = HttpResponse<Post[]>;

@Injectable({ providedIn: 'root' })
export class PostService extends PostingService<Post> {
    private http = inject(HttpClient);

    constructor() {
        super();
    }

    /**
     * creates a post
     * @param courseId
     * @param post
     * @return the created post
     */
    create(courseId: number, post: Post): Observable<EntityResponseType> {
        const postDTO = {
            content: post.content,
            title: (post as Post).title,
            hasForwardedMessages: post.hasForwardedMessages,
            conversation: post.conversation ? { id: post.conversation.id } : undefined,
            plagiarismCase: post.plagiarismCase ? { id: post.plagiarismCase.id } : undefined,
            creationDate: post.creationDate,
            updatedDate: post.updatedDate,
        };
        const copy = this.convertPostingDateFromClient(postDTO);
        return this.http.post<Post>(`${this.getResourceEndpoint(courseId, undefined, post)}`, copy, { observe: 'response' }).pipe(map(this.convertPostingResponseDateFromServer));
    }

    /**
     * gets all posts for course by its id, filtered by context if PostContextFilter is passed
     * a context to filter posts for can be a course-wide topic, a lecture, or an exercise within a course
     * @param courseId
     * @param postContextFilter
     * @return the posts
     */
    getPosts(courseId: number, postContextFilter: PostContextFilter): Observable<EntityArrayResponseType> {
        let params = new HttpParams();

        if (postContextFilter.postSortCriterion) {
            params = params.set('postSortCriterion', postContextFilter.postSortCriterion.toString());
        }
        if (postContextFilter.sortingOrder) {
            params = params.set('sortingOrder', postContextFilter.sortingOrder.toString());
        }
        if (postContextFilter.plagiarismCaseId) {
            params = params.set('plagiarismCaseId', postContextFilter.plagiarismCaseId.toString());
        }
        if (postContextFilter.searchText) {
            params = params.set('searchText', postContextFilter.searchText.toString());
        }
        if (postContextFilter.conversationIds) {
            params = params.set('conversationIds', postContextFilter.conversationIds.toString());
        }
        if (postContextFilter.authorIds) {
            params = params.set('authorIds', postContextFilter.authorIds.toString());
        }
        if (postContextFilter.filterToCourseWide) {
            params = params.set('filterToCourseWide', postContextFilter.filterToCourseWide);
        }
        if (postContextFilter.filterToUnresolved) {
            params = params.set('filterToUnresolved', postContextFilter.filterToUnresolved);
        }
        if (postContextFilter.filterToAnsweredOrReacted) {
            params = params.set('filterToAnsweredOrReacted', postContextFilter.filterToAnsweredOrReacted);
        }
        if (postContextFilter.pagingEnabled) {
            params = params.set('pagingEnabled', postContextFilter.pagingEnabled);
            params = params.set('page', postContextFilter.page!);
            params = params.set('size', postContextFilter.pageSize!);
        }
        if (postContextFilter.pinnedOnly) {
            params = params.set('pinnedOnly', postContextFilter.pinnedOnly);
        }
        return this.http
            .get<Post[]>(`${this.getResourceEndpoint(courseId, postContextFilter, undefined)}`, {
                params,
                observe: 'response',
            })
            .pipe(map(this.convertPostResponseArrayDatesFromServer));
    }

    /**
     * updates a post
     * @param courseId
     * @param post
     * @return the updated post
     */
    update<T extends Posting>(courseId: number, post: T): Observable<EntityResponseType> {
        const updatedPost = {
            id: post.id,
            content: post.content,
            title: (post as Post).title,
            creationDate: post.creationDate,
            updatedDate: post.updatedDate,
        };
        const copy = this.convertPostingDateFromClient(updatedPost);
        return this.http
            .put<Post>(`${this.getResourceEndpoint(courseId, undefined, post)}/${post.id}`, copy, { observe: 'response' })
            .pipe(map(this.convertPostingResponseDateFromServer));
    }

    /**
     * updates the display priority of a post
     * @param courseId
     * @param postId
     * @param displayPriority
     * @return the updated post
     */
    updatePostDisplayPriority(courseId: number, postId: number, displayPriority: DisplayPriority): Observable<EntityResponseType> {
        return this.http
            .put(`api/communication/courses/${courseId}/messages/${postId}/display-priority`, {}, { params: { displayPriority }, observe: 'response' })
            .pipe(map(this.convertPostingResponseDateFromServer));
    }

    /**
     * deletes a post
     * @param courseId
     * @param post
     * @return void
     */
    delete(courseId: number, post: Post): Observable<HttpResponse<void>> {
        return this.http.delete<void>(`${this.getResourceEndpoint(courseId, undefined, post)}/${post.id}`, { observe: 'response' });
    }

    /**
     * gets source posts(original (forwarded) posts in posts) of posts
     * @param {number} courseId
     * @param {number[]} postIds
     * @return {Observable<Post[]>}
     */
    getSourcePostsByIds(courseId: number, postIds: number[]): Observable<Post[]> {
        const params = new HttpParams().set('postIds', postIds.join(','));
        return this.http.get<Post[]>(`api/communication/courses/${courseId}/messages-source-posts`, { params, observe: 'response' }).pipe(map((response) => response.body!));
    }

    /**
     * takes an array of posts and converts the date from the server
     * @param res
     * @return the response with the converted date
     */
    convertPostResponseArrayDatesFromServer(res: HttpResponse<Post[]>): HttpResponse<Post[]> {
        if (res.body) {
            res.body.forEach((post: Post) => {
                post.creationDate = convertDateFromServer(post.creationDate);
                post.answers?.forEach((answer: AnswerPost) => {
                    answer.creationDate = convertDateFromServer(answer.creationDate);
                });
            });
        }
        return res;
    }

    /**
     * Determines the resource endpoint for communication posts.
     * Decides between communication and plagiarism.
     *
     * @param courseId the course id
     * @param postContextFilter current post context filter in use
     * @param post new or updated post
     * @return '/messages' or '/posts'
     */
    private getResourceEndpoint(courseId: number, postContextFilter?: PostContextFilter, post?: Post): string {
        if (post?.conversation || postContextFilter?.conversationIds) {
            return `api/communication/courses/${courseId}/messages`;
        } else {
            return `api/plagiarism/courses/${courseId}/posts`;
        }
    }
}
