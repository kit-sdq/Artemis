import { NgModule } from '@angular/core';
import { HasAnyAuthorityDirective } from 'app/shared/auth/has-any-authority.directive';
import { CircularProgressBarComponent } from 'app/shared/circular-progress-bar/circular-progress-bar.component';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { DeleteDialogComponent } from 'app/shared/delete-dialog/delete-dialog.component';
import { SecureLinkDirective } from 'app/shared/http/secure-link.directive';
import { SecuredImageComponent } from 'app/shared/image/secured-image.component';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';

import { CustomMaxDirective } from 'app/shared/validators/custom-max-validator.directive';
import { CustomMinDirective } from 'app/shared/validators/custom-min-validator.directive';
import { OrganizationSelectorComponent } from './organization-selector/organization-selector.component';
import { AdditionalFeedbackComponent } from './additional-feedback/additional-feedback.component';
import { ResizeableContainerComponent } from './resizeable-container/resizeable-container.component';
import { RouterModule } from '@angular/router';
import { CustomPatternValidatorDirective } from 'app/shared/validators/custom-pattern-validator.directive';
import { ItemCountComponent } from 'app/shared/pagination/item-count.component';
import { ConsistencyCheckComponent } from 'app/shared/consistency-check/consistency-check.component';
import { AssessmentWarningComponent } from 'app/assessment/assessment-warning/assessment-warning.component';
import { JhiConnectionWarningComponent } from 'app/shared/connection-warning/connection-warning.component';
import { LoadingIndicatorContainerComponent } from 'app/shared/loading-indicator-container/loading-indicator-container.component';
import { CompetencySelectionComponent } from 'app/shared/competency-selection/competency-selection.component';
import { StickyPopoverDirective } from 'app/shared/sticky-popover/sticky-popover.directive';
import { ConfirmEntityNameComponent } from 'app/shared/confirm-entity-name/confirm-entity-name.component';
import { DetailOverviewNavigationBarComponent } from 'app/shared/detail-overview-navigation-bar/detail-overview-navigation-bar.component';
import { ScienceDirective } from 'app/shared/science/science.directive';

@NgModule({
    imports: [
        ArtemisSharedCommonModule,
        RouterModule,
        LoadingIndicatorContainerComponent,
        DetailOverviewNavigationBarComponent,
        CircularProgressBarComponent,
        CompetencySelectionComponent,
        AdditionalFeedbackComponent,
        HasAnyAuthorityDirective,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ConfirmEntityNameComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
        JhiConnectionStatusComponent,
        JhiConnectionWarningComponent,
        OrganizationSelectorComponent,
        CustomMinDirective,
        CustomMaxDirective,
        CustomPatternValidatorDirective,
        ItemCountComponent,
        ConsistencyCheckComponent,
        AssessmentWarningComponent,
        StickyPopoverDirective,
        ScienceDirective,
    ],
    exports: [
        ArtemisSharedCommonModule,
        RouterModule,
        CircularProgressBarComponent,
        ConfirmEntityNameComponent,
        DetailOverviewNavigationBarComponent,
        LoadingIndicatorContainerComponent,
        AdditionalFeedbackComponent,
        HasAnyAuthorityDirective,
        SecuredImageComponent,
        DeleteButtonDirective,
        DeleteDialogComponent,
        ResizeableContainerComponent,
        SecureLinkDirective,
        JhiConnectionStatusComponent,
        JhiConnectionWarningComponent,
        OrganizationSelectorComponent,
        CustomMinDirective,
        CustomMaxDirective,
        CustomPatternValidatorDirective,
        ItemCountComponent,
        ConsistencyCheckComponent,
        AssessmentWarningComponent,
        CompetencySelectionComponent,
        StickyPopoverDirective,
        ScienceDirective,
    ],
})
export class ArtemisSharedModule {}
