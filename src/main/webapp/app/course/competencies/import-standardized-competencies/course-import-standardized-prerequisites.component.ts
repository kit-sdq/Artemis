import { ActivatedRoute, Router } from '@angular/router';
import { Component } from '@angular/core';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { SortService } from 'app/shared/service/sort.service';
import { StandardizedCompetencyService } from 'app/shared/standardized-competencies/standardized-competency.service';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { CourseImportStandardizedCourseCompetenciesComponent } from 'app/course/competencies/import-standardized-competencies/course-import-standardized-course-competencies.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { StandardizedCompetencyFilterComponent } from 'app/shared/standardized-competencies/standardized-competency-filter.component';
import { StandardizedCompetencyDetailComponent } from 'app/shared/standardized-competencies/standardized-competency-detail.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { KnowledgeAreaTreeComponent } from 'app/shared/standardized-competencies/knowledge-area-tree.component';

@Component({
    selector: 'jhi-course-import-standardized-prerequisites',
    templateUrl: './course-import-standardized-prerequisites.component.html',
    standalone: true,
    imports: [
        ArtemisSharedCommonModule,
        ArtemisSharedComponentModule,
        ArtemisMarkdownModule,
        StandardizedCompetencyFilterComponent,
        StandardizedCompetencyDetailComponent,
        KnowledgeAreaTreeComponent,
    ],
})
export class CourseImportStandardizedPrerequisitesComponent extends CourseImportStandardizedCourseCompetenciesComponent {
    constructor(
        router: Router,
        activatedRoute: ActivatedRoute,
        standardizedCompetencyService: StandardizedCompetencyService,
        alertService: AlertService,
        translateService: TranslateService,
        sortService: SortService,
        private prerequisiteService: PrerequisiteService,
    ) {
        super(router, activatedRoute, standardizedCompetencyService, alertService, translateService, sortService);
    }

    protected importCompetencies() {
        super.importCompetencies(this.prerequisiteService);
    }
}
