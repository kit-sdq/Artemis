import { AfterViewInit, ChangeDetectorRef, Component, OnDestroy, OnInit, inject, viewChild, viewChildren } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamPage } from 'app/exam/shared/entities/exam-page.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { ExamNavigationBarComponent } from 'app/exam/overview/exam-navigation-bar/exam-navigation-bar.component';
import { ModelingExamSubmissionComponent } from 'app/exam/overview/exercises/modeling/modeling-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/overview/exercises/quiz/quiz-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/overview/exercises/text/text-exam-submission.component';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import dayjs from 'dayjs/esm';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';
import { Observable, Subscription, forkJoin, map, mergeMap, tap, toArray } from 'rxjs';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { FileUploadExamSubmissionComponent } from 'app/exam/overview/exercises/file-upload/file-upload-exam-submission.component';
import { SubmissionVersionService } from 'app/exercise/submission-version/submission-version.service';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { ExamPageComponent } from 'app/exam/overview/exercises/exam-page.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MatSlider, MatSliderThumb } from '@angular/material/slider';
import { FormsModule } from '@angular/forms';
import { toObservable } from '@angular/core/rxjs-interop';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';

@Component({
    selector: 'jhi-student-exam-timeline',
    templateUrl: './student-exam-timeline.component.html',
    styleUrls: ['./student-exam-timeline.component.scss'],
    imports: [
        TranslateDirective,
        MatSlider,
        MatSliderThumb,
        FormsModule,
        ExamNavigationBarComponent,
        QuizExamSubmissionComponent,
        FileUploadExamSubmissionComponent,
        TextExamSubmissionComponent,
        ModelingExamSubmissionComponent,
        ProgrammingExerciseExamDiffComponent,
    ],
})
export class StudentExamTimelineComponent implements OnInit, AfterViewInit, OnDestroy {
    private activatedRoute = inject(ActivatedRoute);
    private submissionService = inject(SubmissionService);
    private submissionVersionService = inject(SubmissionVersionService);
    private cdr = inject(ChangeDetectorRef);

    readonly ExerciseType = ExerciseType;
    readonly SubmissionVersion = SubmissionVersion;

    // stores if a page component has already been visited (true) or not (false)
    // this is an array because the exam-timeline uses a page component for each exercise
    pageComponentVisited: boolean[];
    selectedTimestamp: number;
    timestampIndex = 0;

    studentExam: StudentExam;
    exerciseIndex: number;
    activeExamPage = new ExamPage();
    submissionTimeStamps: dayjs.Dayjs[] = [];
    submissionVersions: SubmissionVersion[] = [];
    programmingSubmissions: ProgrammingSubmission[] = [];
    fileUploadSubmissions: FileUploadSubmission[] = [];

    currentExercise: Exercise | undefined;
    currentSubmission: SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined;
    changesSubscription: Subscription;
    cachedDiffInformation: Map<string, RepositoryDiffInformation> = new Map<string, RepositoryDiffInformation>();

    currentPageComponents = viewChildren(ExamSubmissionComponent);
    examNavigationBarComponent = viewChild.required<ExamNavigationBarComponent>('examNavigationBar');

    private activatedRouteSubscription: Subscription;

