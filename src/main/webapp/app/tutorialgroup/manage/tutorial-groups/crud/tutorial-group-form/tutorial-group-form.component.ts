import { ChangeDetectionStrategy, Component, OnChanges, OnDestroy, OnInit, inject, input, output, viewChild } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { Course, CourseGroup } from 'app/core/course/shared/entities/course.model';
import { User } from 'app/core/user/user.model';
import { onError } from 'app/shared/util/global.utils';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Observable, OperatorFunction, Subject, catchError, concat, finalize, forkJoin, map, merge, of } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { debounceTime, distinctUntilChanged, filter, takeUntil } from 'rxjs/operators';
import { NgbTypeahead } from '@ng-bootstrap/ng-bootstrap';
import { isEqual } from 'lodash-es';
import { faSave } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ScheduleFormComponent, ScheduleFormData } from 'app/tutorialgroup/manage/tutorial-groups/crud/tutorial-group-form/schedule-form/schedule-form.component';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';

export interface TutorialGroupFormData {
    title?: string;
    teachingAssistant?: User;
    additionalInformation?: string;
    capacity?: number;
    isOnline?: boolean;
    language?: string;
    campus?: string;
    notificationText?: string; // Only in edit mode
    updateTutorialGroupChannelName?: boolean; // Only in edit mode
    schedule?: ScheduleFormData;
}

export class UserWithLabel extends User {
    label: string;
}

export const titleRegex = new RegExp('^[a-zA-Z0-9]{1}[a-zA-Z0-9- ]{0,19}$');

