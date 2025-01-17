import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { NgxDatatableModule } from '@siemens/ngx-datatable';
import { ArtemisResultModule } from 'app/exercises/shared/result/result.module';
import { ParticipationSubmissionComponent } from 'app/exercises/shared/participation-submission/participation-submission.component';
import { ArtemisParticipationSubmissionRoutingModule } from 'app/exercises/shared/participation-submission/participation-submission-routing.module';
import { SubmissionResultStatusModule } from 'app/overview/submission-result-status.module';

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisParticipationSubmissionRoutingModule,
        NgxDatatableModule,
        ArtemisResultModule,
        SubmissionResultStatusModule,
        ParticipationSubmissionComponent,
    ],
})
export class ArtemisParticipationSubmissionModule {}