    ngOnInit(): void {
        this.activatedRouteSubscription = this.activatedRoute.data.subscribe(({ studentExam: studentExamWithGrade }) => {
            this.studentExam = studentExamWithGrade.studentExam;
        });
        this.exerciseIndex = 0;
        this.pageComponentVisited = new Array(this.studentExam.exercises!.length).fill(false);
        this.retrieveSubmissionDataAndTimeStamps().subscribe((results) => {
            const allSubmissions = results.flat();
            allSubmissions.forEach((result) => {
                //workaround because instanceof does not work.
                if (this.isSubmissionVersion(result)) {
                    const submissionVersion = result as SubmissionVersion;
                    this.submissionVersions.push(submissionVersion);
                    this.submissionTimeStamps.push(submissionVersion.createdDate);
                } else if (this.isFileUploadSubmission(result)) {
                    const fileUploadSubmission = result as FileUploadSubmission;
                    this.fileUploadSubmissions.push(fileUploadSubmission);
                    this.submissionTimeStamps.push(fileUploadSubmission.submissionDate!);
                } else {
                    const programmingSubmission = result as ProgrammingSubmission;
                    this.programmingSubmissions.push(programmingSubmission);
                    this.submissionTimeStamps.push(programmingSubmission.submissionDate!);
                }
            });
            this.sortTimeStamps();
            this.selectedTimestamp = this.submissionTimeStamps[0]?.toDate().getTime() ?? 0;
            const firstSubmission = this.findFirstSubmission();
            this.currentSubmission = firstSubmission;
            this.exerciseIndex = this.findExerciseIndex(firstSubmission!);
            this.examNavigationBarComponent().changePage(false, this.exerciseIndex, false, firstSubmission);
        });
    }

    ngAfterViewInit(): void {
        this.changesSubscription = this.currentPageComponentsChanges.subscribe(() => {
            this.updateSubmissionOrSubmissionVersionInView();
        });
    }
    ngOnDestroy(): void {
        this.activatedRouteSubscription?.unsubscribe();
        this.changesSubscription?.unsubscribe();
    }

    /**
     * Updates the view for a programming exercise by setting the correct submission for the component
     * This does not really work yet because the programming submission is not updated in the view
     * This will change in the followup PR #7097
     * Programming exercises do not support submission versions and therefore, need to be handled differently.
     */
    private updateProgrammingExerciseView() {
        const activeProgrammingComponent = this.activePageComponent as ProgrammingExerciseExamDiffComponent | undefined;
        if (activeProgrammingComponent) {
            activeProgrammingComponent.studentParticipation.update(() => this.currentExercise!.studentParticipations![0]);
            activeProgrammingComponent.exercise.update(() => this.currentExercise!);
            activeProgrammingComponent.currentSubmission.update(() => this.currentSubmission as ProgrammingSubmission);
            activeProgrammingComponent.previousSubmission.update(() => this.findPreviousProgrammingSubmission(this.currentExercise!, this.currentSubmission!));
            activeProgrammingComponent.submissions.update(() =>
                this.programmingSubmissions.filter((submission) => submission.participation?.exercise?.id === this.currentExercise?.id),
            );
            activeProgrammingComponent.exerciseIdSubject.update((subject) => {
                subject.next(this.currentExercise!.id!);
                return subject;
            });
        }
    }

    findPreviousProgrammingSubmission(currentExercise: Exercise, currentSubmission: ProgrammingSubmission): ProgrammingSubmission | undefined {
        const comparisonTimestamp: dayjs.Dayjs = currentSubmission.submissionDate!;
        let smallestDiff = Infinity;
        let correspondingSubmission: ProgrammingSubmission | undefined;
        for (const programmingSubmission of this.programmingSubmissions) {
            const diff = Math.abs(programmingSubmission.submissionDate!.diff(comparisonTimestamp));
            if (
                programmingSubmission.submissionDate!.isBefore(comparisonTimestamp) &&
                diff < smallestDiff &&
                programmingSubmission.participation?.exercise?.id === currentExercise.id
            ) {
                smallestDiff = diff;
                correspondingSubmission = programmingSubmission;
            }
        }
        return correspondingSubmission;
    }

    /**
     * Updates the view for a file upload exercise by setting the correct submission for the component
     * File Upload exercises do not support submission versions and therefore, need to be handled differently.
     */
    private updateFileUploadExerciseView() {
        const fileUploadComponent = this.activePageComponent as FileUploadExamSubmissionComponent;
        if (fileUploadComponent) {
            fileUploadComponent.studentSubmission.update(() => this.currentSubmission as FileUploadSubmission);
            fileUploadComponent.updateViewFromSubmission();
        }
    }

    displayCurrentTimestamp(): string {
        return dayjs(this.selectedTimestamp).format('HH:mm:ss');
    }

