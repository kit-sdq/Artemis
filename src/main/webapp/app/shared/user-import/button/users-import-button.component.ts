import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { UsersImportDialogComponent } from 'app/shared/user-import/dialog/users-import-dialog.component';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faFileImport } from '@fortawesome/free-solid-svg-icons';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

@Component({
    selector: 'jhi-user-import-button',
    template: `
        <jhi-button [btnType]="buttonType" [btnSize]="buttonSize" [icon]="faFileImport" [title]="'artemisApp.importUsers.buttonLabel'" (onClick)="openUsersImportDialog($event)" />
    `,
    imports: [ButtonComponent],
})
export class UsersImportButtonComponent {
    private modalService = inject(NgbModal);

    ButtonType = ButtonType;
    ButtonSize = ButtonSize;

    @Input() tutorialGroup: TutorialGroup | undefined = undefined;
    @Input() examUserMode: boolean;
    @Input() adminUserMode: boolean;
    @Input() courseGroup: CourseGroup;
    @Input() courseId: number;
    @Input() buttonSize: ButtonSize = ButtonSize.MEDIUM;
    @Input() buttonType: ButtonType = ButtonType.PRIMARY;
    @Input() exam: Exam;

    @Output() importDone: EventEmitter<void> = new EventEmitter();

    // Icons
    faFileImport = faFileImport;

    /**
     * Open up import dialog for users
     * @param {Event} event - Mouse Event which invoked the opening
     */
    openUsersImportDialog(event: MouseEvent) {
        event.stopPropagation();
        const modalRef: NgbModalRef = this.modalService.open(UsersImportDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        modalRef.componentInstance.courseId = this.courseId;
        modalRef.componentInstance.courseGroup = this.courseGroup;
        modalRef.componentInstance.exam = this.exam;
        modalRef.componentInstance.tutorialGroup = this.tutorialGroup;
        modalRef.componentInstance.examUserMode = this.examUserMode;
        modalRef.componentInstance.adminUserMode = this.adminUserMode;
        modalRef.result.then(
            () => this.importDone.emit(),
            () => {},
        );
    }
}
