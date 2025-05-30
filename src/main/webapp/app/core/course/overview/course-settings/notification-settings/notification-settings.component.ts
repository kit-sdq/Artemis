import { Component, OnDestroy, inject } from '@angular/core';
import { CourseSettingCategoryDirective } from 'app/core/course/overview/course-settings/directive/course-setting-category.directive';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBell, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { CourseNotificationSettingPreset } from 'app/communication/shared/entities/course-notification/course-notification-setting-preset';
import { CourseNotificationPresetPickerComponent } from 'app/communication/course-notification/course-notification-preset-picker/course-notification-preset-picker.component';
import { CourseNotificationSettingSpecification } from 'app/communication/shared/entities/course-notification/course-notification-setting-specification';
import { CourseNotificationSettingSpecificationCardComponent } from 'app/communication/course-notification/course-notification-setting-specification-card/course-notification-setting-specification-card.component';
import { CourseNotificationSettingService } from 'app/communication/course-notification/course-notification-setting.service';
import { CourseNotificationService } from 'app/communication/course-notification/course-notification.service';
import { CourseNotificationInfo } from 'app/communication/shared/entities/course-notification/course-notification-info';
import { CourseNotificationSettingInfo } from 'app/communication/shared/entities/course-notification/course-notification-setting-info';
import { CourseNotificationSettingsMap } from 'app/communication/shared/entities/course-notification/course-notification-settings-map';

/**
 * Component that manages notification settings for a course.
 * Extends CourseSettingCategoryDirective to integrate with the course settings framework.
 */
@Component({
    selector: 'jhi-notification-settings',
    imports: [TranslateDirective, FaIconComponent, CourseNotificationPresetPickerComponent, CourseNotificationSettingSpecificationCardComponent],
    templateUrl: './notification-settings.component.html',
    styleUrl: './notification-settings.component.scss',
})
export class NotificationSettingsComponent extends CourseSettingCategoryDirective implements OnDestroy {
    protected readonly courseNotificationSettingService: CourseNotificationSettingService = inject(CourseNotificationSettingService);
    protected readonly courseNotificationService: CourseNotificationService = inject(CourseNotificationService);

    // Icons
    protected readonly faBell = faBell;
    protected readonly faSpinner = faSpinner;

    protected selectableSettingPresets: CourseNotificationSettingPreset[];
    protected selectedSettingPreset?: CourseNotificationSettingPreset;
    protected isLoading = true;
    protected settingInfo?: CourseNotificationSettingInfo;
    protected info?: CourseNotificationInfo;
    protected notificationSpecifications: CourseNotificationSettingSpecification[] = [];

    constructor() {
        super();
    }

    /**
     * Initializes component values once both settingInfo and info are available.
     * Sets up selectable presets, current preset, and notification specifications.
     */
    initializeValues() {
        this.selectableSettingPresets = this.info!.presets;

        this.selectedSettingPreset =
            this.settingInfo!.selectedPreset === 0 ? undefined : this.selectableSettingPresets.find((preset) => preset.typeId === this.settingInfo!.selectedPreset)!;

        if (this.settingInfo!.selectedPreset === 0) {
            this.updateSpecificationArrayByNotificationMap(this.settingInfo!.notificationTypeChannels!, this.settingInfo!.notificationTypeChannels![1] === undefined);
        } else if (this.settingInfo!.selectedPreset !== 0) {
            this.updateSpecificationArrayByNotificationMap(this.selectedSettingPreset!.presetMap!, true);
        }

        this.isLoading = false;
    }

    /**
     * Handles selection of a notification preset.
     * Updates UI and schedules settings update to server with debouncing.
     *
     * @param presetTypeId - The ID of the selected preset (0 for custom settings)
     */
    presetSelected(presetTypeId: number) {
        this.selectedSettingPreset = presetTypeId === 0 ? undefined : this.selectableSettingPresets.find((preset) => preset.typeId === presetTypeId)!;

        this.courseNotificationSettingService.setSettingPreset(this.courseId, presetTypeId, this.selectedSettingPreset);
    }

    /**
     * Handles changes to individual notification settings.
     * Resets selected preset to custom and schedules settings update with debouncing.
     *
     * @param specification - The notification specification that was changed
     */
    optionChanged(specification: CourseNotificationSettingSpecification) {
        this.courseNotificationSettingService.setSettingSpecification(this.courseId, specification, this.selectedSettingPreset);

        this.selectedSettingPreset = undefined;
    }

    /**
     * Lifecycle hook from CourseSettingCategoryDirective.
     * Called when the course information becomes available.
     * Empty implementation as initialization is handled in onCourseIdAvailable.
     */
    onCourseAvailable(): void {}

    /**
     * Lifecycle hook from CourseSettingCategoryDirective.
     * Called when the course ID becomes available.
     * Fetches notification settings and info from the server.
     */
    onCourseIdAvailable(): void {
        this.courseNotificationSettingService.getSettingInfo(this.courseId, true).subscribe((settingInfo) => {
            if (settingInfo) {
                this.settingInfo = settingInfo;

                if (this.info) {
                    this.initializeValues();
                }
            }
        });

        this.courseNotificationService.getInfo().subscribe((info) => {
            if (info.body) {
                this.info = info.body;

                if (this.settingInfo) {
                    this.initializeValues();
                }
            }
        });
    }

    ngOnDestroy() {
        super.ngOnDestroy();
    }

    /**
     * Creates notification specifications from a notification map.
     * Used to update the UI based on either user selections or preset values.
     *
     * @param notificationMap - Map of notification types to channel settings
     * @param useValue - Whether to use the value (true) or key (false) as the lookup in the map
     */
    private updateSpecificationArrayByNotificationMap(notificationMap: CourseNotificationSettingsMap, useValue: boolean) {
        this.notificationSpecifications = [];
        Object.entries(this.info!.notificationTypes!).forEach(([key, value]) => {
            this.notificationSpecifications.push(new CourseNotificationSettingSpecification(value, Number(key), notificationMap[useValue ? value : key]));
        });
    }
}