    /**
     * Checks if the submission is a submission version
     * Instanceof does not work here because it always returns false.
     * @param object the object to check
     */
    isSubmissionVersion(object: SubmissionVersion | Submission | undefined) {
        if (!object) {
            return false;
        }
        const submissionVersion = object as SubmissionVersion;
        // submissionVersion.content is intentionally not checked because it can be undefined if the content is empty
        return submissionVersion.id && submissionVersion.createdDate && submissionVersion.submission;
    }

    /**
     * Helper method to check if the object is a file upload submission
     * Instanceof does not work here because we have submission objects that are is only a super class of FileUploadSubmission
     * @param object the object to check
     */
    private isFileUploadSubmission(object: FileUploadSubmission | SubmissionVersion | ProgrammingSubmission | undefined) {
        if (!object) {
            return false;
        }
        const fileUploadSubmission = object as FileUploadSubmission;
        return !!fileUploadSubmission.id && fileUploadSubmission.submissionDate && fileUploadSubmission.filePath;
    }

    /**
     * Retrieve all submission versions/submissions for all exercises of the exam
     */
    retrieveSubmissionDataAndTimeStamps() {
        const submissionObservables: Observable<SubmissionVersion[] | Submission[]>[] = [];
        this.studentExam.exercises?.forEach((exercise) => {
            if (exercise.type === ExerciseType.PROGRAMMING) {
                const id = exercise.studentParticipations![0].id!;
                const programmingSubmission = this.submissionService.findAllSubmissionsOfParticipation(id).pipe(map(({ body }) => body!));
                submissionObservables.push(programmingSubmission);
            } else if (exercise.type === ExerciseType.FILE_UPLOAD) {
                const id = exercise.studentParticipations![0].id!;
                submissionObservables.push(this.submissionService.findAllSubmissionsOfParticipation(id).pipe(map(({ body }) => body!)));
            } else {
                submissionObservables.push(
                    this.submissionVersionService.findAllSubmissionVersionsOfSubmission(exercise.studentParticipations![0].submissions![0].id!).pipe(
                        mergeMap((versions) => versions),
                        toArray(),
                    ),
                );
            }
        });

        return forkJoin([...submissionObservables]).pipe(tap(() => this.cdr.detectChanges()));
    }

    /**
     * Sorts the time stamps in ascending order
     */
    private sortTimeStamps() {
        this.submissionTimeStamps = this.submissionTimeStamps.sort((date1, date2) => (date1.isAfter(date2) ? 1 : -1));
    }

    /**
     * This method is called when the user clicks on the next or previous button in the navigation bar or on the slider.
     * @param exerciseChange contains the exercise to which the user wants to navigate to and the submission that should be displayed
     */
    onPageChange(exerciseChange: { exercise?: Exercise; submission?: ProgrammingSubmission | SubmissionVersion | FileUploadSubmission }): void {
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onDeactivate();
        }

