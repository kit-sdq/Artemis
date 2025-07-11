import { Component, OnDestroy, OnInit, inject, viewChildren } from '@angular/core';
import dayjs from 'dayjs/esm';
import isMobile from 'ismobilejs-es5';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { Subscription, combineLatest, of, take } from 'rxjs';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService, AlertType } from 'app/shared/service/alert.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { MultipleChoiceQuestionComponent } from 'app/quiz/shared/questions/multiple-choice-question/multiple-choice-question.component';
import { DragAndDropQuestionComponent } from 'app/quiz/shared/questions/drag-and-drop-question/drag-and-drop-question.component';
import { ShortAnswerQuestionComponent } from 'app/quiz/shared/questions/short-answer-question/short-answer-question.component';
import { TranslateService } from '@ngx-translate/core';
import * as smoothscroll from 'smoothscroll-polyfill';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ButtonComponent, ButtonSize, ButtonType } from 'app/shared/components/buttons/button/button.component';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { ShortAnswerSubmittedAnswer } from 'app/quiz/shared/entities/short-answer-submitted-answer.model';
import { QuizExerciseService } from 'app/quiz/manage/service/quiz-exercise.service';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { ShortAnswerSubmittedText } from 'app/quiz/shared/entities/short-answer-submitted-text.model';
import { QuizParticipationService } from 'app/quiz/overview/service/quiz-participation.service';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizBatch, QuizExercise, QuizMode } from 'app/quiz/shared/entities/quiz-exercise.model';
import { DragAndDropSubmittedAnswer } from 'app/quiz/shared/entities/drag-and-drop-submitted-answer.model';
import { QuizSubmission } from 'app/quiz/shared/entities/quiz-submission.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { MultipleChoiceSubmittedAnswer } from 'app/quiz/shared/entities/multiple-choice-submitted-answer.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { roundValueSpecifiedByCourseSettings } from 'app/shared/util/utils';
import { onError } from 'app/shared/util/global.utils';
import { AUTOSAVE_CHECK_INTERVAL, AUTOSAVE_EXERCISE_INTERVAL, UI_RELOAD_TIME } from 'app/shared/constants/exercise-exam-constants';
import { debounce } from 'lodash-es';
import { captureException } from '@sentry/angular';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faCircleNotch, faSync } from '@fortawesome/free-solid-svg-icons';
import { ArtemisServerDateService } from 'app/shared/service/server-date.service';
import { NgClass, NgTemplateOutlet } from '@angular/common';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { JhiConnectionStatusComponent } from 'app/shared/connection-status/connection-status.component';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ArtemisQuizService } from 'app/quiz/shared/service/quiz.service';

