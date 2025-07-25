import { Routes } from '@angular/router';

import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';

export const ltiConfigurationRoute: Routes = [
    {
        path: 'lti-configuration',
        loadComponent: () => import('app/core/admin/lti-configuration/lti-configuration.component').then((m) => m.LtiConfigurationComponent),
        data: {
            pageTitle: 'global.menu.admin.lti',
            defaultSort: 'id,desc',
        },
    },
    {
        path: 'lti-configuration',
        data: {
            pageTitle: 'global.menu.admin.lti',
        },
        children: [
            {
                path: 'new',
                loadComponent: () => import('app/core/admin/lti-configuration/edit/edit-lti-configuration.component').then((m) => m.EditLtiConfigurationComponent),
                data: {
                    authorities: [Authority.ADMIN],
                    pageTitle: 'artemisApp.lti.addLtiPlatform',
                },
                canActivate: [UserRouteAccessService],
            },
            {
                path: ':platformId',
                data: {
                    breadcrumbLabelVariable: 'platform.id',
                },
                children: [
                    {
                        path: 'edit',
                        loadComponent: () => import('app/core/admin/lti-configuration/edit/edit-lti-configuration.component').then((m) => m.EditLtiConfigurationComponent),
                        data: {
                            authorities: [Authority.ADMIN],
                            pageTitle: 'artemisApp.lti.editLtiPlatform',
                        },
                        canActivate: [UserRouteAccessService],
                    },
                ],
            },
        ],
    },
];
