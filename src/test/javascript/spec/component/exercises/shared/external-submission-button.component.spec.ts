import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTestModule } from '../../../test.module';
import { ExternalSubmissionDialogComponent } from 'app/exercises/shared/external-submission/external-submission-dialog.component';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';
import { Exercise } from 'app/entities/exercise.model';
import { By } from '@angular/platform-browser';

describe('External Submission Dialog', () => {
    let fixture: ComponentFixture<ExternalSubmissionButtonComponent>;
    let component: ExternalSubmissionButtonComponent;
    let modalService: NgbModal;

    beforeEach(() => {
        modalService = { open: jest.fn() } as any as NgbModal;
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [{ provide: NgbModal, useValue: modalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExternalSubmissionButtonComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open external submission dialog on click', () => {
        const exercise = { id: 1 } as Exercise;
        component.exercise = exercise;
        const modalRefMock = { componentInstance: {} } as NgbModalRef;
        const openMock = jest.spyOn(modalService, 'open').mockReturnValue(modalRefMock);

        fixture.detectChanges();
        fixture.debugElement.query(By.css('.btn')).nativeElement.click();

        expect(openMock).toHaveBeenCalledOnce();
        expect(openMock).toHaveBeenCalledWith(ExternalSubmissionDialogComponent, { keyboard: true, size: 'lg', backdrop: 'static' });
        expect(modalRefMock.componentInstance.exercise).toBe(exercise);
    });
});
