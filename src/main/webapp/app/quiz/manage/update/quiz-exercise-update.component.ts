import { ChangeDetectionStrategy, ChangeDetectorRef, Component, HostListener, OnChanges, OnInit, SimpleChanges, ViewEncapsulation, inject, viewChild } from '@angular/core';
import { ExerciseTitleChannelNameComponent } from 'app/exercise/exercise-title-channel-name/exercise-title-channel-name.component';
import { IncludedInOverallScorePickerComponent } from 'app/exercise/included-in-overall-score-picker/included-in-overall-score-picker.component';
import { QuizExerciseService } from '../service/quiz-exercise.service';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { QuizBatch, QuizExercise, QuizMode, resetQuizForImport } from 'app/quiz/shared/entities/quiz-exercise.model';
import { DragAndDropQuestionUtil } from 'app/quiz/shared/service/drag-and-drop-question-util.service';
import { ShortAnswerQuestionUtil } from 'app/quiz/shared/service/short-answer-question-util.service';
import { TranslateService } from '@ngx-translate/core';
import { Duration } from '../interfaces/quiz-exercise-interfaces';
import { NgbDate, NgbModal, NgbModalOptions, NgbModalRef, NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/shared/service/alert.service';
import { ComponentCanDeactivate } from 'app/shared/guard/can-deactivate.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { Exercise, IncludedInOverallScore, ValidationReason } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseGroupService } from 'app/exam/manage/exercise-groups/exercise-group.service';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { cloneDeep } from 'lodash-es';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared/components/buttons/documentation-button/documentation-button.component';

import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { round } from 'app/shared/util/utils';
import { onError } from 'app/shared/util/global.utils';
import { QuizExerciseValidationDirective } from 'app/quiz/manage/util/quiz-exercise-validation.directive';
import { faExclamationCircle, faPlus, faXmark } from '@fortawesome/free-solid-svg-icons';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { isQuizEditable } from 'app/quiz/shared/service/quiz-manage-util.service';
import { QuizQuestionListEditComponent } from 'app/quiz/manage/list-edit/quiz-question-list-edit.component';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { GenericConfirmationDialogComponent } from 'app/communication/course-conversations-components/generic-confirmation-dialog/generic-confirmation-dialog.component';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { CategorySelectorComponent } from 'app/shared/category-selector/category-selector.component';
import { FormDateTimePickerComponent } from 'app/shared/date-time-picker/date-time-picker.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JsonPipe, NgClass } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { DifficultyPickerComponent } from 'app/exercise/difficulty-picker/difficulty-picker.component';
import { CompetencySelectionComponent } from 'app/atlas/shared/competency-selection/competency-selection.component';
import { CalendarEventService } from 'app/core/calendar/shared/service/calendar-event.service';

@Component({
    selector: 'jhi-quiz-exercise-detail',
    templateUrl: './quiz-exercise-update.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    providers: [DragAndDropQuestionUtil, ShortAnswerQuestionUtil],
    styleUrls: ['./quiz-exercise-update.component.scss', '../../shared/quiz.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        TranslateDirective,
        DocumentationButtonComponent,
        FormsModule,
        ExerciseTitleChannelNameComponent,
        HelpIconComponent,
        CategorySelectorComponent,
        DifficultyPickerComponent,
        FormDateTimePickerComponent,
        ButtonComponent,
        IncludedInOverallScorePickerComponent,
        CompetencySelectionComponent,
        QuizQuestionListEditComponent,
        NgbTooltip,
        FaIconComponent,
        NgClass,
        JsonPipe,
        ArtemisTranslatePipe,
    ],
})
export class QuizExerciseUpdateComponent extends QuizExerciseValidationDirective implements OnInit, OnChanges, ComponentCanDeactivate {
    private route = inject(ActivatedRoute);
    private courseService = inject(CourseManagementService);
    private quizExerciseService = inject(QuizExerciseService);
    private router = inject(Router);
    private translateService = inject(TranslateService);
    private exerciseService = inject(ExerciseService);
    private alertService = inject(AlertService);
    private changeDetector = inject(ChangeDetectorRef);
    private exerciseGroupService = inject(ExerciseGroupService);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private modalService = inject(NgbModal);
    private calendarEventService = inject(CalendarEventService);

    readonly quizQuestionListEditComponent = viewChild.required<QuizQuestionListEditComponent>('quizQuestionsEdit');

    course?: Course;
    exerciseGroup?: ExerciseGroup;
    courseRepository: CourseManagementService;
    notificationText?: string;

    isImport = false;

    /** Constants for 'Add existing questions' and 'Import file' features **/
    showExistingQuestions = false;

    exams: Exam[] = [];

    courses: Course[] = [];
    quizExercises: QuizExercise[];
    allExistingQuestions: QuizQuestion[];
    existingQuestions: QuizQuestion[];
    importFile?: File;
    importFileName: string;
    searchQueryText: string;
    dndFilterEnabled: boolean;
    mcqFilterEnabled: boolean;
    shortAnswerFilterEnabled: boolean;

    /** Duration object **/
    duration = new Duration(0, 0);

    /** Status constants **/
    isSaving = false;
    scheduleQuizStart = false;

    exerciseCategories: ExerciseCategory[];
    existingCategories: ExerciseCategory[];

    /** Route params **/
    examId?: number;
    courseId?: number;

    // Icons
    faPlus = faPlus;
    faXmark = faXmark;
    faExclamationCircle = faExclamationCircle;

    readonly QuizMode = QuizMode;
    readonly documentationType: DocumentationType = 'Quiz';
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;

    readonly defaultSecondLayerDialogOptions: NgbModalOptions = {
        size: 'md',
        scrollable: false,
        backdrop: 'static',
        backdropClass: 'second-layer-modal-bg',
        centered: true,
    };

    /**
     * Initialize variables and load course and quiz from server.
     */
    ngOnInit(): void {
        /** Initialize local constants **/
        this.showExistingQuestions = false;
        this.quizExercises = [];
        this.allExistingQuestions = [];
        this.existingQuestions = [];
        this.importFile = undefined;
        this.importFileName = '';
        this.searchQueryText = '';
        this.dndFilterEnabled = true;
        this.mcqFilterEnabled = true;
        this.shortAnswerFilterEnabled = true;
        this.notificationText = undefined;

        this.courseId = Number(this.route.snapshot.paramMap.get('courseId'));
        this.examId = Number(this.route.snapshot.paramMap.get('examId'));
        const quizId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const groupId = Number(this.route.snapshot.paramMap.get('exerciseGroupId'));
        if (this.examId && groupId) {
            this.isExamMode = true;
        }

        if (this.router.url.includes('/import')) {
            this.isImport = true;
        }

        /** Query the courseService for the participationId given by the params */
        if (this.courseId) {
            this.courseService.find(this.courseId).subscribe((response: HttpResponse<Course>) => {
                this.course = response.body!;
                // Load exerciseGroup and set exam mode
                if (this.isExamMode) {
                    this.exerciseGroupService.find(this.courseId!, this.examId!, groupId).subscribe((groupResponse: HttpResponse<ExerciseGroup>) => {
                        // Make sure to call init if we didn't receive an id => new quiz-exercise
                        this.exerciseGroup = groupResponse.body || undefined;
                        if (!quizId) {
                            this.init();
                        } else if (this.quizExercise) {
                            this.quizExercise.exerciseGroup = this.exerciseGroup;
                            this.savedEntity.exerciseGroup = this.exerciseGroup;
                        }
                    });
                } else {
                    // Make sure to call init if we didn't receive an id => new quiz-exercise
                    if (!quizId) {
                        this.init();
                    } else if (this.quizExercise) {
                        this.quizExercise.course = this.course;
                        this.savedEntity.course = this.course;
                    }
                }
            });
        }
        if (quizId) {
            this.quizExerciseService.find(quizId).subscribe((response: HttpResponse<QuizExercise>) => {
                this.quizExercise = response.body!;
                this.init();
                if (!this.quizExercise.isEditable) {
                    this.alertService.error('error.http.403');
                }
                if (this.testRunExistsAndShouldNotBeIgnored()) {
                    this.alertService.warning(this.translateService.instant('artemisApp.quizExercise.edit.testRunSubmissionsExist'));
                }
            });
        }

        // TODO: we should try to avoid calling this.init() above more than once
        this.courseRepository = this.courseService;
    }

    /**
     * Initializes and returns a new quiz exercise
     */
    initializeNewQuizExercise(): QuizExercise {
        const newQuiz = new QuizExercise(undefined, undefined);
        newQuiz.title = '';
        newQuiz.duration = 600;
        newQuiz.isOpenForPractice = false;
        newQuiz.releaseDate = dayjs();
        newQuiz.randomizeQuestionOrder = true;
        newQuiz.quizQuestions = [];
        newQuiz.quizMode = QuizMode.SYNCHRONIZED;
        newQuiz.allowedNumberOfAttempts = 1;
        newQuiz.isEditable = true;
        this.prepareEntity(newQuiz);
        return newQuiz;
    }

    /**
     * Initializes local constants and prepares the QuizExercise entity
     */
    init(): void {
        if (!this.quizExercise) {
            this.quizExercise = this.initializeNewQuizExercise();
        } else {
            this.prepareEntity(this.quizExercise);
            this.quizExercise.isEditable = isQuizEditable(this.quizExercise);
        }

        if (this.isImport || this.isExamMode) {
            resetQuizForImport(this.quizExercise);
        }

        if (this.isExamMode) {
            this.quizExercise.course = undefined;
            if (!this.quizExercise.exerciseGroup || this.isImport) {
                this.quizExercise.exerciseGroup = this.exerciseGroup;
            }
        } else {
            this.quizExercise.exerciseGroup = undefined;
            if (!this.quizExercise.course || this.isImport) {
                this.quizExercise.course = this.course;
            }
        }

        if (!this.isExamMode) {
            this.exerciseCategories = this.quizExercise.categories || [];
            this.courseService.findAllCategoriesOfCourse(this.courseId!).subscribe({
                next: (response: HttpResponse<string[]>) => {
                    this.existingCategories = this.exerciseService.convertExerciseCategoriesAsStringFromServer(response.body!);
                },
                error: (error: HttpErrorResponse) => onError(this.alertService, error),
            });
        }
        // Exam exercises cannot be not included into the total score
        if (this.isExamMode && this.quizExercise.includedInOverallScore === IncludedInOverallScore.NOT_INCLUDED) {
            this.quizExercise.includedInOverallScore = IncludedInOverallScore.INCLUDED_COMPLETELY;
        }
        this.scheduleQuizStart = (this.quizExercise.quizBatches?.length ?? 0) > 0;
        this.updateDuration();
        this.exerciseService.validateDate(this.quizExercise);

        // Assign savedEntity to identify local changes
        this.savedEntity = this.quizExercise.id && !this.isImport ? cloneDeep(this.quizExercise) : new QuizExercise(undefined, undefined);

        this.cacheValidation();
    }

    /**
     * Validates if the date is correct
     */
    validateDate() {
        this.exerciseService.validateDate(this.quizExercise);
        const dueDate = this.quizExercise.quizMode === QuizMode.SYNCHRONIZED ? undefined : this.quizExercise.dueDate;
        this.quizExercise?.quizBatches?.forEach((batch) => {
            // validate release < start and start + duration > due
            const startTime = dayjs(batch.startTime);
            const endTime = startTime.add(dayjs.duration(this.duration.minutes, 'minutes')).add(dayjs.duration(this.duration.seconds, 'seconds'));
            batch.startTimeError = startTime.isBefore(this.quizExercise.releaseDate) || (dueDate != undefined && endTime.isAfter(dueDate));
        });
    }

    cacheValidation() {
        if (this.quizExercise.quizMode === QuizMode.SYNCHRONIZED) {
            this.quizExercise.dueDate = undefined; // Due date is calculated on server side
            if (this.scheduleQuizStart) {
                if ((this.quizExercise.quizBatches?.length ?? 0) !== 1) {
                    this.quizExercise.quizBatches = [this.quizExercise.quizBatches?.[0] ?? new QuizBatch()];
                }
            } else {
                if ((this.quizExercise.quizBatches?.length ?? 0) !== 0) {
                    this.quizExercise.quizBatches = [];
                }
            }
        }

        this.validateDate();
        return super.cacheValidation(this.changeDetector);
    }

    addQuizBatch() {
        if (!this.quizExercise.quizBatches) {
            this.quizExercise.quizBatches = [];
        }
        this.quizExercise.quizBatches.push(new QuizBatch());
    }

    removeQuizBatch(quizBatch: QuizBatch) {
        if (this.quizExercise.quizBatches) {
            const idx = this.quizExercise.quizBatches.indexOf(quizBatch);
            if (idx >= 0) {
                this.quizExercise.quizBatches.splice(idx, 1);
            }
        }
    }

    /**
     * Apply updates for changed course and quizExercise
     * @param changes the changes to apply
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.course || changes.quizExercise) {
            this.init();
        }
    }

    /**
     * Update the categories and overwrite the cache, overwrites existing categories
     * @param categories the new categories
     */
    updateCategories(categories: ExerciseCategory[]) {
        this.quizExercise.categories = categories;
        this.exerciseCategories = categories;
        this.cacheValidation();
    }

    /**
     * Determine which dropdown to display depending on the relationship between start time, end time, and current time
     * @returns {string} Name of the dropdown to show
     */
    get showDropdown(): string {
        if (!this.quizExercise || !this.quizExercise.quizStarted || this.isImport) {
            return 'isVisibleBeforeStart';
        } else if (this.quizExercise.quizEnded) {
            return 'isOpenForPractice';
        } else {
            return 'active';
        }
    }

    /**
     * Returns whether pending changes are present, preventing a deactivation.
     */
    canDeactivate(): boolean {
        return !this.pendingChangesCache;
    }

    /**
     * Displays the alert for confirming refreshing or closing the page if there are unsaved changes
     * NOTE: while the beforeunload event might be deprecated in the future, it is currently the only way to display a confirmation dialog when the user tries to leave the page
     * @param event the beforeunload event
     */
    @HostListener('window:beforeunload', ['$event'])
    unloadNotification(event: BeforeUnloadEvent) {
        if (!this.canDeactivate()) {
            event.preventDefault();
            return this.translateService.instant('pendingChanges');
        }
        return true;
    }

    /**
     * @desc Callback for datepicker to decide whether given date should be disabled
     * All dates which are in the past (< today) are disabled
     */
    isDateInPast = (date: NgbDate, current: { month: number }) =>
        current.month < dayjs().month() + 1 ||
        dayjs()
            .year(date.year)
            .month(date.month - 1)
            .date(date.day)
            .isBefore(dayjs());

    /**
     * Iterates over the questions of the quizExercise and calculates the sum of all question scores
     */
    calculateMaxExerciseScore(): number {
        let scoreSum = 0;
        this.quizExercise.quizQuestions!.forEach((question) => (scoreSum += question.points!));
        return scoreSum;
    }

    /**
     * Remove question from the quiz
     * @param questionToDelete {QuizQuestion} the question to remove
     */
    deleteQuestion(questionToDelete: QuizQuestion): void {
        this.quizExercise.quizQuestions = this.quizExercise.quizQuestions?.filter((question) => question !== questionToDelete);
        this.cacheValidation();
    }

    checkItemCountDragAndDrop(dragAndDropQuestions: DragAndDropQuestion[]): boolean {
        if (!dragAndDropQuestions) return false;
        return dragAndDropQuestions?.some((dragAndDropQuestion) => {
            const numberOfDropLocations = dragAndDropQuestion.dropLocations?.length ?? 1;
            const numberOfDragItems = dragAndDropQuestion.dragItems?.length ?? 1;
            const numberOfCorrectMappings = dragAndDropQuestion.correctMappings?.length ?? 1;
            // Magic number is 13 * 13 * 13
            return numberOfCorrectMappings * numberOfDragItems * numberOfDropLocations > 2197;
        });
    }
    checkItemCountShortAnswer(shortAnswerQuestions: ShortAnswerQuestion[]): boolean {
        if (!shortAnswerQuestions) return false;
        return shortAnswerQuestions?.some((shortAnswerQuestion) => {
            const numberOfCorrectMappings = shortAnswerQuestion.correctMappings?.length ?? 1;
            const numberOfSpots = shortAnswerQuestion.spots?.length ?? 1;
            const numberOfSolutions = shortAnswerQuestion.solutions?.length ?? 1;
            // Magic number is 13 * 13 * 13
            return numberOfCorrectMappings * numberOfSpots * numberOfSolutions > 2197;
        });
    }

    validateItemLimit() {
        const dragAndDropQuestions = this.quizExercise.quizQuestions?.filter((question) => {
            return question.type === this.DRAG_AND_DROP;
        });

        const shortAnswerQuestions = this.quizExercise.quizQuestions?.filter((question) => {
            return question.type === this.SHORT_ANSWER;
        });

        const dragAndDropItemsExceedLimit = this.checkItemCountDragAndDrop(dragAndDropQuestions as DragAndDropQuestion[]);
        const shortAnswerItemsExceedLimit = this.checkItemCountShortAnswer(shortAnswerQuestions as ShortAnswerQuestion[]);

        if (dragAndDropItemsExceedLimit || shortAnswerItemsExceedLimit) {
            const keys = {
                titleKey: 'artemisApp.quizWarning.title',
                questionKey: 'artemisApp.quizWarning.question',
                descriptionKey: 'artemisApp.quizWarning.description',
                confirmButtonKey: 'artemisApp.quizWarning.confirmButton',
            };
            const modalRef: NgbModalRef = this.modalService.open(GenericConfirmationDialogComponent, this.defaultSecondLayerDialogOptions);
            modalRef.componentInstance.translationKeys = keys;
            modalRef.componentInstance.canBeUndone = true;
            modalRef.componentInstance.initialize();
            modalRef.result.then(
                () => {
                    // On confirm
                    this.save();
                },
                () => {
                    // On cancel
                    return;
                },
            );
        } else {
            this.save();
        }
    }
    /**
     * Save the quiz to the server and invoke callback functions depending on result
     */
    save(): void {
        if (this.hasSavedQuizStarted || !this.pendingChangesCache || !this.quizIsValid) {
            return;
        }

        Exercise.sanitize(this.quizExercise);
        const filesMap = this.quizQuestionListEditComponent().fileMap;
        const files = new Map<string, Blob>();
        filesMap.forEach((value, key) => {
            files.set(key, value.file);
        });

        this.isSaving = true;
        this.quizQuestionListEditComponent().parseAllQuestions();
        if (this.quizExercise.id !== undefined) {
            if (this.isImport) {
                this.quizExerciseService.import(this.quizExercise, files).subscribe({
                    next: (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                        if (quizExerciseResponse.body) {
                            this.onSaveSuccess(quizExerciseResponse.body, true);
                        } else {
                            this.onSaveError();
                        }
                    },
                    error: (error) => this.onSaveError(error),
                });
            } else {
                const requestOptions = {} as any;
                if (this.notificationText) {
                    requestOptions.notificationText = this.notificationText;
                }
                this.quizExerciseService.update(this.quizExercise.id, this.quizExercise, files, requestOptions).subscribe({
                    next: (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                        this.notificationText = undefined;
                        if (quizExerciseResponse.body) {
                            this.onSaveSuccess(quizExerciseResponse.body, false);
                        } else {
                            this.onSaveError();
                        }
                    },
                    error: (error) => this.onSaveError(error),
                });
            }
        } else {
            this.quizExerciseService.create(this.quizExercise, files).subscribe({
                next: (quizExerciseResponse: HttpResponse<QuizExercise>) => {
                    if (quizExerciseResponse.body) {
                        this.onSaveSuccess(quizExerciseResponse.body, true);
                    } else {
                        this.onSaveError();
                    }
                },
                error: (error) => this.onSaveError(error),
            });
        }
    }

    /**
     * Callback function for when the save succeeds
     * Terminates the saving process and assign the returned quizExercise to the local entities
     * @param quizExercise Saved quizExercise entity
     * @param isCreate Flag if the quizExercise was created or updated
     */
    private onSaveSuccess(quizExercise: QuizExercise, isCreate: boolean): void {
        this.isSaving = false;
        this.pendingChangesCache = false;
        this.prepareEntity(quizExercise);
        this.quizQuestionListEditComponent().fileMap.clear();
        this.quizExercise = quizExercise;
        this.quizExercise.isEditable = isQuizEditable(this.quizExercise);
        this.exerciseService.validateDate(this.quizExercise);
        this.savedEntity = cloneDeep(this.quizExercise);
        this.changeDetector.detectChanges();

        // Navigate back only if it's an import
        // If we edit the exercise, a user might just want to save the current state of the added quiz questions without going back
        if (this.isImport) {
            this.previousState();
        } else if (isCreate) {
            this.router.navigate(['..', quizExercise.id, 'edit'], { relativeTo: this.route, skipLocationChange: true });
        }
        this.calendarEventService.refresh();
    }

    /**
     * Callback function for when the save fails
     */
    private onSaveError = (errorRes?: HttpErrorResponse): void => {
        if (errorRes?.error && errorRes.error.title) {
            this.alertService.addErrorAlert(errorRes.error.title, errorRes.error.message, errorRes.error.params);
        }
        this.alertService.error('artemisApp.quizExercise.saveError');
        this.isSaving = false;
        this.changeDetector.detectChanges();
    };

    /**
     * Makes sure the entity is well-formed and its fields are of the correct types
     * @param quizExercise {QuizExercise} exercise which will be prepared
     */
    prepareEntity(quizExercise: QuizExercise): void {
        if (!this.isExamMode) {
            quizExercise.releaseDate = quizExercise.releaseDate ? dayjs(quizExercise.releaseDate) : dayjs();
            quizExercise.duration = Number(quizExercise.duration);
            quizExercise.duration = isNaN(quizExercise.duration) ? 10 : quizExercise.duration;
        }
        for (const question of quizExercise.quizQuestions ?? []) {
            if (question.type === QuizQuestionType.SHORT_ANSWER) {
                this.shortAnswerQuestionUtil.prepareShortAnswerQuestion(question as ShortAnswerQuestion);
            }
        }
    }

    /**
     * Reach to changes of duration inputs by updating model and ui
     */
    onDurationChange(): void {
        if (!this.isExamMode) {
            const duration = dayjs.duration(this.duration.minutes, 'minutes').add(this.duration.seconds, 'seconds');
            this.quizExercise.duration = Math.min(Math.max(duration.asSeconds(), 0), 10 * 60 * 60);
            this.updateDuration();
            this.cacheValidation();
        } else if (this.quizExercise.releaseDate && this.quizExercise.dueDate) {
            const duration = dayjs(this.quizExercise.dueDate).diff(this.quizExercise.releaseDate, 's');
            this.quizExercise.duration = round(duration);
            this.updateDuration();
            this.cacheValidation();
        }
    }

    /**
     * Update ui to current value of duration
     */
    updateDuration(): void {
        const duration = dayjs.duration(this.quizExercise.duration!, 'seconds');
        this.changeDetector.detectChanges();
        // when input fields are empty do not update their values
        if (this.duration.minutes !== undefined) {
            this.duration.minutes = 60 * duration.hours() + duration.minutes();
        }
        if (this.duration.seconds !== undefined) {
            this.duration.seconds = duration.seconds();
        }
    }

    /**
     * Navigate back to the overview
     */
    previousState(): void {
        this.navigationUtilService.navigateBackFromExerciseUpdate(this.quizExercise);
    }

    /**
     * Check if the saved quiz has started
     * @return {boolean} true if the saved quiz has started, otherwise false
     */
    get hasSavedQuizStarted(): boolean {
        return !!(this.savedEntity && this.savedEntity.quizBatches && this.savedEntity.quizBatches.some((batch) => dayjs(batch.startTime).isBefore(dayjs())));
    }

    includedInOverallScoreChange(includedInOverallScore: IncludedInOverallScore) {
        this.quizExercise.includedInOverallScore = includedInOverallScore;
        this.cacheValidation();
    }

    computeInvalidReasons(): ValidationReason[] {
        const invalidReasons = new Array<ValidationReason>();
        if (!this.quizExercise) {
            return [];
        }
        // TODO: quiz cleanup: properly validate dates and deduplicate the checks (see isValidQuiz)
        return super.computeInvalidReasons().concat(invalidReasons);
    }

    isSaveDisabled(): boolean {
        return this.isSaving || !this.pendingChangesCache || !this.quizIsValid || this.hasSavedQuizStarted || this.quizExercise.dueDateError || this.hasErrorInQuizBatches();
    }

    hasErrorInQuizBatches(): boolean {
        return !!this.quizExercise?.quizBatches?.some((batch) => batch.startTimeError);
    }

    handleQuestionChanged() {
        this.cacheValidation();
    }
}
