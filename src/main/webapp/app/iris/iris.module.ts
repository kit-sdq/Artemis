import { NgModule } from '@angular/core';
import { MatDialogModule } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';
import { RouterModule } from '@angular/router';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { IrisSettingsUpdateComponent } from './settings/iris-settings-update/iris-settings-update.component';
import { IrisGlobalSettingsUpdateComponent } from './settings/iris-global-settings-update/iris-global-settings-update.component';
import { IrisCommonSubSettingsUpdateComponent } from './settings/iris-settings-update/iris-common-sub-settings-update/iris-common-sub-settings-update.component';
import { IrisCourseSettingsUpdateComponent } from 'app/iris/settings/iris-course-settings-update/iris-course-settings-update.component';
import { IrisExerciseSettingsUpdateComponent } from 'app/iris/settings/iris-exercise-settings-update/iris-exercise-settings-update.component';
import { IrisExerciseChatbotButtonComponent } from 'app/iris/exercise-chatbot/exercise-chatbot-button.component';
import { IrisChatbotWidgetComponent } from 'app/iris/exercise-chatbot/widget/chatbot-widget.component';
import { IrisEnabledComponent } from 'app/iris/settings/shared/iris-enabled.component';
import { IrisLogoButtonComponent } from 'app/iris/iris-logo-button/iris-logo-button.component';

import { IrisBaseChatbotComponent } from 'app/iris/base-chatbot/iris-base-chatbot.component';
import { ChatStatusBarComponent } from 'app/iris/base-chatbot/chat-status-bar/chat-status-bar.component';
import { CourseChatbotComponent } from 'app/iris/course-chatbot/course-chatbot.component';
import { IrisLogoComponent } from 'app/iris/iris-logo/iris-logo.component';

@NgModule({
    imports: [
        CommonModule,
        MatDialogModule,
        FormsModule,
        FontAwesomeModule,
        ArtemisSharedModule,
        ArtemisMarkdownModule,
        ArtemisSharedComponentModule,
        RouterModule,
        IrisLogoComponent,
        IrisBaseChatbotComponent,
        IrisChatbotWidgetComponent,
        IrisExerciseChatbotButtonComponent,
        IrisSettingsUpdateComponent,
        IrisGlobalSettingsUpdateComponent,
        IrisCourseSettingsUpdateComponent,
        IrisExerciseSettingsUpdateComponent,
        IrisCommonSubSettingsUpdateComponent,
        IrisEnabledComponent,
        ChatStatusBarComponent,
        IrisLogoButtonComponent,
        CourseChatbotComponent,
    ],
    exports: [IrisExerciseChatbotButtonComponent, IrisEnabledComponent, IrisLogoButtonComponent, IrisBaseChatbotComponent, CourseChatbotComponent],
})
export class IrisModule {}
