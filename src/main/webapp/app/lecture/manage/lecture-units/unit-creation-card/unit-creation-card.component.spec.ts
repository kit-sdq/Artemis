import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { UnitCreationCardComponent } from 'app/lecture/manage/lecture-units/unit-creation-card/unit-creation-card.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';
import { RouterModule } from '@angular/router';

describe('UnitCreationCardComponent', () => {
    let unitCreationCardComponentFixture: ComponentFixture<UnitCreationCardComponent>;
    let unitCreationCardComponent: UnitCreationCardComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [RouterModule.forRoot([]), FaIconComponent],
            declarations: [UnitCreationCardComponent, MockPipe(ArtemisTranslatePipe), MockComponent(DocumentationButtonComponent), MockDirective(TranslateDirective)],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                unitCreationCardComponentFixture = TestBed.createComponent(UnitCreationCardComponent);
                unitCreationCardComponent = unitCreationCardComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        unitCreationCardComponentFixture.detectChanges();
        expect(unitCreationCardComponent).not.toBeNull();
    });

    it('should emit creation card event', () => {
        const emitSpy = jest.spyOn(unitCreationCardComponent.onUnitCreationCardClicked, 'emit');
        unitCreationCardComponent.emitEvents = true;
        unitCreationCardComponent.onButtonClicked(LectureUnitType.ONLINE);
        expect(emitSpy).toHaveBeenCalledWith(LectureUnitType.ONLINE);
    });
});
