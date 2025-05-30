import { Component, ContentChild, EventEmitter, Input, Output, TemplateRef } from '@angular/core';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

@Component({
    selector: 'jhi-tutorial-group-sessions-table',
    template: `
        <div>
            @for (session of sessions; track session) {
                <div jhi-session-row [extraColumn]="extraColumn" [session]="session" [timeZone]="timeZone" [showIdColumn]="showIdColumn"></div>
            }
        </div>
    `,
})
export class TutorialGroupSessionsTableStubComponent {
    @ContentChild(TemplateRef, { static: true }) extraColumn: TemplateRef<any>;

    @Input()
    sessions: TutorialGroupSession[] = [];

    @Input()
    timeZone?: string = undefined;

    @Input()
    showIdColumn = false;

    @Input()
    tutorialGroup: TutorialGroup;

    @Input()
    isReadOnly = false;

    @Output() attendanceUpdated = new EventEmitter<void>();
}
@Component({
    selector: '[jhi-session-row]',
    template: `
        <div>
            @if (showIdColumn) {
                <div>
                    <span>{{ session.id }}</span>
                </div>
            }
            @if (extraColumn) {
                <div>
                    <ng-template [ngTemplateOutlet]="extraColumn" [ngTemplateOutletContext]="{ $implicit: session }" />
                </div>
            }
        </div>
    `,
})
export class TutorialGroupSessionRowStubComponent {
    @Input()
    showIdColumn = false;

    @Input() extraColumn: TemplateRef<any>;

    @Input() session: TutorialGroupSession;
    @Input() timeZone?: string = undefined;
    @Input() tutorialGroup: TutorialGroup;

    @Input()
    isReadOnly = false;
    @Output() attendanceChanged = new EventEmitter<TutorialGroupSession>();
}
