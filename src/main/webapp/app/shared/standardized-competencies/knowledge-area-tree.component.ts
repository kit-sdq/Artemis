import { NestedTreeControl } from '@angular/cdk/tree';
import { Component, ContentChild, Input, TemplateRef } from '@angular/core';
import { MatTreeNestedDataSource } from '@angular/material/tree';
import { faChevronRight } from '@fortawesome/free-solid-svg-icons';
import { KnowledgeAreaForTree } from 'app/entities/competency/standardized-competency.model';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { MatTreeModule } from '@angular/material/tree';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbCollapse } from '@ng-bootstrap/ng-bootstrap';

@Component({
    selector: 'jhi-knowledge-area-tree',
    templateUrl: './knowledge-area-tree.component.html',
    styleUrls: ['./knowledge-area-tree.component.scss'],
    imports: [ArtemisSharedCommonModule, MatTreeModule, FaIconComponent, NgbCollapse],
})
export class KnowledgeAreaTreeComponent {
    @Input() dataSource: MatTreeNestedDataSource<KnowledgeAreaForTree> = new MatTreeNestedDataSource<KnowledgeAreaForTree>();
    @Input() treeControl: NestedTreeControl<KnowledgeAreaForTree> = new NestedTreeControl<KnowledgeAreaForTree>((node) => node.children);

    @ContentChild('knowledgeAreaTemplate') knowledgeAreaTemplate: TemplateRef<any>;
    @ContentChild('competencyTemplate') competencyTemplate: TemplateRef<any>;

    //Icons
    protected readonly faChevronRight = faChevronRight;
    //constants
    readonly trackBy = (_: number, node: KnowledgeAreaForTree) => node.id;
}