@Component({
    selector: 'jhi-tutorial-group-form',
    templateUrl: './tutorial-group-form.component.html',
    styleUrls: ['./tutorial-group-form.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [FormsModule, ReactiveFormsModule, TranslateDirective, NgbTypeahead, MarkdownEditorMonacoComponent, ScheduleFormComponent, FaIconComponent, ArtemisTranslatePipe],
})
export class TutorialGroupFormComponent implements OnInit, OnChanges, OnDestroy {
    private fb = inject(FormBuilder);
    private courseManagementService = inject(CourseManagementService);
    private tutorialGroupService = inject(TutorialGroupsService);
    private alertService = inject(AlertService);

    readonly formData = input<TutorialGroupFormData>({
        title: undefined,
        teachingAssistant: undefined,
        additionalInformation: undefined,
        capacity: undefined,
        isOnline: undefined,
        language: undefined,
        campus: undefined,
    });
    readonly course = input.required<Course>();
    readonly isEditMode = input(false);
    readonly formSubmitted = output<TutorialGroupFormData>();

    form: FormGroup;
    // not included in reactive form
    additionalInformation: string | undefined;

    teachingAssistantsAreLoading = false;
    teachingAssistants: UserWithLabel[];
    readonly taTypeAhead = viewChild.required<NgbTypeahead>('teachingAssistantInput');
    taFocus$ = new Subject<string>();
    taClick$ = new Subject<string>();

    campusAreLoading = false;
    campus: string[];
    readonly campusTypeAhead = viewChild.required<NgbTypeahead>('campusInput');
    campusFocus$ = new Subject<string>();
    campusClick$ = new Subject<string>();

    languagesAreLoading = false;
    languages: string[];
    readonly languageTypeAhead = viewChild.required<NgbTypeahead>('languageInput');
    languageFocus$ = new Subject<string>();
    languageClick$ = new Subject<string>();

    configureSchedule = true;
    readonly scheduleFormComponent = viewChild<ScheduleFormComponent>('scheduleForm');
    existingScheduleFormDate: ScheduleFormData | undefined;

    existingTitle: string | undefined;

    // icons
    faSave = faSave;

    ngUnsubscribe = new Subject<void>();

    get titleControl() {
        return this.form.get('title');
    }

    get teachingAssistantControl() {
        return this.form.get('teachingAssistant');
    }

    get campusControl() {
        return this.form.get('campus');
    }

    get capacityControl() {
        return this.form.get('capacity');
    }

    get isOnlineControl() {
        return this.form.get('isOnline');
    }

    get languageControl() {
        return this.form.get('language');
    }

    get notificationControl() {
        return this.form.get('notificationText');
    }

    get updateTutorialGroupChannelNameControl() {
        return this.form.get('updateTutorialGroupChannelName');
    }

    get isSubmitPossible() {
        if (this.configureSchedule) {
            // check all controls
            return !this.form.invalid;
        } else {
            // only check the parts of the form not covered by the schedule form
            return !(
                this.titleControl!.invalid ||
                this.teachingAssistantControl!.invalid ||
                this.capacityControl!.invalid ||
                this.isOnlineControl!.invalid ||
                this.languageControl!.invalid ||
                this.campusControl!.invalid
            );
        }
    }

    get showUpdateChannelNameCheckbox() {
        if (!this.isEditMode()) {
            return false;
        }
        if (!this.existingTitle) {
            return false;
        }
        if (!this.titleControl?.value) {
            return false;
        }
        return this.existingTitle !== this.titleControl!.value;
    }

    get showScheduledChangedWarning() {
        if (!this.isEditMode()) {
            return false;
        }
        const originalHasSchedule = !!this.existingScheduleFormDate;
        if (!originalHasSchedule) {
            return false;
        }
        const updateHasSchedule = this.configureSchedule && this.form.value.schedule;

        if (originalHasSchedule && !updateHasSchedule) {
            return true;
        } else if (!originalHasSchedule && updateHasSchedule) {
            return true;
        } else if (!originalHasSchedule && !updateHasSchedule) {
            return false;
        } else {
            const newScheduleValues = { ...this.form.value.schedule };
            delete newScheduleValues.location;
            const existingScheduleValues = { ...this.existingScheduleFormDate };
            // we do not consider the location when comparing the schedules as change it has no irreversible effect
            delete existingScheduleValues.location;
            return !isEqual(newScheduleValues, existingScheduleValues);
        }
    }

    ngOnInit(): void {
        this.getTeachingAssistantsInCourse();
        this.getUniqueCampusValuesOfCourse();
        this.getUniqueLanguageValuesOfCourse();
        this.initializeForm();
    }

    ngOnDestroy(): void {
        this.ngUnsubscribe.next();
        this.ngUnsubscribe.complete();
    }

    ngOnChanges() {
        this.initializeForm();
        const formData = this.formData();
        if (this.isEditMode() && formData) {
            this.setFormValues(formData);
        }
    }

    submitForm() {
        const tutorialGroupFormData: TutorialGroupFormData = { ...this.form.value };
        tutorialGroupFormData.additionalInformation = this.additionalInformation;
        if (!this.configureSchedule) {
            tutorialGroupFormData.schedule = undefined;
        }
        this.formSubmitted.emit(tutorialGroupFormData);
    }

    trackId(index: number, item: User) {
        return item.id;
    }

    taFormatter = (user: UserWithLabel) => user.label;
    taSearch: OperatorFunction<string, readonly UserWithLabel[]> = (text$: Observable<string>) => {
        return this.mergeSearch$(text$, this.taFocus$, this.taClick$, this.taTypeAhead()).pipe(
            map((term) => (term === '' ? this.teachingAssistants : this.teachingAssistants.filter((ta) => ta.label.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    campusFormatter = (campus: string) => campus;

    campusSearch: OperatorFunction<string, readonly string[]> = (text$: Observable<string>) => {
        return this.mergeSearch$(text$, this.campusFocus$, this.campusClick$, this.campusTypeAhead()).pipe(
            map((term) => (term === '' ? this.campus : this.campus.filter((campus) => campus.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    languageFormatter = (language: string) => language;

    languageSearch: OperatorFunction<string, readonly string[]> = (text$: Observable<string>) => {
        return this.mergeSearch$(text$, this.languageFocus$, this.languageClick$, this.languageTypeAhead()).pipe(
            map((term) => (term === '' ? this.languages : this.languages.filter((language) => language.toLowerCase().indexOf(term.toLowerCase()) > -1))),
        );
    };

    private mergeSearch$(text$: Observable<string>, focus$: Subject<string>, click$: Subject<string>, typeahead: NgbTypeahead) {
        const debouncedText$ = text$.pipe(debounceTime(200), distinctUntilChanged());
        const clicksWithClosedPopup$ = click$.pipe(filter(() => typeahead && !typeahead.isPopupOpen()));
        return merge(debouncedText$, focus$, clicksWithClosedPopup$);
    }

    private initializeForm() {
        if (this.form) {
            return;
        }

        this.form = this.fb.group({
            // Note: We restrict the title so 19 characters so we can create a 20char communication channel name with the prefix $ from the tite
            title: [undefined, [Validators.required, Validators.maxLength(19), Validators.pattern(titleRegex)]],
            teachingAssistant: [undefined, [Validators.required]],
            capacity: [undefined, [Validators.min(1)]],
            isOnline: [false, [Validators.required]],
            language: [undefined, [Validators.required, Validators.maxLength(255)]],
            campus: [undefined, Validators.maxLength(255)],
        });

        if (this.isEditMode()) {
            this.form.addControl('notificationText', new FormControl(undefined, [Validators.maxLength(1000)]));
            this.form.addControl('updateTutorialGroupChannelName', new FormControl(true));
        }
    }

    private setFormValues(formData: TutorialGroupFormData) {
        if (formData.teachingAssistant) {
            formData.teachingAssistant = this.createUserWithLabel(formData.teachingAssistant);
        }
        this.configureSchedule = !!formData.schedule;
        this.existingScheduleFormDate = formData.schedule;
        this.existingTitle = formData.title;
        this.additionalInformation = formData.additionalInformation;
        this.form.patchValue(formData);
    }

    private createUserWithLabel(user: User): UserWithLabel {
        return { ...user, label: this.createUserLabel(user) };
    }

    private createUserLabel(ta: User) {
        let label = '';
        if (ta.firstName) {
            label += ta.firstName + ' ';
        }
        if (ta.lastName) {
            label += ta.lastName + ' ';
        }
        if (ta.login) {
            label += '(' + ta.login + ')';
        }
        return label.trim();
    }

    private getTeachingAssistantsInCourse() {
        const generateUserObservable = (group: CourseGroup) => {
            return this.courseManagementService.getAllUsersInCourseGroup(this.course().id!, group).pipe(
                catchError((res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    return of([]);
                }),
                map((res: HttpResponse<User[]>) => res.body!),
            );
        };

        type result = {
            tutors: User[];
            instructors: User[];
            editors: User[];
        };

        return concat(
            of([]), // default items
            forkJoin({
                tutors: generateUserObservable(CourseGroup.TUTORS),
                instructors: generateUserObservable(CourseGroup.INSTRUCTORS),
                editors: generateUserObservable(CourseGroup.EDITORS),
            }).pipe(
                map((res: result) => {
                    return [...res.tutors, ...res.instructors, ...res.editors];
                }),
                finalize(() => {
                    this.teachingAssistantsAreLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            ),
        ).subscribe((users: User[]) => {
            this.teachingAssistants = users.map((user) => this.createUserWithLabel(user));
        });
    }

    private getUniqueCampusValuesOfCourse() {
        return concat(
            of([]), // default items
            this.tutorialGroupService.getUniqueCampusValues(this.course().id!).pipe(
                catchError((res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    return of([]);
                }),
                map((res: HttpResponse<string[]>) => res.body!),
                finalize(() => {
                    this.campusAreLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            ),
        ).subscribe((campus: string[]) => {
            this.campus = campus;
        });
    }

    private getUniqueLanguageValuesOfCourse() {
        return concat(
            of([]), // default items
            this.tutorialGroupService.getUniqueLanguageValues(this.course().id!).pipe(
                catchError((res: HttpErrorResponse) => {
                    onError(this.alertService, res);
                    return of([]);
                }),
                map((res: HttpResponse<string[]>) => res.body!),
                finalize(() => {
                    this.languagesAreLoading = false;
                }),
                takeUntil(this.ngUnsubscribe),
            ),
        ).subscribe((languages: string[]) => {
            this.languages = languages;
            // default values for English & German
            if (!languages.includes('English')) {
                this.languages.push('English');
            }
            if (!languages.includes('German')) {
                this.languages.push('German');
            }
        });
    }
}
