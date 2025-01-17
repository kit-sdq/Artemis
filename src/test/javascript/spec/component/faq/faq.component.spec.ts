import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { MockRouterLinkDirective } from '../../helpers/mocks/directive/mock-router-link.directive';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisTestModule } from '../../test.module';
import { FaqService } from 'app/faq/faq.service';
import { Faq, FaqState } from 'app/entities/faq.model';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

import { BrowserAnimationsModule } from '@angular/platform-browser/animations';
import { FaqComponent } from 'app/faq/faq.component';
import { FaqCategory } from 'app/entities/faq-category.model';
import { CustomExerciseCategoryBadgeComponent } from 'app/shared/exercise-categories/custom-exercise-category-badge/custom-exercise-category-badge.component';
import { AlertService } from 'app/core/util/alert.service';
import { SortService } from 'app/shared/service/sort.service';
import { MockAccountService } from '../../helpers/mocks/service/mock-account.service';
import { AccountService } from 'app/core/auth/account.service';
import 'jest-extended';

function createFaq(id: number, category: string, color: string): Faq {
    const faq = new Faq();
    faq.id = id;
    faq.questionTitle = 'questionTitle';
    faq.questionAnswer = 'questionAnswer';
    faq.categories = [new FaqCategory(category, color)];
    faq.faqState = FaqState.PROPOSED;
    return faq;
}

