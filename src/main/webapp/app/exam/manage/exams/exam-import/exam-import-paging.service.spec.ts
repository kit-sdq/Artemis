import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { SortingOrder } from 'app/shared/table/pageable-table';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { take } from 'rxjs/operators';
import { MockSyncStorage } from 'test/helpers/mocks/service/mock-sync-storage.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { provideHttpClient } from '@angular/common/http';

describe('Exam Import Paging Service', () => {
    let service: ExamImportPagingService;
    let httpMock: HttpTestingController;

    const exam = {
        id: 1,
        title: 'RealExam For Testing',
        course: undefined,
        testExam: false,
    } as Exam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        service = TestBed.inject(ExamImportPagingService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    it('should find an exam', fakeAsync(() => {
        const searchResult = { resultsOnPage: [exam], numberOfPages: 5 };
        const pageable = { pageSize: 2, page: 4, sortingOrder: SortingOrder.DESCENDING, searchTerm: 'ExamSearch', sortedColumn: 'testSortedColumn' };
        service
            .search(pageable, { withExercises: false })
            .pipe(take(1))
            .subscribe((resp) => expect(resp).toEqual(searchResult));
        const req = httpMock.expectOne({ method: 'GET' });
        expect(req.request.params.get('pageSize')).toBe('2');
        expect(req.request.params.get('page')).toBe('4');
        expect(req.request.params.get('sortingOrder')).toBe(SortingOrder.DESCENDING);
        expect(req.request.params.get('searchTerm')).toBe('ExamSearch');
        expect(req.request.params.get('sortedColumn')).toBe('testSortedColumn');
        req.flush(searchResult);
        tick();
    }));

    afterEach(() => {
        httpMock.verify();
    });
});
