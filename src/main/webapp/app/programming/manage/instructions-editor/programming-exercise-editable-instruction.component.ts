import {
    AfterViewInit,
    Component,
    EventEmitter,
    HostListener,
    Input,
    OnChanges,
    OnDestroy,
    OnInit,
    Output,
    SimpleChanges,
    ViewChild,
    ViewEncapsulation,
    computed,
    inject,
    signal,
} from '@angular/core';
import { AlertService } from 'app/shared/service/alert.service';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { Observable, Subject, Subscription, of, throwError } from 'rxjs';
import { catchError, map, switchMap, tap } from 'rxjs/operators';
import { ProgrammingExerciseTestCase } from 'app/programming/shared/entities/programming-exercise-test-case.model';
import { ProblemStatementAnalysis } from 'app/programming/manage/instructions-editor/analysis/programming-exercise-instruction-analysis.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { hasExerciseChanged } from 'app/exercise/util/exercise.utils';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ProgrammingExerciseGradingService } from 'app/programming/manage/services/programming-exercise-grading.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { faCheckCircle, faCircleNotch, faExclamationTriangle, faSave } from '@fortawesome/free-solid-svg-icons';
import { MarkdownEditorHeight, MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { FormulaAction } from 'app/shared/monaco-editor/model/actions/formula.action';
import { TaskAction } from 'app/shared/monaco-editor/model/actions/task.action';
import { TestCaseAction } from 'app/shared/monaco-editor/model/actions/test-case.action';
import { TextEditorDomainAction } from 'app/shared/monaco-editor/model/actions/text-editor-domain-action.model';
import { NgClass } from '@angular/common';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseInstructionAnalysisComponent } from './analysis/programming-exercise-instruction-analysis.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RewriteAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewrite.action';
import { PROFILE_IRIS } from 'app/app.constants';
import RewritingVariant from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-variant';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ActivatedRoute } from '@angular/router';
import { ConsistencyCheckAction } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check.action';
import { Annotation } from 'app/programming/shared/code-editor/monaco/code-editor-monaco.component';
import { RewriteResult } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/rewriting-result';