describe('FaqComponent', () => {
    let faqComponentFixture: ComponentFixture<FaqComponent>;
    let faqComponent: FaqComponent;

    let faqService: FaqService;
    let alertServiceStub: jest.SpyInstance;
    let alertService: AlertService;
    let sortService: SortService;

    let faq1: Faq;
    let faq2: Faq;
    let faq3: Faq;

    let courseId: number;

    beforeEach(() => {
        // In beforeEach:
        faq1 = createFaq(1, 'category1', '#94a11c');
        faq2 = createFaq(2, 'category2', '#0ab84f');
        faq3 = createFaq(3, 'category3', '#0ab84f');

        courseId = 1;

        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, MockModule(ArtemisMarkdownEditorModule), MockModule(BrowserAnimationsModule)],
            declarations: [FaqComponent, MockRouterLinkDirective, MockComponent(CustomExerciseCategoryBadgeComponent)],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Router, useClass: MockRouter },
                { provide: AccountService, useClass: MockAccountService },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        data: of({ course: { id: 1 } }),
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                            }),
                        },
                    },
                },
                MockProvider(FaqService, {
                    findAllByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [faq1, faq2, faq3],
                                status: 200,
                            }),
                        );
                    },
                    delete: () => {
                        return of(new HttpResponse({ status: 200 }));
                    },
                    findAllCategoriesByCourseId: () => {
                        return of(
                            new HttpResponse({
                                body: [],
                                status: 200,
                            }),
                        );
                    },
                    applyFilters: () => {
                        return [faq2, faq3];
                    },
                    hasSearchTokens: () => {
                        return true;
                    },
                }),
            ],
        })
            .compileComponents()
            .then(() => {
                faqComponentFixture = TestBed.createComponent(FaqComponent);
                faqComponent = faqComponentFixture.componentInstance;

                faqService = TestBed.inject(FaqService);
                alertService = TestBed.inject(AlertService);
                sortService = TestBed.inject(SortService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch faqs when initialized', () => {
        const findAllSpy = jest.spyOn(faqService, 'findAllByCourseId');

        faqComponentFixture.detectChanges();
        expect(findAllSpy).toHaveBeenCalledExactlyOnceWith(1);
        expect(faqComponent.faqs).toHaveLength(3);
        expect(faqComponent.faqs).toEqual([faq1, faq2, faq3]);
    });

    it('should catch error if loading fails', () => {
        const error = { status: 404 };
        const findSpy = jest.spyOn(faqService, 'findAllByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        expect(findSpy).toHaveBeenCalled();
        expect(faqComponent.faqs).toBeUndefined();
    });

    it('should delete faq', () => {
        const deleteSpy = jest.spyOn(faqService, 'delete');
        faqComponentFixture.detectChanges();
        faqComponent.deleteFaq(courseId, faq1.id!);
        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(courseId, faq1.id!);
        expect(faqComponent.faqs).toHaveLength(2);
        expect(faqComponent.faqs).not.toContain(faq1);
        expect(faqComponent.faqs).toEqual(faqComponent.filteredFaqs);
    });

    it('should not delete faq on error', () => {
        const error = { status: 404 };
        const deleteSpy = jest.spyOn(faqService, 'delete').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.deleteFaq(courseId, faq1.id!);
        expect(deleteSpy).toHaveBeenCalledExactlyOnceWith(courseId, faq1.id!);
        expect(faqComponent.faqs).toHaveLength(3);
        expect(faqComponent.faqs).toContain(faq1);
    });

    it('should toggle filter correctly', () => {
        const toggleFilterSpy = jest.spyOn(faqService, 'toggleFilter');
        faqComponentFixture.detectChanges();
        faqComponent.toggleFilters('category2');
        expect(toggleFilterSpy).toHaveBeenCalledOnce();
        expect(faqComponent.filteredFaqs).toHaveLength(2);
        expect(faqComponent.filteredFaqs).not.toContain(faq1);
        expect(faqComponent.filteredFaqs).toEqual([faq2, faq3]);
    });

    it('should catch error if no categories are found', () => {
        alertServiceStub = jest.spyOn(alertService, 'error');
        const error = { status: 404 };
        jest.spyOn(faqService, 'findAllCategoriesByCourseId').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        expect(alertServiceStub).toHaveBeenCalledOnce();
    });

    it('should search through already filtered array', () => {
        const searchSpy = jest.spyOn(faqService, 'hasSearchTokens');
        const applyFilterSpy = jest.spyOn(faqService, 'applyFilters');
        faqComponent.setSearchValue('questionTitle');
        faqComponent.refreshFaqList(faqComponent.searchInput.getValue());
        expect(applyFilterSpy).toHaveBeenCalledOnce();
        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenCalledWith(faq2, 'questionTitle');
        expect(searchSpy).toHaveBeenCalledWith(faq3, 'questionTitle');
        expect(faqComponent.filteredFaqs).toHaveLength(2);
        expect(faqComponent.filteredFaqs).not.toContain(faq1);
        expect(faqComponent.filteredFaqs).toEqual([faq2, faq3]);
    });

    it('should call sortService when sortRows is called', () => {
        jest.spyOn(sortService, 'sortByProperty').mockReturnValue([]);
        faqComponent.sortRows();
        expect(sortService.sortByProperty).toHaveBeenCalledOnce();
    });

    it('should reject faq properly', () => {
        jest.spyOn(faqService, 'update').mockReturnValue(of(new HttpResponse({ body: faq1 })));
        faqComponentFixture.detectChanges();
        faqComponent.rejectFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(courseId, faq1);
        expect(faq1.faqState).toEqual(FaqState.REJECTED);
    });

    it('should not change status if rejection fails', () => {
        const error = { status: 500 };
        jest.spyOn(faqService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.rejectFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(courseId, faq1);
        expect(faq1.faqState).toEqual(FaqState.PROPOSED);
    });

    it('should accepts proposed faq properly', () => {
        jest.spyOn(faqService, 'update').mockReturnValue(of(new HttpResponse({ body: faq1 })));
        faqComponentFixture.detectChanges();
        faqComponent.acceptProposedFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(courseId, faq1);
        expect(faq1.faqState).toEqual(FaqState.ACCEPTED);
    });

    it('should not change status if acceptance fails', () => {
        const error = { status: 500 };
        jest.spyOn(faqService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse(error)));
        faqComponentFixture.detectChanges();
        faqComponent.acceptProposedFaq(courseId, faq1);
        expect(faqService.update).toHaveBeenCalledExactlyOnceWith(courseId, faq1);
        expect(faq1.faqState).toEqual(FaqState.PROPOSED);
    });
});
