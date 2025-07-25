import { Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const ltiLaunchRoutes: Routes = [
    {
        path: 'launch',
        loadComponent: () => import('app/lti/overview/lti13-exercise-launch/lti13-exercise-launch.component').then((m) => m.Lti13ExerciseLaunchComponent),
        data: {
            pageTitle: 'artemisApp.lti13.launch.title',
        },
    },
    {
        path: 'dynamic-registration',
        loadComponent: () => import('app/lti/overview/lti13-dynamic-registration/lti13-dynamic-registration.component').then((m) => m.Lti13DynamicRegistrationComponent),
        data: {
            authorities: [Authority.ADMIN],
            pageTitle: 'artemisApp.lti13.dynamicRegistration.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'select-content',
        loadComponent: () => import('app/lti/overview/lti13-select-content/lti13-select-content.component').then((m) => m.Lti13SelectContentComponent),
        data: {
            pageTitle: 'artemisApp.lti13.deepLinking.title',
        },
    },
    {
        path: 'select-course',
        loadComponent: () => import('app/lti/manage/lti13-select-course/lti13-select-course.component').then((m) => m.LtiCoursesComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lti13.selectCourse',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        path: 'exercises/:courseId',
        loadComponent: () => import('app/lti/manage/lti13-deep-linking/lti13-deep-linking.component').then((m) => m.Lti13DeepLinkingComponent),
        data: {
            authorities: [Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lti13.deepLinking.title',
        },
        canActivate: [UserRouteAccessService],
    },
];
