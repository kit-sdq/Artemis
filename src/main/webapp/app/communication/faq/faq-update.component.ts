import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { onError } from 'app/shared/util/global.utils';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { faBan, faQuestionCircle, faSave } from '@fortawesome/free-solid-svg-icons';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { Faq, FaqState } from 'app/communication/shared/entities/faq.model';
import { FaqService } from 'app/communication/faq/faq.service';
import { FaqCategory } from 'app/communication/shared/entities/faq-category.model';
import { loadCourseFaqCategories } from 'app/communication/faq/faq.utils';
import { AccountService } from 'app/core/auth/account.service';
import { RewriteAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewrite.action';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { PROFILE_IRIS } from 'app/app.constants';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { FormsModule } from '@angular/forms';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaqConsistencyComponent } from 'app/communication/faq/faq-consistency.component';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';

@Component({
    selector: 'jhi-faq-update',
    templateUrl: './faq-update.component.html',
    styleUrls: ['./faq-update.component.scss'],
    imports: [CategorySelectorComponent, MarkdownEditorMonacoComponent, TranslateDirective, FontAwesomeModule, FormsModule, FaqConsistencyComponent],
})
export class FaqUpdateComponent implements OnInit {
    private alertService = inject(AlertService);
    private faqService = inject(FaqService);
    private activatedRoute = inject(ActivatedRoute);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private router = inject(Router);
    private profileService = inject(ProfileService);
    private accountService = inject(AccountService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);

    faq: Faq;
    isSaving: boolean;
    isAllowedToSave: boolean;
    existingCategories: FaqCategory[];
    faqCategories: FaqCategory[];
    courseId: number;
    isAtLeastInstructor = false;
    domainActionsDescription = [new FormulaAction()];

    renderedConsistencyCheckResultMarkdown = signal<RewriteResult>({
        result: undefined,
        inconsistencies: undefined,
        suggestions: undefined,
        improvement: undefined,
    });

    showConsistencyCheck = computed(() => !!this.renderedConsistencyCheckResultMarkdown().result);

    irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
    artemisIntelligenceActions = computed(() =>
        this.irisEnabled ? [new RewriteAction(this.artemisIntelligenceService, RewritingVariant.FAQ, this.courseId, this.renderedConsistencyCheckResultMarkdown)] : [],
    );
    // Icons
    readonly faQuestionCircle = faQuestionCircle;
    readonly faSave = faSave;
    readonly faBan = faBan;

    ngOnInit() {
        this.isSaving = false;
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.activatedRoute.parent?.data.subscribe((data) => {
            // Create a new faq to use unless we fetch an existing faq
            const faq = data['faq'];
            this.faq = faq ?? new Faq();
            const course = data['course'];
            if (course) {
                this.faq.course = course;
                this.loadCourseFaqCategories(course.id);
                this.isAtLeastInstructor = this.accountService.isAtLeastInstructorInCourse(course);
            }
            this.faqCategories = faq?.categories ? faq.categories : [];
        });
        this.validate();
    }

    /**
     * Revert to the previous state, equivalent with pressing the back button on your browser
     * Returns to the detail page if there is no previous state and we edited an existing faq
     * Returns to the overview page if there is no previous state and we created a new faq
     */

    previousState() {
        this.navigationUtilService.navigateBack(['course-management', this.courseId, 'faqs']);
    }
    /**
     * Save the changes on a faq
     * This function is called by pressing save after creating or editing a faq
     */
    save() {
        this.isSaving = true;
        this.faq.faqState = this.isAtLeastInstructor ? FaqState.ACCEPTED : FaqState.PROPOSED;
        if (this.faq.id !== undefined) {
            this.subscribeToSaveResponse(this.faqService.update(this.courseId, this.faq));
        } else {
            this.subscribeToSaveResponse(this.faqService.create(this.courseId, this.faq));
        }
    }

    /**
     * @param result The Http response from the server
     */
    protected subscribeToSaveResponse(result: Observable<HttpResponse<Faq>>) {
        result.subscribe({
            next: (response: HttpResponse<Faq>) => this.onSaveSuccess(response.body!),
            error: (error: HttpErrorResponse) => this.onSaveError(error),
        });
    }

    /**
     * Action on successful faq creation or edit
     */
    protected onSaveSuccess(faq: Faq) {
        if (!this.faq.id) {
            this.faqService.find(this.courseId, faq.id!).subscribe({
                next: (response: HttpResponse<Faq>) => {
                    this.isSaving = false;
                    const faqBody = response.body;
                    if (faqBody) {
                        this.faq = faqBody;
                    }
                    this.showSuccessAlert(faq, false);
                    this.router.navigate(['course-management', this.courseId, 'faqs']);
                },
            });
        } else {
            this.showSuccessAlert(faq, true);
            this.router.navigate(['course-management', this.courseId, 'faqs']);
        }
    }

    private showSuccessAlert(faq: Faq, newlyCreated: boolean): void {
        let messageKey: string;

        if (this.isAtLeastInstructor) {
            messageKey = newlyCreated ? 'artemisApp.faq.updated' : 'artemisApp.faq.created';
        } else {
            messageKey = newlyCreated ? 'artemisApp.faq.proposedChange' : 'artemisApp.faq.proposed';
        }
        this.alertService.success(messageKey, { title: faq.questionTitle });
    }

    /**
     * Action on unsuccessful faq creation or edit
     * @param errorRes the errorRes handed to the alert service
     */
    protected onSaveError(errorRes: HttpErrorResponse) {
        this.isSaving = false;
        if (errorRes.error?.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        } else {
            onError(this.alertService, errorRes);
        }
    }

    updateCategories(categories: FaqCategory[]) {
        this.faq.categories = categories;
        this.faqCategories = categories;
    }

    private loadCourseFaqCategories(courseId: number) {
        loadCourseFaqCategories(courseId, this.alertService, this.faqService).subscribe((existingCategories) => {
            this.existingCategories = existingCategories;
        });
    }

    validate() {
        if (this.faq.questionTitle && this.faq.questionAnswer) {
            this.isAllowedToSave = this.faq.questionTitle?.trim().length > 0 && this.faq.questionAnswer?.trim().length > 0;
        } else {
            this.isAllowedToSave = false;
        }
    }

    handleMarkdownChange(markdown: string): void {
        this.faq.questionAnswer = markdown;
        this.validate();
    }

    dismissConsistencyCheck() {
        this.renderedConsistencyCheckResultMarkdown.set({
            result: '',
            inconsistencies: [],
            suggestions: [],
            improvement: '',
        });
    }
}