@Component({
    selector: 'jhi-programming-exercise-editable-instructions',
    templateUrl: './programming-exercise-editable-instruction.component.html',
    styleUrls: ['./programming-exercise-editable-instruction.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [
        MarkdownEditorMonacoComponent,
        ProgrammingExerciseInstructionComponent,
        NgClass,
        FaIconComponent,
        TranslateDirective,
        NgbTooltip,
        ProgrammingExerciseInstructionAnalysisComponent,
        ArtemisTranslatePipe,
    ],
})
export class ProgrammingExerciseEditableInstructionComponent implements AfterViewInit, OnChanges, OnDestroy, OnInit {
    private activatedRoute = inject(ActivatedRoute);
    private programmingExerciseService = inject(ProgrammingExerciseService);
    private alertService = inject(AlertService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private testCaseService = inject(ProgrammingExerciseGradingService);
    private profileService = inject(ProfileService);
    private artemisIntelligenceService = inject(ArtemisIntelligenceService);

    participationValue: Participation;
    programmingExercise: ProgrammingExercise;

    exerciseTestCases: string[] = [];

    taskRegex = TaskAction.GLOBAL_TASK_REGEX;
    testCaseAction = new TestCaseAction();
    domainActions: TextEditorDomainAction[] = [new FormulaAction(), new TaskAction(), this.testCaseAction];

    courseId: number;
    exerciseId: number;
    irisEnabled = this.profileService.isProfileActive(PROFILE_IRIS);
    artemisIntelligenceActions = computed(() =>
        this.irisEnabled
            ? [
                  new RewriteAction(
                      this.artemisIntelligenceService,
                      RewritingVariant.PROBLEM_STATEMENT,
                      this.courseId,
                      signal<RewriteResult>({ result: '', inconsistencies: undefined, suggestions: undefined, improvement: undefined }),
                  ),
                  ...(this.exerciseId ? [new ConsistencyCheckAction(this.artemisIntelligenceService, this.exerciseId, this.renderedConsistencyCheckResultMarkdown)] : []),
              ]
            : [],
    );

    savingInstructions = false;
    unsavedChangesValue = false;

    renderedConsistencyCheckResultMarkdown = signal<string>('');
    showConsistencyCheck = computed(() => !!this.renderedConsistencyCheckResultMarkdown());

    testCaseSubscription: Subscription;
    forceRenderSubscription: Subscription;

    @ViewChild(MarkdownEditorMonacoComponent, { static: false }) markdownEditorMonaco?: MarkdownEditorMonacoComponent;

    @Input() showStatus = true;
    // If the programming exercise is being created, some features have to be disabled (saving the problemStatement & querying test cases).
    @Input() editMode = true;
    @Input() enableResize = true;
    @Input({ required: true }) initialEditorHeight: MarkdownEditorHeight | 'external';
    @Input() showSaveButton = false;
    @Input() templateParticipation: Participation;
    @Input() forceRender: Observable<void>;
    @Input()
    get exercise() {
        return this.programmingExercise;
    }
    @Input()
    get participation() {
        return this.participationValue;
    }

    @Output() participationChange = new EventEmitter<Participation>();
    @Output() hasUnsavedChanges = new EventEmitter<boolean>();
    @Output() exerciseChange = new EventEmitter<ProgrammingExercise>();
    @Output() instructionChange = new EventEmitter<string>();
    generateHtmlSubject: Subject<void> = new Subject<void>();

    set participation(participation: Participation) {
        this.participationValue = participation;
        this.participationChange.emit(this.participationValue);
    }

    set exercise(exercise: ProgrammingExercise) {
        if (this.programmingExercise && exercise.problemStatement !== this.programmingExercise.problemStatement) {
            this.unsavedChanges = true;
        }
        this.programmingExercise = exercise;
        this.exerciseChange.emit(this.programmingExercise);
    }

    set unsavedChanges(hasChanges: boolean) {
        this.unsavedChangesValue = hasChanges;
        if (hasChanges) {
            this.hasUnsavedChanges.emit(hasChanges);
        }
    }

    // Icons
    faSave = faSave;
    faCheckCircle = faCheckCircle;
    faExclamationTriangle = faExclamationTriangle;
    faCircleNotch = faCircleNotch;

    protected readonly MarkdownEditorHeight = MarkdownEditorHeight;

    ngOnInit() {
        this.courseId = Number(this.activatedRoute.snapshot.paramMap.get('courseId'));
        this.exerciseId = Number(this.activatedRoute.snapshot.paramMap.get('exerciseId'));
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (hasExerciseChanged(changes)) {
            this.setupTestCaseSubscription();
        }
    }

    ngOnDestroy(): void {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }
        if (this.forceRenderSubscription) {
            this.forceRenderSubscription.unsubscribe();
        }
    }

    ngAfterViewInit() {
        // If forced to render, generate the instruction HTML.
        if (this.forceRender) {
            this.forceRenderSubscription = this.forceRender.subscribe(() => this.generateHtml());
        }
    }

    /** Save the problem statement on the server.
     * @param event
     **/
    saveInstructions(event: any) {
        event.stopPropagation();
        this.savingInstructions = true;
        return this.programmingExerciseService
            .updateProblemStatement(this.exercise.id!, this.exercise.problemStatement!)
            .pipe(
                tap(() => {
                    this.unsavedChanges = false;
                }),
                catchError(() => {
                    // TODO: move to programming exercise translations
                    this.alertService.error(`artemisApp.editor.errors.problemStatementCouldNotBeUpdated`);
                    return of(undefined);
                }),
            )
            .subscribe(() => {
                this.savingInstructions = false;
            });
    }

    @HostListener('document:keydown.control.s', ['$event'])
    saveOnControlAndS(event: KeyboardEvent) {
        if (!navigator.userAgent.includes('Mac')) {
            event.preventDefault();
            this.saveInstructions(event);
        }
    }

    @HostListener('document:keydown.meta.s', ['$event'])
    saveOnCommandAndS(event: KeyboardEvent) {
        if (navigator.userAgent.includes('Mac')) {
            event.preventDefault();
            this.saveInstructions(event);
        }
    }

