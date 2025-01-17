import { NgModule } from '@angular/core';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time/student-exam-working-time.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { TestExamWorkingTimeComponent } from 'app/exam/shared/testExam-workingTime/test-exam-working-time.component';
import { WorkingTimeControlComponent } from 'app/exam/shared/working-time-control/working-time-control.component';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { WorkingTimeChangeComponent } from 'app/exam/shared/working-time-change/working-time-change.component';

@NgModule({
    imports: [
        ArtemisSharedCommonModule,
        ArtemisMarkdownModule,
        StudentExamWorkingTimeComponent,
        TestExamWorkingTimeComponent,
        WorkingTimeControlComponent,
        WorkingTimeChangeComponent,
        ExamLiveEventComponent,
    ],
    exports: [StudentExamWorkingTimeComponent, TestExamWorkingTimeComponent, WorkingTimeControlComponent, WorkingTimeChangeComponent, ExamLiveEventComponent],
})
export class ArtemisExamSharedModule {}
