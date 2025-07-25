import { ComponentFixture, TestBed } from '@angular/core/testing';
import { StandardizedCompetencyFilterStubComponent } from 'test/helpers/stubs/atlas/standardized-competency-filter-stub.component';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { HtmlForMarkdownPipe } from 'app/shared/pipes/html-for-markdown.pipe';
import { StandardizedCompetencyDetailStubComponent } from 'test/helpers/stubs/atlas/standardized-competency-detail-stub.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Competency } from 'app/atlas/shared/entities/competency.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { KnowledgeAreaTreeStubComponent } from 'test/helpers/stubs/atlas/knowledge-area-tree-stub.component';
import { SortService } from 'app/shared/service/sort.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { Source } from 'app/atlas/shared/entities/standardized-competency.model';
import { DocumentationButtonComponent } from 'app/shared/components/buttons/documentation-button/documentation-button.component';
import { CourseImportStandardizedPrerequisitesComponent } from 'app/atlas/manage/import-standardized-competencies/course-import-standardized-prerequisites.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { StandardizedCompetencyFilterComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyService } from 'app/atlas/shared/standardized-competencies/standardized-competency.service';
import { StandardizedCompetencyDetailComponent } from 'app/atlas/shared/standardized-competencies/standardized-competency-detail.component';
import { KnowledgeAreaTreeComponent } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('CourseImportStandardizedPrerequisitesComponent', () => {
    let componentFixture: ComponentFixture<CourseImportStandardizedPrerequisitesComponent>;
    let component: CourseImportStandardizedPrerequisitesComponent;
    const courseId = 1;
    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: courseId }) },
    } as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [
                CourseImportStandardizedPrerequisitesComponent,
                StandardizedCompetencyFilterComponent,
                StandardizedCompetencyDetailComponent,
                KnowledgeAreaTreeComponent,
                FaIconComponent,
            ],
            declarations: [
                StandardizedCompetencyFilterStubComponent,
                StandardizedCompetencyDetailStubComponent,
                KnowledgeAreaTreeStubComponent,
                MockComponent(ButtonComponent),
                MockDirective(SortDirective),
                MockPipe(HtmlForMarkdownPipe),
                MockComponent(DocumentationButtonComponent),
            ],
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: Router, useClass: MockRouter },
                MockProvider(PrerequisiteService),
                MockProvider(StandardizedCompetencyService),
                MockProvider(SortService),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CourseImportStandardizedPrerequisitesComponent);
                component = componentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load data', () => {
        const dtoTree = [
            {
                id: 1,
                children: [
                    {
                        id: 11,
                        parentId: 1,
                        children: [
                            {
                                id: 111,
                                parentId: 11,
                            },
                        ],
                    },
                ],
            },
            {
                id: 2,
                children: [
                    {
                        id: 21,
                        parentId: 2,
                    },
                ],
                competencies: [
                    {
                        id: 1,
                    },
                ],
            },
        ];
        const sources: Source[] = [{ id: 1 }, { id: 2 }];

        const standardizedCompetencyService = TestBed.inject(StandardizedCompetencyService);
        const getForTreeViewSpy = jest.spyOn(standardizedCompetencyService, 'getAllForTreeView').mockReturnValue(of(new HttpResponse({ body: dtoTree })));
        const getSourcesSpy = jest.spyOn(standardizedCompetencyService, 'getSources').mockReturnValue(of(new HttpResponse({ body: sources })));

        componentFixture.detectChanges();

        expect(getForTreeViewSpy).toHaveBeenCalled();
        expect(getSourcesSpy).toHaveBeenCalled();
        expect(component['knowledgeAreaMap'].size).toBe(5);
        expect(component['knowledgeAreasForSelect']).toHaveLength(5);
        expect(component['sources']).toHaveLength(2);
    });

    it('should open details', () => {
        component['sources'] = [{ id: 1, title: 'title1', author: 'author1' }];
        const competencyToOpen = { id: 2, isVisible: true, selected: true, sourceId: 1 };

        component['openCompetencyDetails'](competencyToOpen);

        expect(component['selectedCompetency']).toEqual(competencyToOpen);
        expect(component['sourceString']).toBeTruthy();
    });

    it('should close details', () => {
        component['selectedCompetency'] = { id: 2, isVisible: true, selected: true };

        component['closeCompetencyDetails']();

        expect(component['selectedCompetency']).toBeUndefined();
    });

    it('should toggle select', () => {
        const competencyToSelect = { id: 2, isVisible: true, selected: true };
        component['selectedCompetencies'] = [{ id: 1, isVisible: true, selected: true }];

        competencyToSelect.selected = true;
        component['toggleSelect'](competencyToSelect);

        expect(component['selectedCompetencies']).toHaveLength(2);

        competencyToSelect.selected = false;
        component['toggleSelect'](competencyToSelect);

        expect(component['selectedCompetencies']).toHaveLength(1);
    });

    it('should deselect', () => {
        const competencyToDeselect = { id: 2, isVisible: true, selected: true };
        component['selectedCompetencies'] = [{ id: 1, isVisible: true, selected: true }, competencyToDeselect];

        component['deselectCompetency'](competencyToDeselect);

        expect(component['selectedCompetencies']).toHaveLength(1);
        expect(competencyToDeselect.selected).toBeFalse();
    });

    it('should sort', () => {
        const sortService = TestBed.inject(SortService);
        const sortSpy = jest.spyOn(sortService, 'sortByProperty');

        component['sortSelected']({ predicate: 'title', ascending: true });

        expect(sortSpy).toHaveBeenCalledWith([], 'title', true);
    });

    it('should navigate after importing competencies', () => {
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');
        const prerequisiteService = TestBed.inject(PrerequisiteService);
        const importSpy = jest.spyOn(prerequisiteService, 'importStandardizedCompetencies').mockReturnValue(
            of({
                body: [{ id: 1 }, { id: 2 }],
            } as HttpResponse<Competency[]>),
        );
        component['selectedCompetencies'] = [
            { id: 11, isVisible: true },
            { id: 12, isVisible: true },
        ];

        component['importCompetencies']();

        expect(importSpy).toHaveBeenCalled();
        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should cancel', () => {
        const router: Router = TestBed.inject(Router);
        const navigateSpy = jest.spyOn(router, 'navigate');

        component['cancel']();

        expect(navigateSpy).toHaveBeenCalled();
    });

    it('should not deactivate with pending changes', () => {
        let canDeactivate;

        component['isLoading'] = false;
        canDeactivate = component.canDeactivate();
        expect(canDeactivate).toBeTrue();

        component['isLoading'] = true;
        canDeactivate = component.canDeactivate();
        expect(canDeactivate).toBeFalse();
    });
});
