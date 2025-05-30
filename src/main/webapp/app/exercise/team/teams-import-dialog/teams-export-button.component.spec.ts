import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsExportButtonComponent } from 'app/exercise/team/teams-import-dialog/teams-export-button.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FeatureToggleDirective } from 'app/shared/feature-toggle/feature-toggle.directive';
import { MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
describe('TeamsExportButtonComponent', () => {
    let comp: TeamsExportButtonComponent;
    let fixture: ComponentFixture<TeamsExportButtonComponent>;
    let debugElement: DebugElement;
    let teamService: TeamService;

    function resetComponent() {
        comp.teams = mockTeams;
    }

    beforeEach(waitForAsync(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule), MockDirective(FeatureToggleDirective)],
            declarations: [TeamsExportButtonComponent, ButtonComponent, MockPipe(ArtemisTranslatePipe), MockDirective(TranslateDirective)],
            providers: [MockProvider(TeamService)],
        }).compileComponents();
    }));
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsExportButtonComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        teamService = TestBed.inject(TeamService);
    });

    describe('exportTeams', () => {
        let exportTeamsStub: jest.SpyInstance;
        beforeEach(() => {
            resetComponent();
            exportTeamsStub = jest.spyOn(teamService, 'exportTeams');
        });
        afterEach(() => {
            jest.restoreAllMocks();
        });
        it('should call export teams from team service when called', () => {
            const button = debugElement.nativeElement.querySelector('button');
            button.click();
            expect(exportTeamsStub).toHaveBeenCalledOnce();
        });
    });
});
