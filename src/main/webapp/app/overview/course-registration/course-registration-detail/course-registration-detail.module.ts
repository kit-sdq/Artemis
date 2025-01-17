import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { CourseRegistrationDetailComponent } from 'app/overview/course-registration/course-registration-detail/course-registration-detail.component';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

import { CoursePrerequisitesButtonModule } from 'app/overview/course-registration/course-prerequisites-button/course-prerequisites-button.module';
import { CourseRegistrationButtonModule } from 'app/overview/course-registration/course-registration-button/course-registration-button.module';

const routes: Routes = [
    {
        path: '',
        loadComponent: () =>
            import('app/overview/course-registration/course-registration-detail/course-registration-detail.component').then((m) => m.CourseRegistrationDetailComponent),
        data: {
            authorities: [Authority.USER],
            pageTitle: 'artemisApp.studentDashboard.enroll.title',
        },
        canActivate: [UserRouteAccessService],
    },
];

@NgModule({
    imports: [
        ArtemisSharedModule,
        ArtemisSharedComponentModule,
        RouterModule.forChild(routes),
        CoursePrerequisitesButtonModule,
        CourseRegistrationButtonModule,
        CourseRegistrationDetailComponent,
    ],
})
export class CourseRegistrationDetailModule {}
