import { Routes } from '@angular/router';
import { Authority } from 'app/shared/constants/authority.constants';
import { UserRouteAccessService } from 'app/core/auth/user-route-access-service';
import { CourseManagementResolve } from 'app/core/course/manage/services/course-management-resolve.service';
import { AttachmentVideoUnitResolve } from 'app/lecture/manage/lecture-units/services/lecture-unit-management-resolve.service';

export const lectureUnitRoute: Routes = [
    {
        path: 'unit-management',
        loadComponent: () => import('app/lecture/manage/lecture-units/management/lecture-unit-management.component').then((m) => m.LectureUnitManagementComponent),
        data: {
            authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
            pageTitle: 'artemisApp.lectureUnit.home.title',
        },
        canActivate: [UserRouteAccessService],
    },
    {
        // Create a new path without a component defined to prevent the LectureUnitManagementComponent from being always rendered
        path: 'unit-management',
        data: {
            pageTitle: 'artemisApp.lectureUnit.home.title',
        },
        children: [
            {
                path: 'exercise-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-exercise-unit/create-exercise-unit.component').then((m) => m.CreateExerciseUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.exerciseUnit.createExerciseUnit.title',
                },
            },
            {
                path: 'attachment-video-units/process',
                loadComponent: () =>
                    import('app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component').then((m) => m.AttachmentVideoUnitsComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentVideoUnit.createAttachmentVideoUnits.pageTitle',
                },
            },
            {
                path: 'attachment-video-units/create',
                loadComponent: () =>
                    import('app/lecture/manage/lecture-units/create-attachment-video-unit/create-attachment-video-unit.component').then(
                        (m) => m.CreateAttachmentVideoUnitComponent,
                    ),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentVideoUnit.createAttachmentVideoUnit.title',
                },
            },
            {
                path: 'online-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-online-unit/create-online-unit.component').then((m) => m.CreateOnlineUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.createOnlineUnit.title',
                },
            },
            {
                path: 'text-units/create',
                loadComponent: () => import('app/lecture/manage/lecture-units/create-text-unit/create-text-unit.component').then((m) => m.CreateTextUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.createTextUnit.title',
                },
            },
            {
                path: 'attachment-video-units/:attachmentVideoUnitId/edit',
                loadComponent: () =>
                    import('app/lecture/manage/lecture-units/edit-attachment-video-unit/edit-attachment-video-unit.component').then((m) => m.EditAttachmentVideoUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.attachmentVideoUnit.editAttachmentVideoUnit.title',
                },
            },
            {
                path: 'attachment-video-units/:attachmentVideoUnitId/view',
                loadComponent: () => import('app/lecture/manage/pdf-preview/pdf-preview.component').then((m) => m.PdfPreviewComponent),
                resolve: {
                    course: CourseManagementResolve,
                    attachmentVideoUnit: AttachmentVideoUnitResolve,
                },
            },
            {
                path: 'online-units/:onlineUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-online-unit/edit-online-unit.component').then((m) => m.EditOnlineUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.onlineUnit.editOnlineUnit.title',
                },
            },
            {
                path: 'text-units/:textUnitId/edit',
                loadComponent: () => import('app/lecture/manage/lecture-units/edit-text-unit/edit-text-unit.component').then((m) => m.EditTextUnitComponent),
                data: {
                    authorities: [Authority.EDITOR, Authority.INSTRUCTOR, Authority.ADMIN],
                    pageTitle: 'artemisApp.textUnit.editTextUnit.title',
                },
            },
        ],
    },
];
