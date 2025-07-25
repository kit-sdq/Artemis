import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { generateExampleTutorialGroup } from 'test/helpers/sample/tutorialgroup/tutorialGroupExampleModels';
import { User } from 'app/core/user/user.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockDirective } from 'ng-mocks';
import { TranslatePipeMock } from 'test/helpers/mocks/service/mock-translate.service';
import { RouterModule } from '@angular/router';
import { CourseTutorialGroupCardComponent } from 'app/tutorialgroup/overview/course-tutorial-group-card/course-tutorial-group-card.component';

describe('CourseTutorialGroupCardComponent', () => {
    let component: CourseTutorialGroupCardComponent;
    let fixture: ComponentFixture<CourseTutorialGroupCardComponent>;
    let exampleTutorialGroup: TutorialGroup;
    let exampleTA: User;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), FaIconComponent],
            declarations: [CourseTutorialGroupCardComponent, TranslatePipeMock, MockDirective(TranslateDirective)],
            providers: [
                {
                    provide: TranslateService,
                    useValue: {
                        instant: (key: string) => key,
                        get: (key: string) => key,
                    },
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CourseTutorialGroupCardComponent);
        component = fixture.componentInstance;
        exampleTA = { id: 1, name: 'TA' } as User;
        exampleTutorialGroup = generateExampleTutorialGroup({ teachingAssistant: exampleTA });
        fixture.componentRef.setInput('tutorialGroup', exampleTutorialGroup);
        fixture.componentRef.setInput('course', { id: 1 });
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