    updateProblemStatement(problemStatement: string) {
        if (this.exercise.problemStatement !== problemStatement) {
            this.exercise = { ...this.exercise, problemStatement };
            this.unsavedChanges = true;
        }
        this.instructionChange.emit(problemStatement);
    }

    dismissConsistencyCheck() {
        this.renderedConsistencyCheckResultMarkdown.set('');
    }

    /**
     * Signal that the markdown should be rendered into html.
     */
    generateHtml() {
        this.generateHtmlSubject.next();
    }

    private setupTestCaseSubscription() {
        if (this.testCaseSubscription) {
            this.testCaseSubscription.unsubscribe();
        }

        // Only set up a subscription for test cases if the exercise already exists.
        if (this.editMode) {
            this.testCaseSubscription = this.testCaseService
                .subscribeForTestCases(this.exercise.id!)
                .pipe(
                    switchMap((testCases: ProgrammingExerciseTestCase[] | undefined) => {
                        // If there are test cases, map them to their names, sort them and use them for the markdown editor.
                        if (testCases) {
                            const sortedTestCaseNames = testCases
                                .filter((testCase) => testCase.active)
                                .map((testCase) => testCase.testName!)
                                .sort();
                            return of(sortedTestCaseNames);
                        } else if (this.exercise.templateParticipation) {
                            // Legacy case: If there are no test cases, but a template participation, use its feedbacks for generating test names.
                            return this.loadTestCasesFromTemplateParticipationResult(this.exercise.templateParticipation!.id!);
                        }
                        return of();
                    }),
                    tap((testCaseNames: string[]) => {
                        this.exerciseTestCases = testCaseNames;
                        const cases = this.exerciseTestCases.map((value) => ({ value, id: value }));
                        this.testCaseAction.setValues(cases);
                    }),
                    catchError(() => of()),
                )
                .subscribe();
        }
    }

    /**
     * Generate test case names from the feedback of the exercise's templateParticipation.
     * This is the fallback for older programming exercises without test cases in the database.
     * @param templateParticipationId
     */
    loadTestCasesFromTemplateParticipationResult = (templateParticipationId: number): Observable<Array<string | undefined>> => {
        // Fallback for exercises that don't have test cases yet.
        return this.programmingExerciseParticipationService.getLatestResultWithFeedback(templateParticipationId).pipe(
            map((result) => (!result?.feedbacks ? throwError(() => new Error('no result available')) : result)),
            // use the text (legacy case) or the name of the provided test case attribute
            map(({ feedbacks }: Result) => feedbacks!.map((feedback) => feedback.text ?? feedback.testCase?.testName).sort()),
            catchError(() => of([])),
        );
    };

    /**
     * On every update of the problem statement analysis, update the appropriate line numbers of the editor with the results of the analysis.
     * Will show warning symbols for every item.
     *
     * @param analysis that contains the resulting issues of the problem statement.
     */
    onAnalysisUpdate = (analysis: ProblemStatementAnalysis) => {
        const lineWarnings = this.mapAnalysisToWarnings(analysis);
        this.markdownEditorMonaco?.monacoEditor?.setAnnotations(lineWarnings as Annotation[]);
    };

    private mapAnalysisToWarnings = (analysis: ProblemStatementAnalysis) => {
        return Array.from(analysis.values()).flatMap(({ lineNumber, invalidTestCases, repeatedTestCases }) =>
            this.mapIssuesToAnnotations(lineNumber, invalidTestCases, repeatedTestCases),
        );
    };

    private mapIssuesToAnnotations = (lineNumber: number, invalidTestCases?: string[], repeatedTestCases?: string[]) => {
        const mapIssues = (issues: string[]) => ({ row: lineNumber, column: 0, text: ' - ' + issues.join('\n - '), type: 'warning' });

        const annotations = [];
        if (invalidTestCases) {
            annotations.push(mapIssues(invalidTestCases));
        }

        if (repeatedTestCases) {
            annotations.push(mapIssues(repeatedTestCases));
        }

        return annotations;
    };
}
