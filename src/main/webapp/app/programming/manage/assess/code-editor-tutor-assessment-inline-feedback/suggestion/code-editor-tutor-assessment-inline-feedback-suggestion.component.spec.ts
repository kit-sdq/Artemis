import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { EventEmitter } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent } from 'app/programming/manage/assess/code-editor-tutor-assessment-inline-feedback/suggestion/code-editor-tutor-assessment-inline-feedback-suggestion.component';
import { MockComponent } from 'ng-mocks';
import { FeedbackSuggestionBadgeComponent } from 'app/exercise/feedback/feedback-suggestion-badge/feedback-suggestion-badge.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent', () => {
    let component: CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent;
    let fixture: ComponentFixture<CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [FaIconComponent],
            declarations: [CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent, MockComponent(FeedbackSuggestionBadgeComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(CodeEditorTutorAssessmentInlineFeedbackSuggestionComponent);
        component = fixture.componentInstance;
        component.feedback = new Feedback();
        component.course = new Course();
        component.onAcceptSuggestion = new EventEmitter();
        component.onDiscardSuggestion = new EventEmitter();
        fixture.detectChanges();
    });

    it('should emit onAcceptSuggestion event when Accept button is clicked', () => {
        jest.spyOn(component.onAcceptSuggestion, 'emit');
        const acceptButton = fixture.debugElement.query(By.css('.btn-success')).nativeElement;
        acceptButton.click();
        expect(component.onAcceptSuggestion.emit).toHaveBeenCalledWith(component.feedback);
    });

    it('should emit onDiscardSuggestion event when Discard button is clicked', () => {
        jest.spyOn(component.onDiscardSuggestion, 'emit');
        const discardButton = fixture.debugElement.query(By.css('.btn-danger')).nativeElement;
        discardButton.click();
        expect(component.onDiscardSuggestion.emit).toHaveBeenCalledWith(component.feedback);
    });
});