@Component({
    selector: 'jhi-quiz',
    templateUrl: './quiz-participation.component.html',
    providers: [ParticipationService],
    styleUrls: ['./quiz-participation.component.scss'],
    imports: [
        NgClass,
        NgTemplateOutlet,
        TranslateDirective,
        ButtonComponent,
        NgbTooltip,
        MultipleChoiceQuestionComponent,
        DragAndDropQuestionComponent,
        ShortAnswerQuestionComponent,
        JhiConnectionStatusComponent,
        FormsModule,
        FaIconComponent,
        ArtemisDatePipe,
        ArtemisTranslatePipe,
    ],
})
export class QuizParticipationComponent implements OnInit, OnDestroy {
    private websocketService = inject(WebsocketService);
    private quizExerciseService = inject(QuizExerciseService);
    private participationService = inject(ParticipationService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private alertService = inject(AlertService);
    private quizParticipationService = inject(QuizParticipationService);
    private translateService = inject(TranslateService);
    private quizService = inject(ArtemisQuizService);
    private serverDateService = inject(ArtemisServerDateService);

    // make constants available to html for comparison
    readonly DRAG_AND_DROP = QuizQuestionType.DRAG_AND_DROP;
    readonly MULTIPLE_CHOICE = QuizQuestionType.MULTIPLE_CHOICE;
    readonly SHORT_ANSWER = QuizQuestionType.SHORT_ANSWER;
    readonly ButtonSize = ButtonSize;
    readonly ButtonType = ButtonType;
    readonly QuizMode = QuizMode;
    readonly roundScoreSpecifiedByCourseSettings = roundValueSpecifiedByCourseSettings;
    readonly getCourseFromExercise = getCourseFromExercise;

    readonly mcQuestionComponents = viewChildren(MultipleChoiceQuestionComponent);

    readonly dndQuestionComponents = viewChildren(DragAndDropQuestionComponent);

    readonly shortAnswerQuestionComponents = viewChildren(ShortAnswerQuestionComponent);

    private routeAndDataSubscription: Subscription;

    runningTimeouts = new Array<any>(); // actually the function type setTimeout(): (handler: any, timeout?: any, ...args: any[]): number

    isSubmitting = false;
    // isSaving = false;
    lastSavedTimeText = '';
    justSaved = false;
    waitingForQuizStart = false;
    refreshingQuiz = false;

    remainingTimeText = '?';
    remainingTimeSeconds = 0;
    timeUntilStart = '0';
    disconnected = false;
    unsavedChanges = false;

    showingResult = false;
    userScore: number;

    mode: string;
    submission = new QuizSubmission();
    quizExercise: QuizExercise;
    quizBatch?: QuizBatch;
    totalScore: number;
    selectedAnswerOptions = new Map<number, AnswerOption[]>();
    dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
    shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();
    result: Result;
    questionScores: { [id: number]: number } = {};
    quizId: number;
    courseId: number;
    interval?: number;
    autoSaveInterval?: number;
    autoSaveTimer = 0;
    quizStarted = false;
    startDate: dayjs.Dayjs | undefined;
    endDate: dayjs.Dayjs | undefined;
    password = '';
    previousRunning = false;
    isMobile = false;
    isManagementView = false;

    /**
     * Websocket channels
     */
    participationChannel: string;
    quizExerciseChannel: string;
    quizBatchChannel: string;
    websocketSubscription?: Subscription;

    /**
     * debounced function to reset 'justSubmitted', so that time since last submission is displayed again when no submission has been made for at least 2 seconds
     */
    timeoutJustSaved = debounce(() => {
        this.justSaved = false;
    }, 2000);

    // Icons
    faSync = faSync;
    faCircleNotch = faCircleNotch;

    constructor() {
        smoothscroll.polyfill();
    }

    ngOnInit() {
        this.isMobile = isMobile(window.navigator.userAgent).any;
        // set correct mode
        this.routeAndDataSubscription = combineLatest([this.route.data, this.route.params, this.route.parent?.parent?.params ?? of({ courseId: undefined })]).subscribe(
            ([data, params, parentParams]) => {
                this.mode = data.mode;
                this.quizId = Number(params['exerciseId']);
                this.courseId = Number(parentParams['courseId']);
                // init according to mode
                switch (this.mode) {
                    case 'practice':
                        this.initPracticeMode();
                        break;
                    case 'preview':
                        this.initPreview();
                        break;
                    case 'solution':
                        this.initShowSolution();
                        break;
                    case 'live':
                        this.initLiveMode();
                        break;
                }
            },
        );
        // update displayed times in UI regularly
        this.interval = window.setInterval(() => {
            this.updateDisplayedTimes();
            this.checkForQuizEnd();
        }, UI_RELOAD_TIME);
        this.isManagementView = this.router.url.startsWith('/course-management');
    }

    ngOnDestroy() {
        window.clearInterval(this.interval);
        window.clearInterval(this.autoSaveInterval);
        /**
         * unsubscribe from all subscribed websocket channels when page is closed
         */
        this.runningTimeouts.forEach((timeout) => {
            clearTimeout(timeout);
        });

        if (this.participationChannel) {
            this.websocketService.unsubscribe(this.participationChannel);
        }
        if (this.quizExerciseChannel) {
            this.websocketService.unsubscribe(this.quizExerciseChannel);
        }
        this.websocketSubscription?.unsubscribe();
        this.routeAndDataSubscription?.unsubscribe();
    }

    /**
     * loads latest submission from server and sets up socket connection
     */
    initLiveMode() {
        // listen to connect / disconnect events
        this.websocketSubscription = this.websocketService.connectionState.subscribe((status) => {
            if (status.connected && this.disconnected) {
                // if the disconnect happened during the live quiz and there are unsaved changes, we save the submission on the server
                this.triggerSave(false);
                // if the quiz was not yet started, we might have missed the quiz start => refresh
                if (this.quizBatch && !this.quizBatch.started) {
                    this.refreshQuiz(true);
                } else if (this.quizBatch && this.endDate && this.endDate.isBefore(this.serverDateService.now())) {
                    // if the quiz has ended, we might have missed to load the results => refresh
                    this.refreshQuiz(true);
                }
            }
            this.disconnected = !status.connected;
        });

        this.subscribeToWebsocketChannels();
        this.setupAutoSave();

        // load the quiz (and existing submission if quiz has started)
        this.participationService.startQuizParticipation(this.quizId).subscribe({
            next: (response: HttpResponse<StudentParticipation>) => {
                this.updateParticipationFromServer(response.body!);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * loads quizExercise and starts practice mode
     */
    initPracticeMode() {
        this.quizExerciseService.findForStudent(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                if (res.body && res.body.isOpenForPractice) {
                    this.startQuizPreviewOrPractice(res.body);
                } else {
                    alert('Error: This quiz is not open for practice!');
                }
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * loads quiz exercise and starts preview mode
     */
    initPreview() {
        this.quizExerciseService.find(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.startQuizPreviewOrPractice(res.body!);
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    initShowSolution() {
        this.quizExerciseService.find(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                this.quizExercise = res.body!;
                this.initQuiz();
                this.showingResult = true;
            },
            error: (error: HttpErrorResponse) => onError(this.alertService, error),
        });
    }

    /**
     * Start the given quiz in practice or preview mode
     *
     * @param quizExercise {object} the quizExercise to start
     */
    startQuizPreviewOrPractice(quizExercise: QuizExercise) {
        // init quiz
        this.quizExercise = quizExercise;
        this.initQuiz();

        // randomize order
        this.quizService.randomizeOrder(this.quizExercise.quizQuestions, this.quizExercise.randomizeQuestionOrder);

        // init empty submission
        this.submission = new QuizSubmission();

        // adjust end date
        this.endDate = dayjs().add(this.quizExercise.duration!, 'seconds');

        // auto submit when time is up
        this.runningTimeouts.push(
            setTimeout(() => {
                this.onSubmit();
            }, quizExercise.duration! * 1000),
        );
    }

    setupAutoSave(): void {
        // Clear existing autosaves - only one may run at a time
        this.stopAutoSave();
        this.autoSaveInterval = window.setInterval(() => {
            if (this.waitingForQuizStart) {
                // The quiz has not started. No need to autosave yet.
                return;
            }
            if (this.remainingTimeSeconds < 0 || this.submission.submitted) {
                this.stopAutoSave();
                return;
            }
            this.autoSaveTimer++;
            if (this.autoSaveTimer >= AUTOSAVE_EXERCISE_INTERVAL) {
                this.triggerSave();
            }
        }, AUTOSAVE_CHECK_INTERVAL);
    }

    stopAutoSave(): void {
        window.clearInterval(this.autoSaveInterval);
        this.autoSaveInterval = undefined;
    }

    /**
     * subscribe to any outstanding websocket channels
     */
    subscribeToWebsocketChannels() {
        if (!this.participationChannel) {
            this.participationChannel = '/user/topic/exercise/' + this.quizId + '/participation';
            // TODO: subscribe for new results instead if this is what we are actually interested in
            // participation channel => react to new results
            this.websocketService.subscribe(this.participationChannel);
            this.websocketService.receive(this.participationChannel).subscribe((changedParticipation: StudentParticipation) => {
                if (changedParticipation && this.quizExercise && changedParticipation.exercise!.id === this.quizExercise.id) {
                    if (this.waitingForQuizStart) {
                        // only apply completely if quiz hasn't started to prevent jumping ui during participation
                        this.updateParticipationFromServer(changedParticipation);
                    } else {
                        // update quizExercise and results / submission
                        this.showQuizResultAfterQuizEnd(changedParticipation);
                    }
                }
            });
        }

        if (!this.quizExerciseChannel) {
            this.quizExerciseChannel = '/topic/courses/' + this.courseId + '/quizExercises';
            // quizExercise channel => react to changes made to quizExercise (e.g. start date)
            this.websocketService.subscribe(this.quizExerciseChannel);
            this.websocketService.receive(this.quizExerciseChannel).subscribe((quiz) => {
                if (this.waitingForQuizStart && this.quizId === quiz.id) {
                    this.applyQuizFull(quiz);
                }
            });
        }

        if (this.quizBatch && !this.quizBatch.started) {
            const batchChannel = this.quizExerciseChannel + '/' + this.quizBatch.id;
            if (this.quizBatchChannel !== batchChannel) {
                this.quizBatchChannel = batchChannel;
                this.websocketService.subscribe(this.quizBatchChannel);
                this.websocketService.receive(this.quizBatchChannel).subscribe((quiz) => {
                    this.applyQuizFull(quiz);
                });
            }
        }
    }

    /**
     * updates all displayed (relative) times in the UI
     */
    updateDisplayedTimes() {
        const translationBasePath = 'artemisApp.showStatistic.';
        // update remaining time
        if (this.endDate) {
            const endDate = this.endDate;
            if (endDate.isAfter(this.serverDateService.now())) {
                // quiz is still running => calculate remaining seconds and generate text based on that
                // Get the diff as a floating point number in seconds
                this.remainingTimeSeconds = endDate.diff(this.serverDateService.now(), 'seconds');
                this.remainingTimeText = this.relativeTimeText(this.remainingTimeSeconds);
            } else {
                // quiz is over => set remaining seconds to negative, to deactivate 'Submit' button
                this.remainingTimeSeconds = -1;
                this.remainingTimeText = this.translateService.instant(translationBasePath + 'quizHasEnded');
            }
        } else {
            // remaining time is unknown => Set remaining seconds to 0, to keep 'Submit' button enabled
            this.remainingTimeSeconds = 0;
            this.remainingTimeText = '?';
        }

        // update submission time
        if (this.submission && this.submission.submissionDate) {
            // exact value is not important => use default relative time from dayjs for better readability and less distraction
            this.lastSavedTimeText = dayjs(this.submission.submissionDate).fromNow();
        }

        // update time until start
        if (this.quizBatch && this.startDate) {
            if (this.startDate.isAfter(this.serverDateService.now())) {
                this.timeUntilStart = this.relativeTimeText(this.startDate.diff(this.serverDateService.now(), 'seconds'));
            } else {
                this.timeUntilStart = this.translateService.instant(translationBasePath + 'now');
                // Check if websocket has updated the quiz exercise and check that following block is only executed once
                if (!this.quizBatch.started && !this.quizStarted) {
                    this.quizStarted = true;
                    if (this.quizExercise.quizMode === QuizMode.INDIVIDUAL) {
                        // there is not websocket notification for INDIVIDUAL mode so just load the quiz
                        this.refreshQuiz(true);
                    } else {
                        // Refresh quiz after 5 seconds when client did not receive websocket message to start the quiz
                        setTimeout(() => {
                            // Check again if websocket has updated the quiz exercise within the 5 seconds
                            if (!this.quizBatch || !this.quizBatch.started) {
                                this.refreshQuiz(true);
                            }
                        }, 5000);
                    }
                }
            }
        } else {
            this.timeUntilStart = '';
        }
    }

    /**
     * Express the given timespan as humanized text
     *
     * @param remainingTimeSeconds the amount of seconds to display
     * @return humanized text for the given amount of seconds
     */
    relativeTimeText(remainingTimeSeconds: number) {
        if (remainingTimeSeconds > 210) {
            return Math.ceil(remainingTimeSeconds / 60) + ' min';
        } else if (remainingTimeSeconds > 59) {
            return Math.floor(remainingTimeSeconds / 60) + ' min ' + (remainingTimeSeconds % 60) + ' s';
        } else {
            return remainingTimeSeconds + ' s';
        }
    }

    checkForQuizEnd() {
        const running = this.mode === 'live' && !!this.quizBatch && this.remainingTimeSeconds >= 0;
        if (!running && this.previousRunning) {
            // Rely on the grace period to store any unsaved changes at the end of the quiz
            if (!this.submission.submitted) {
                this.stopAutoSave();
                this.triggerSave();
            }
        }
        this.previousRunning = running;
    }

    /**
     * Initialize the selections / mappings for each question with an empty array
     */
    initQuiz() {
        // calculate score
        this.totalScore = this.quizExercise.quizQuestions
            ? this.quizExercise.quizQuestions.reduce((score, question) => {
                  return score + question.points!;
              }, 0)
            : 0;

        // prepare selection arrays for each question
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.quizExercise.quizQuestions) {
            this.quizExercise.quizQuestions.forEach((question) => {
                switch (question.type) {
                    case QuizQuestionType.MULTIPLE_CHOICE:
                        // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.selectedAnswerOptions.set(question.id!, []);
                        break;
                    case QuizQuestionType.DRAG_AND_DROP:
                        // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.dragAndDropMappings.set(question.id!, []);
                        break;
                    case QuizQuestionType.SHORT_ANSWER:
                        // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.shortAnswerSubmittedTexts.set(question.id!, []);
                        break;
                    default:
                        captureException('Unknown question type: ' + question);
                        break;
                }
            }, this);
        }
    }

    /**
     * applies the data from the model to the UI (reverse of applySelection):
     *
     * Sets the checkmarks (selected answers) for all questions according to the submission data
     * this needs to be done when we get new submission data, e.g. through the websocket connection
     */
    applySubmission() {
        // create dictionaries (key: questionID, value: Array of selected answerOptions / mappings)
        // for the submittedAnswers to hand the selected options / mappings in individual arrays to the question components
        this.selectedAnswerOptions = new Map<number, AnswerOption[]>();
        this.dragAndDropMappings = new Map<number, DragAndDropMapping[]>();
        this.shortAnswerSubmittedTexts = new Map<number, ShortAnswerSubmittedText[]>();

        if (this.quizExercise.quizQuestions) {
            // iterate through all questions of this quiz
            this.quizExercise.quizQuestions.forEach((question) => {
                // find the submitted answer that belongs to this question, only when submitted answers already exist
                const submittedAnswer = this.submission.submittedAnswers?.find((answer) => {
                    return answer.quizQuestion!.id === question.id;
                });

                switch (question.type) {
                    case QuizQuestionType.MULTIPLE_CHOICE:
                        // add the array of selected options to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.selectedAnswerOptions.set(question.id!, (submittedAnswer as MultipleChoiceSubmittedAnswer)?.selectedOptions || []);
                        break;
                    case QuizQuestionType.DRAG_AND_DROP:
                        // add the array of mappings to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.dragAndDropMappings.set(question.id!, (submittedAnswer as DragAndDropSubmittedAnswer)?.mappings || []);
                        break;
                    case QuizQuestionType.SHORT_ANSWER:
                        // add the array of submitted texts to the dictionary (add an empty array, if there is no submittedAnswer for this question)
                        this.shortAnswerSubmittedTexts.set(question.id!, (submittedAnswer as ShortAnswerSubmittedAnswer)?.submittedTexts || []);
                        break;
                    default:
                        captureException('Unknown question type: ' + question);
                        break;
                }
            }, this);
        }
    }

    /**
     * updates the model according to UI state (reverse of applySubmission):
     * Creates the submission from the user's selection
     * this needs to be done when we want to send the submission either for saving (through websocket) or for submitting (through REST call)
     */
    applySelection() {
        // convert the selection dictionary (key: questionID, value: Array of selected answerOptions / mappings)
        // into an array of submittedAnswer objects and save it as the submittedAnswers of the submission
        this.submission.submittedAnswers = [];

        // for multiple-choice questions
        this.selectedAnswerOptions.forEach((answerOptions, questionId) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions?.find((selectedQuestion) => {
                return selectedQuestion.id === questionId;
            });
            if (!question) {
                captureException('question not found for ID: ' + questionId);
                return;
            }
            // generate the submittedAnswer object
            const mcSubmittedAnswer = new MultipleChoiceSubmittedAnswer();
            mcSubmittedAnswer.quizQuestion = question;
            mcSubmittedAnswer.selectedOptions = answerOptions;
            this.submission.submittedAnswers!.push(mcSubmittedAnswer);
        }, this);

        // for drag-and-drop questions
        this.dragAndDropMappings.forEach((mappings, questionId) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions?.find((localQuestion) => {
                return localQuestion.id === questionId;
            });
            if (!question) {
                captureException('question not found for ID: ' + questionId);
                return;
            }
            // generate the submittedAnswer object
            const dndSubmittedAnswer = new DragAndDropSubmittedAnswer();
            dndSubmittedAnswer.quizQuestion = question;
            dndSubmittedAnswer.mappings = mappings;
            this.submission.submittedAnswers!.push(dndSubmittedAnswer);
        }, this);
        // for short-answer questions
        this.shortAnswerSubmittedTexts.forEach((submittedTexts, questionId) => {
            // find the question object for the given question id
            const question = this.quizExercise.quizQuestions?.find((localQuestion) => {
                return localQuestion.id === questionId;
            });
            if (!question) {
                captureException('question not found for ID: ' + questionId);
                return;
            }
            // generate the submittedAnswer object
            const shortAnswerSubmittedAnswer = new ShortAnswerSubmittedAnswer();
            shortAnswerSubmittedAnswer.quizQuestion = question;
            shortAnswerSubmittedAnswer.submittedTexts = submittedTexts;
            this.submission.submittedAnswers!.push(shortAnswerSubmittedAnswer);
        }, this);
    }

    /**
     * Apply the data of the participation, replacing all old data
     */
    updateParticipationFromServer(participation: StudentParticipation) {
        if (participation) {
            this.applyQuizFull(participation.exercise as QuizExercise);
        }

        // apply submission if it exists
        if (participation?.submissions?.length) {
            this.submission = participation.submissions.first() as QuizSubmission;

            // update submission time
            this.updateSubmissionTime();

            // show submission answers in UI
            this.applySubmission();

            if (this.submission.results?.length && this.submission.results[0].score !== undefined && this.quizExercise.quizEnded) {
                // quiz has ended and results are available
                this.showResult(this.submission.results[0]);
            }
        } else {
            this.submission = new QuizSubmission();
            this.initQuiz();
        }
    }

    /**
     * apply the data of the quiz, replacing all old data and enabling reconnect if necessary
     * @param quizExercise
     */
    applyQuizFull(quizExercise: QuizExercise) {
        this.quizExercise = quizExercise;
        this.initQuiz();

        this.quizBatch = this.quizExercise.quizBatches?.[0];
        if (this.quizExercise.quizEnded) {
            // quiz is done
            this.waitingForQuizStart = false;
        } else if (!this.quizBatch || !this.quizBatch.started) {
            // quiz hasn't started yet
            this.waitingForQuizStart = true;

            // enable automatic websocket reconnect
            this.websocketService.enableReconnect();

            if (this.quizBatch && this.quizBatch.startTime) {
                // synchronize time with server
                this.startDate = dayjs(this.quizBatch.startTime ?? this.serverDateService.now());
            }
        } else {
            // quiz has started
            this.waitingForQuizStart = false;

            // update timeDifference
            this.startDate = dayjs(this.quizBatch.startTime ?? this.serverDateService.now());
            this.endDate = this.startDate.add(this.quizExercise.duration!, 'seconds');

            // check if quiz hasn't ended
            if (!this.quizBatch.ended) {
                // enable automatic websocket reconnect
                this.websocketService.enableReconnect();

                // apply randomized order where necessary
                this.quizService.randomizeOrder(this.quizExercise.quizQuestions, this.quizExercise.randomizeQuestionOrder);
            }
        }
    }

    /*
     * This method only handles the update of the quiz after the quiz has ended
     */
    showQuizResultAfterQuizEnd(participation: StudentParticipation) {
        const quizExercise = participation.exercise as QuizExercise;
        if (participation.submissions?.first() !== undefined && quizExercise.quizEnded) {
            // quiz has ended and results are available
            this.submission = participation.submissions.first() as QuizSubmission;
            if (this.submission.results?.length) {
                // update submission time
                this.updateSubmissionTime();
                this.transferInformationToQuizExercise(quizExercise);
                this.applySubmission();
                this.showResult(this.submission.results[0]);
            }
        }
    }

    /**
     * Transfer additional information (explanations, correct answers) from the given full quiz exercise to quizExercise.
     * This method is typically invoked after the quiz has ended and makes sure that the (random) order of the quiz
     * questions and answer options for the particular user is respected
     *
     * @param fullQuizExerciseFromServer {object} the quizExercise containing additional information
     */
    transferInformationToQuizExercise(fullQuizExerciseFromServer: QuizExercise) {
        this.quizExercise.quizQuestions!.forEach((clientQuestion) => {
            // find updated question
            const fullQuestionFromServer = fullQuizExerciseFromServer.quizQuestions?.find((fullQuestion) => {
                return clientQuestion.id === fullQuestion.id;
            });
            if (fullQuestionFromServer) {
                clientQuestion.explanation = fullQuestionFromServer.explanation;

                switch (clientQuestion.type) {
                    case QuizQuestionType.MULTIPLE_CHOICE:
                        const mcClientQuestion = clientQuestion as MultipleChoiceQuestion;
                        const mcFullQuestionFromServer = fullQuestionFromServer as MultipleChoiceQuestion;

                        const answerOptions = mcClientQuestion.answerOptions!;
                        answerOptions.forEach((clientAnswerOption) => {
                            // find updated answerOption
                            const fullAnswerOptionFromServer = mcFullQuestionFromServer.answerOptions!.find((option) => {
                                return clientAnswerOption.id === option.id;
                            });
                            if (fullAnswerOptionFromServer) {
                                clientAnswerOption.explanation = fullAnswerOptionFromServer.explanation;
                                clientAnswerOption.isCorrect = fullAnswerOptionFromServer.isCorrect;
                            }
                        });
                        break;
                    case QuizQuestionType.DRAG_AND_DROP:
                        const dndClientQuestion = clientQuestion as DragAndDropQuestion;
                        const dndFullQuestionFromServer = fullQuestionFromServer as DragAndDropQuestion;
                        dndClientQuestion.correctMappings = dndFullQuestionFromServer.correctMappings;
                        break;
                    case QuizQuestionType.SHORT_ANSWER:
                        const shortAnswerClientQuestion = clientQuestion as ShortAnswerQuestion;
                        const shortAnswerFullQuestionFromServer = fullQuestionFromServer as ShortAnswerQuestion;
                        shortAnswerClientQuestion.correctMappings = shortAnswerFullQuestionFromServer.correctMappings;
                        break;
                    default:
                        captureException(new Error('Unknown question type: ' + clientQuestion));
                        break;
                }
            }
        }, this);

        // make sure that a possible explanation is updated correctly in all sub components
        this.mcQuestionComponents().forEach((mcQuestionComponent) => {
            mcQuestionComponent.watchCollection();
        });
        this.dndQuestionComponents().forEach((dndQuestionComponent) => {
            dndQuestionComponent.watchCollection();
        });
        this.shortAnswerQuestionComponents().forEach((shortAnswerQuestionComponent) => {
            shortAnswerQuestionComponent.watchCollection();
        });
    }

    /**
     * Display results of the quiz for the user
     * @param result
     */
    showResult(result: Result) {
        this.result = result;
        if (this.result) {
            this.showingResult = true;
            const course = this.quizExercise.course || this.quizExercise?.exerciseGroup?.exam?.course;

            // assign user score (limit decimal places to 2)
            this.userScore = this.submission.scoreInPoints ? roundValueSpecifiedByCourseSettings(this.submission.scoreInPoints, course) : 0;

            // create dictionary with scores for each question
            this.questionScores = {};
            this.submission.submittedAnswers?.forEach((submittedAnswer) => {
                // limit decimal places
                this.questionScores[submittedAnswer.quizQuestion!.id!] = roundValueSpecifiedByCourseSettings(submittedAnswer.scoreInPoints!, course);
            }, this);
        }
    }

    /**
     * Callback method to be triggered when the user changes any of the answers in the quiz (in sub components based on the question type)
     */
    onSelectionChanged() {
        this.unsavedChanges = true;
    }

    triggerSave(resetAutoSaveTimer = true): void {
        if (resetAutoSaveTimer) {
            this.autoSaveTimer = 0;
        }
        if (this.unsavedChanges && !this.isSubmitting) {
            this.applySelection();
            this.submission.submissionDate = this.serverDateService.now();
            this.quizParticipationService
                .saveOrSubmitForLiveMode(this.submission, this.quizId, false)
                .pipe(take(1))
                .subscribe({
                    next: () => {
                        this.unsavedChanges = false;
                        this.updateSubmissionTime();
                    },
                    error: (error: HttpErrorResponse) => this.onSaveError(error.message),
                });
        }
    }

    /**
     * update the value for adjustedSubmissionDate in submission
     */
    updateSubmissionTime() {
        if (this.submission.submissionDate) {
            if (Math.abs(dayjs(this.submission.submissionDate).diff(this.serverDateService.now(), 'seconds')) < 2) {
                this.justSaved = true;
                this.timeoutJustSaved();
            }
        }
    }

    /**
     * Callback function for handling quiz submission after saving submission to server
     * @param error a potential error during save
     */
    onSaveError(error: string) {
        if (error) {
            const errorMessage = 'Saving answers failed: ' + error;
            this.alertService.addAlert({
                type: AlertType.DANGER,
                message: errorMessage,
                disableTranslation: true,
            });
            this.unsavedChanges = true;
            this.isSubmitting = false;
        }
    }

    /**
     * Checks if the student has interacted with each question of the quiz:
     * - for a Multiple Choice Questions it checks if an answer option was selected
     * - for a Drag and Drop Questions it checks if at least one mapping has been made
     * - for a Short Answer Questions it checks if at least one field has been clicked in
     * @return {boolean} true when student interacted with every question, false when not with every questions has an interaction
     */
    areAllQuestionsAnswered(): boolean {
        if (!this.quizExercise.quizQuestions) {
            return true;
        }

        for (const question of this.quizExercise.quizQuestions) {
            switch (question.type) {
                case QuizQuestionType.MULTIPLE_CHOICE:
                    const options = this.selectedAnswerOptions.get(question.id!);
                    if (options && options.length === 0) {
                        return false;
                    }
                    break;
                case QuizQuestionType.DRAG_AND_DROP:
                    const mappings = this.dragAndDropMappings.get(question.id!);
                    if (mappings && mappings.length === 0) {
                        return false;
                    }
                    break;
                case QuizQuestionType.SHORT_ANSWER:
                    const submittedTexts = this.shortAnswerSubmittedTexts.get(question.id!);
                    if (submittedTexts && submittedTexts.length === 0) {
                        return false;
                    }
                    break;
            }
        }

        return true;
    }

    /**
     * This function is called when the user clicks the 'Submit' button
     */
    onSubmit() {
        const translationBasePath = 'artemisApp.quizExercise.';
        this.applySelection();
        let confirmSubmit = true;

        if (this.remainingTimeSeconds > 15 && !this.areAllQuestionsAnswered()) {
            const warningText = this.translateService.instant(translationBasePath + 'submissionWarning');
            confirmSubmit = window.confirm(warningText);
        }
        if (confirmSubmit) {
            this.isSubmitting = true;
            switch (this.mode) {
                case 'practice':
                    if (!this.submission.id) {
                        this.quizParticipationService.submitForPractice(this.submission, this.quizId).subscribe({
                            next: (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            error: (error: HttpErrorResponse) => this.onSubmitError(error),
                        });
                    }
                    break;
                case 'preview':
                    if (!this.submission.id) {
                        this.quizParticipationService.submitForPreview(this.submission, this.quizId).subscribe({
                            next: (response: HttpResponse<Result>) => {
                                this.onSubmitPracticeOrPreviewSuccess(response.body!);
                            },
                            error: (error: HttpErrorResponse) => this.onSubmitError(error),
                        });
                    }
                    break;
                case 'live':
                    // copy submission and send it through websocket with 'submitted = true'
                    const quizSubmission = new QuizSubmission();
                    quizSubmission.submittedAnswers = this.submission.submittedAnswers;
                    this.quizParticipationService.saveOrSubmitForLiveMode(quizSubmission, this.quizId, true).subscribe({
                        next: (response: HttpResponse<QuizSubmission>) => {
                            this.submission = response.body!;
                            this.isSubmitting = false;
                            this.unsavedChanges = false;
                            this.updateSubmissionTime();
                            this.applySubmission();
                            if (this.quizExercise.quizMode !== QuizMode.SYNCHRONIZED) {
                                this.alertService.success('artemisApp.quizExercise.submitSuccess');
                            }
                        },
                        error: (error: HttpErrorResponse) => this.onSubmitError(error),
                    });
                    break;
            }
        }
    }

    /**
     * Callback function for handling response after submitting for practice or preview
     * @param result
     */
    onSubmitPracticeOrPreviewSuccess(result: Result) {
        this.isSubmitting = false;
        this.submission = result.submission as QuizSubmission;
        // make sure the additional information (explanations, correct answers) is available
        const quizExercise = (this.submission.participation! as StudentParticipation).exercise as QuizExercise;
        this.transferInformationToQuizExercise(quizExercise);
        this.applySubmission();
        this.showResult(result);
    }

    /**
     * Callback function for handling error when submitting
     * @param error
     */
    onSubmitError(error: HttpErrorResponse) {
        const errorMessage = 'Submitting the quiz was not possible. ' + error.headers?.get('X-artemisApp-message') || error.message;
        this.alertService.addAlert({
            type: AlertType.DANGER,
            message: errorMessage,
            disableTranslation: true,
        });
        this.isSubmitting = false;
    }

    /**
     * By clicking on the bubble of the progress navigation towards the corresponding question of the quiz is triggered
     * @param questionIndex
     */
    navigateToQuestion(questionIndex: number): void {
        document.getElementById('question' + questionIndex)!.scrollIntoView({
            behavior: 'smooth',
        });
    }

    /**
     * Refresh quiz
     */
    refreshQuiz(refresh = false) {
        this.refreshingQuiz = refresh;
        this.quizExerciseService.findForStudent(this.quizId).subscribe({
            next: (res: HttpResponse<QuizExercise>) => {
                const quizExercise = res.body!;
                if (quizExercise.quizStarted) {
                    if (quizExercise.quizEnded) {
                        this.waitingForQuizStart = false;
                        this.endDate = dayjs();
                    }
                    this.quizExercise = quizExercise;
                    this.initQuiz();
                    this.initLiveMode();
                }
                setTimeout(() => (this.refreshingQuiz = false), 500); // ensure min animation duration
            },
            error: () => {
                setTimeout(() => (this.refreshingQuiz = false), 500); // ensure min animation duration
            },
        });
    }

    joinBatch() {
        this.quizExerciseService.join(this.quizId, this.password).subscribe({
            next: (res: HttpResponse<QuizBatch>) => {
                if (res.body) {
                    this.quizBatch = res.body;
                    if (this.quizBatch?.started) {
                        this.refreshQuiz();
                    } else {
                        this.subscribeToWebsocketChannels();
                    }
                }
            },
            error: (error: HttpErrorResponse) => {
                const errorMessage = 'Joining the quiz was not possible: ' + error.headers?.get('X-artemisApp-message') || error.message;
                this.alertService.addAlert({
                    type: AlertType.DANGER,
                    message: errorMessage,
                    disableTranslation: true,
                });
            },
        });
    }
}