        if (!exerciseChange.submission) {
            // only change the submission if the exercise has changed, prevents unnecessary updates if you press the same button multiple times on the navigation bar
            if (exerciseChange.exercise !== this.currentExercise) {
                exerciseChange.submission = this.findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exerciseChange.exercise!);
            }
        }

        if (exerciseChange.submission) {
            if (this.isSubmissionVersion(exerciseChange.submission)) {
                const submissionVersion = exerciseChange.submission as SubmissionVersion;
                this.selectedTimestamp = submissionVersion.createdDate.toDate().getTime();
            } else if (this.isFileUploadSubmission(exerciseChange.submission)) {
                const fileUploadSubmission = exerciseChange.submission as FileUploadSubmission;
                this.selectedTimestamp = fileUploadSubmission.submissionDate!.toDate().getTime();
            } else {
                const programmingSubmission = exerciseChange.submission as ProgrammingSubmission;
                this.selectedTimestamp = programmingSubmission.submissionDate!.toDate().getTime();
            }
            this.currentSubmission = exerciseChange.submission;
            this.initializeExercise(exerciseChange.exercise!, exerciseChange.submission);
        }
    }

    /**
     * Finds the first submission of the student exam
     * Used to determine what's the first submission and hence, the first exercise to display.
     */

    private findFirstSubmission(): FileUploadSubmission | SubmissionVersion | ProgrammingSubmission | undefined {
        const submissionVersion = this.submissionVersions.find((submission) => submission.createdDate.isSame(this.submissionTimeStamps[0]));
        if (!submissionVersion) {
            const programmingSubmission = this.programmingSubmissions.find((submission) => submission.submissionDate?.isSame(this.submissionTimeStamps[0]));
            if (!programmingSubmission) {
                return this.fileUploadSubmissions.find((submission) => submission.submissionDate?.isSame(this.submissionTimeStamps[0]));
            } else {
                return programmingSubmission;
            }
        }
        return submissionVersion;
    }

    initializeExercise(exercise: Exercise, submission: Submission | SubmissionVersion | undefined) {
        this.activeExamPage.exercise = exercise;
        // set current exercise index
        this.exerciseIndex = this.studentExam.exercises!.findIndex((exercise1) => exercise1.id === exercise.id);
        this.currentExercise = exercise;
        this.currentSubmission = submission;
        this.activateActiveComponent();
        this.updateSubmissionOrSubmissionVersionInView();
    }

    private updateSubmissionOrSubmissionVersionInView() {
        if (this.currentExercise?.type === ExerciseType.PROGRAMMING) {
            this.updateProgrammingExerciseView();
        } else if (this.currentExercise?.type === ExerciseType.FILE_UPLOAD) {
            this.updateFileUploadExerciseView();
        } else {
            const activePageComponent = this.activePageComponent as ExamSubmissionComponent | undefined;
            activePageComponent?.setSubmissionVersion(this.currentSubmission as SubmissionVersion);
        }
    }

    private activateActiveComponent() {
        this.pageComponentVisited[this.activePageIndex] = true;
        const activeComponent = this.activePageComponent;
        if (activeComponent) {
            activeComponent.onActivate();
        }
    }

    get activePageIndex(): number {
        return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === this.activeExamPage.exercise?.id);
    }

    get activePageComponent(): ExamPageComponent | undefined {
        // we have to find the current component based on the activeExercise because the queryList might not be full yet (e.g. only 2 of 5 components initialized)
        return this.currentPageComponents().find((submissionComponent) => (submissionComponent as ExamSubmissionComponent).getExercise().id === this.activeExamPage.exercise?.id);
    }

    /**
     * This method is called when the user clicks on the slider
     */
    onSliderInputChange() {
        this.selectedTimestamp = this.submissionTimeStamps[this.timestampIndex].toDate().getTime();
        const submission = this.findCorrespondingSubmissionForTimestamp(this.selectedTimestamp);
        if (this.isSubmissionVersion(submission)) {
            const submissionVersion = submission as SubmissionVersion;
            this.currentExercise = submissionVersion.submission.participation?.exercise;
        } else if (this.isFileUploadSubmission(submission)) {
            const fileUploadSubmission = submission as FileUploadSubmission;
            this.currentExercise = fileUploadSubmission.participation?.exercise;
        } else {
            const programmingSubmission = submission as ProgrammingSubmission;
            this.currentExercise = programmingSubmission.participation?.exercise;
        }
        const exerciseIndex = this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === this.currentExercise?.id);
        this.exerciseIndex = exerciseIndex;
        this.currentSubmission = submission;
        this.examNavigationBarComponent().changePage(false, exerciseIndex, false, submission);
    }

    /**
     * Finds the submission for the current timestamp.
     * @param timestamp The timestamp for which the submission should be found.
     */
    private findCorrespondingSubmissionForTimestamp(timestamp: number): SubmissionVersion | ProgrammingSubmission | FileUploadSubmission | undefined {
        const comparisonObject = dayjs(timestamp);
        for (const submissionVersion of this.submissionVersions) {
            if (submissionVersion.createdDate.isSame(comparisonObject)) {
                return submissionVersion;
            }
        }
        for (const programmingSubmission of this.programmingSubmissions) {
            if (programmingSubmission.submissionDate?.isSame(comparisonObject)) {
                return programmingSubmission;
            }
        }
        for (const fileUploadSubmission of this.fileUploadSubmissions) {
            if (fileUploadSubmission.submissionDate?.isSame(comparisonObject)) {
                return fileUploadSubmission;
            }
        }
        return undefined;
    }

    /**
     * Finds the submission for the exercise with the closest timestamp to the current timestamp.
     * @param exercise The exercise for which the submission should be found.
     */
    private findSubmissionForExerciseClosestToCurrentTimeStampForExercise(exercise: Exercise) {
        const comparisonObject = dayjs(this.selectedTimestamp);
        let smallestDiff = Infinity;
        let timestampWithSmallestDiff = 0;
        if (exercise.type === ExerciseType.PROGRAMMING) {
            timestampWithSmallestDiff = this.findClosestTimestampForExerciseInSubmissionArray(exercise, this.programmingSubmissions);
        } else if (exercise.type === ExerciseType.FILE_UPLOAD) {
            // file upload exercises only have one submission
            return this.fileUploadSubmissions.find((submission) => submission.participation?.exercise?.id === exercise.id);
        } else {
            const numberOfSubmissionsForExercise = this.submissionVersions.filter((submission) => submission.submission?.participation?.exercise?.id === exercise.id).length;
            for (const submissionVersion of this.submissionVersions) {
                if (
                    Math.abs(submissionVersion.createdDate.diff(comparisonObject)) < smallestDiff &&
                    submissionVersion.submission.participation?.exercise?.id === exercise.id &&
                    (!submissionVersion.createdDate.isSame(comparisonObject) || numberOfSubmissionsForExercise === 1)
                ) {
                    smallestDiff = Math.abs(submissionVersion.createdDate.diff(comparisonObject));
                    timestampWithSmallestDiff = submissionVersion.createdDate.valueOf();
                }
            }
        }
        return this.findCorrespondingSubmissionForTimestamp(timestampWithSmallestDiff);
    }

    /**
     * Finds the closest timestamp for a submission of a given exercise in a given submission array
     * @param exercise the exercise for which the submission should be found
     * @param submissions the submissions array in which the submission should be found
     */
    private findClosestTimestampForExerciseInSubmissionArray(exercise: Exercise, submissions: Submission[]): number {
        const comparisonObject = dayjs(this.selectedTimestamp);
        let smallestDiff = Infinity;
        let timestampWithSmallestDiff = 0;
        const numberOfSubmissionsForExercise = submissions.filter(
            (submission: ProgrammingSubmission | FileUploadSubmission) => submission.participation?.exercise?.id === exercise.id,
        ).length;
        for (const submission of submissions) {
            if (
                submission.submissionDate!.diff(comparisonObject) < smallestDiff &&
                submission.participation?.exercise?.id === exercise.id &&
                (!submission.submissionDate?.isSame(comparisonObject) || numberOfSubmissionsForExercise === 1)
            ) {
                smallestDiff = submission.submissionDate!.diff(comparisonObject);
                timestampWithSmallestDiff = submission.submissionDate!.valueOf();
            }
        }
        return timestampWithSmallestDiff;
    }

    private findExerciseIndex(firstSubmission: FileUploadSubmission | SubmissionVersion | ProgrammingSubmission) {
        if (this.isSubmissionVersion(firstSubmission)) {
            const submissionVersion = firstSubmission as SubmissionVersion;
            return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === submissionVersion.submission.participation?.exercise?.id);
        } else {
            const submission = firstSubmission as FileUploadSubmission | ProgrammingSubmission;
            return this.studentExam.exercises!.findIndex((examExercise) => examExercise.id === submission.participation?.exercise?.id);
        }
    }

    protected readonly currentPageComponentsChanges = toObservable(this.currentPageComponents);
}
