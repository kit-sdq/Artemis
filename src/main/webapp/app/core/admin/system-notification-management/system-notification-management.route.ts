import { Route } from '@angular/router';

import { SystemNotificationManagementResolve } from 'app/core/admin/system-notification-management/system-notification-management-resolve.service';

export const systemNotificationManagementRoute: Route[] = [
    {
        path: 'system-notification-management',
        loadComponent: () => import('app/core/admin/system-notification-management/system-notification-management.component').then((m) => m.SystemNotificationManagementComponent),
        data: {
            pageTitle: 'artemisApp.systemNotification.systemNotifications',
            defaultSort: 'id,asc',
        },
    },
    {
        // Create a new path without a component defined to prevent resolver caching and the SystemNotificationManagementComponent from being always rendered
        path: 'system-notification-management',
        data: {
            pageTitle: 'artemisApp.systemNotification.systemNotifications',
        },
        children: [
            {
                path: 'new',
                loadComponent: () =>
                    import('app/core/admin/system-notification-management/system-notification-management-update.component').then(
                        (m) => m.SystemNotificationManagementUpdateComponent,
                    ),
                data: {
                    pageTitle: 'global.generic.create',
                },
            },
            {
                path: ':id',
                loadComponent: () =>
                    import('app/core/admin/system-notification-management/system-notification-management-detail.component').then(
                        (m) => m.SystemNotificationManagementDetailComponent,
                    ),
                resolve: {
                    notification: SystemNotificationManagementResolve,
                },
                data: {
                    pageTitle: 'artemisApp.systemNotification.systemNotifications',
                    breadcrumbLabelVariable: 'notification.body.id',
                },
            },
            {
                // Create a new path without a component defined to prevent resolver caching and the SystemNotificationManagementDetailComponent from being always rendered
                path: ':id',
                resolve: {
                    notification: SystemNotificationManagementResolve,
                },
                data: {
                    breadcrumbLabelVariable: 'notification.body.id',
                },
                children: [
                    {
                        path: 'edit',
                        loadComponent: () =>
                            import('app/core/admin/system-notification-management/system-notification-management-update.component').then(
                                (m) => m.SystemNotificationManagementUpdateComponent,
                            ),
                        data: {
                            pageTitle: 'global.generic.edit',
                            breadcrumbLabelVariable: '',
                        },
                    },
                ],
            },
        ],
    },
];
