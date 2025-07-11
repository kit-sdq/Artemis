import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Course } from 'app/core/course/shared/entities/course.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Feedback, FeedbackType, STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER } from 'app/assessment/shared/entities/feedback.model';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { SubmissionType } from 'app/exercise/shared/entities/submission/submission.model';
import { BuildLogService } from 'app/programming/shared/services/build-log.service';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { FeedbackItem } from 'app/exercise/feedback/item/feedback-item';
import { ProgrammingFeedbackItemService } from 'app/exercise/feedback/item/programming-feedback-item.service';
import { FeedbackNode } from 'app/exercise/feedback/node/feedback-node';
import { ResultService } from 'app/exercise/result/result.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { of, throwError } from 'rxjs';
import { FeedbackGroup } from 'app/exercise/feedback/group/feedback-group';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';

describe('FeedbackComponent', () => {
    let comp: FeedbackComponent;
    let fixture: ComponentFixture<FeedbackComponent>;

    let exercise: ProgrammingExercise;
    let buildLogService: BuildLogService;
    let resultService: ResultService;
    let profileService: ProfileService;
    let feedbackItemService: ProgrammingFeedbackItemService;

    let buildlogsStub: jest.SpyInstance;
    let getFeedbackDetailsForResultStub: jest.SpyInstance;

    const feedbackReference = {
        id: 1,
        result: { id: 2 } as Result,
        hasLongFeedback: false,
    } as Feedback;

    const makeFeedback = (fb: Feedback) => {
        return Object.assign({ type: FeedbackType.AUTOMATIC, text: '', detailText: '', credits: 0 } as Feedback, fb);
    };

    const makeFeedbackItem = (item: FeedbackItem) => {
        return Object.assign(
            {
                type: 'Reviewer',
                credits: 0,
                title: undefined,
                positive: undefined,
            } as FeedbackItem,
            item,
        );
    };

    const generateSCAFeedbackPair = (
        showDetails: boolean,
        category: string,
        credits: number,
        penalty: number,
        { line = 1, column = undefined }: { line?: number; column?: number } = {},
    ) => {
        return {
            fb: makeFeedback({
                text: STATIC_CODE_ANALYSIS_FEEDBACK_IDENTIFIER + category,
                detailText: JSON.stringify({
                    filePath: 'www/packet/File.java',
                    startLine: line,
                    startColumn: column,
                    rule: 'Rule',
                    message: 'This is a code issue',
                    penalty,
                }),
                credits,
                positive: false,
            }),
            item: makeFeedbackItem({
                type: 'Static Code Analysis',
                name: 'artemisApp.result.detail.codeIssue.name',
                title: 'artemisApp.result.detail.codeIssue.title',
                text: showDetails ? 'Rule: This is a code issue' : 'This is a code issue',
                credits,
                positive: false,
                feedbackReference,
            }),
        };
    };

    const generateTestCaseFeedbackPair = (showDetails: boolean, name: string, message: string | undefined, credits: number) => {
        return {
            fb: makeFeedback({
                testCase: { testName: name },
                detailText: message,
                credits,
                positive: credits > 0,
            }),
            item: makeFeedbackItem({
                type: 'Test',
                name: showDetails ? 'artemisApp.result.detail.test.name' : 'artemisApp.result.detail.feedback',
                text: message,
                credits,
                positive: credits > 0,
                title: showDetails ? (credits > 0 ? 'artemisApp.result.detail.test.passed' : 'artemisApp.result.detail.test.failed') : undefined,
                feedbackReference,
            }),
        };
    };

    const generateManualFeedbackPair = (showDetails: boolean, title: string, text: string, credits = 0) => {
        return {
            fb: makeFeedback({
                type: FeedbackType.MANUAL,
                text: title,
                detailText: text,
                credits,
                positive: credits > 0,
            }),
            item: makeFeedbackItem({
                type: 'Reviewer',
                name: showDetails ? 'artemisApp.course.tutor' : 'artemisApp.result.detail.feedback',
                title,
                text,
                credits,
                positive: credits > 0,
                feedbackReference,
            }),
        };
    };

    const generateFeedbacksAndExpectedItems = (showTestDetails = false) => {
        const feedbacks: Feedback[] = [];
        const expectedItems: FeedbackNode[] = [];
        const addPair = (pair: { fb: Feedback; item: FeedbackItem }) => {
            feedbacks.push(pair.fb);
            expectedItems.push(pair.item);
        };
        addPair(generateSCAFeedbackPair(showTestDetails, 'Bad Practice', -2, 2));
        addPair(generateSCAFeedbackPair(showTestDetails, 'Styling', -0.5, 1, { column: 10 }));
        addPair(generateSCAFeedbackPair(showTestDetails, 'Styling', -0.5, 1, { line: 2, column: 1 }));
        addPair(generateManualFeedbackPair(showTestDetails, 'Positive', 'This is good', 4));
        addPair(generateManualFeedbackPair(showTestDetails, 'Negative', 'This is bad', -2));
        addPair(generateManualFeedbackPair(showTestDetails, 'Neutral', 'This is neutral', 0));
        addPair(generateTestCaseFeedbackPair(showTestDetails, 'TestCase1', 'This failed.', 0));
        addPair(generateTestCaseFeedbackPair(showTestDetails, 'TestCase2', 'This passed.', 3));
        addPair(generateTestCaseFeedbackPair(showTestDetails, 'TestCase3', undefined, 3));

        if (!showTestDetails) {
            expectedItems.pop();
            expectedItems.unshift(
                makeFeedbackItem({
                    type: 'Test',
                    name: 'artemisApp.result.detail.feedback',
                    title: 'artemisApp.result.detail.test.passedTest',
                    positive: true,
                    credits: 3,
                    feedbackReference,
                }),
            );
        }

        return { feedbacks, expectedItems };
    };

    const generateProgrammingSubmission = (buildFailed: boolean) => {
        const programmingSubmission = new ProgrammingSubmission();
        programmingSubmission.buildFailed = buildFailed;
        return programmingSubmission;
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },

                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(FeedbackComponent);
        comp = fixture.componentInstance;
        exercise = {
            maxPoints: 100,
            bonusPoints: 0,
            type: ExerciseType.PROGRAMMING,
            staticCodeAnalysisEnabled: true,
            maxStaticCodeAnalysisPenalty: 20,
            projectKey: 'somekey',
        } as ProgrammingExercise;
        const course = new Course();
        course.id = 3;
        course.title = 'Testcourse';
        exercise.course = course;
        comp.exercise = exercise;
        comp.participation = {
            id: 55,
            type: ParticipationType.PROGRAMMING,
            participantIdentifier: 'student42',
            repositoryUri: 'https://artemis.tum.de/projects/somekey/repos/somekey-student42',
        } as ProgrammingExerciseStudentParticipation;
        comp.result = {
            id: 89,
            submission: {
                participation: {
                    id: 55,
                    type: ParticipationType.PROGRAMMING,
                    participantIdentifier: 'student42',
                    repositoryUri: 'https://artemis.tum.de/projects/somekey/repos/somekey-student42',
                },
            },
        } as Result;
        buildLogService = TestBed.inject(BuildLogService);
        resultService = TestBed.inject(ResultService);
        profileService = TestBed.inject(ProfileService);
        feedbackItemService = TestBed.inject(ProgrammingFeedbackItemService);
        buildlogsStub = jest.spyOn(buildLogService, 'getBuildLogs').mockReturnValue(of([]));
        getFeedbackDetailsForResultStub = jest.spyOn(resultService, 'getFeedbackDetailsForResult').mockReturnValue(of({ body: [] as Feedback[] } as HttpResponse<Feedback[]>));
        // Set profile info
        const profileInfo = new ProfileInfo();
        jest.spyOn(profileService, 'getProfileInfo').mockReturnValue(profileInfo);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should set the exercise from the participation if available', () => {
        comp.exercise = undefined;
        comp.participation.exercise = exercise;

        comp.ngOnInit();

        expect(comp.exercise).toEqual(exercise);
        expect(comp.course).toEqual(exercise.course);
    });

    it('should set the exercise type from the exercise if not available otherwise', () => {
        comp.exerciseType = undefined as any;
        exercise.type = ExerciseType.MODELING;
        comp.exercise = exercise;

        comp.ngOnInit();

        expect(comp.exerciseType).toBe(ExerciseType.MODELING);
    });

    it('should set the exercise type from a programming participation if not available otherwise', () => {
        comp.exerciseType = undefined as any;
        comp.exercise = undefined;
        comp.result.submission!.participation!.type = ParticipationType.PROGRAMMING;

        comp.ngOnInit();

        expect(comp.exerciseType).toBe(ExerciseType.PROGRAMMING);
    });

    it('should generate commit link for programming exercise result with submission, participation and exercise', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;
        comp.result.submission = {
            ...comp.result.submission,
            type: SubmissionType.MANUAL,
            commitHash: '123456789ab',
        } as ProgrammingSubmission;

        comp.ngOnInit();

        expect(comp.getCommitHash()).toBe('123456789ab');
    });

    it('should not try to retrieve the feedbacks from the server if provided result has feedbacks', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).not.toHaveBeenCalled();
        expect(comp.isLoading).toBeFalse();
    });

    it('should try to retrieve the feedbacks from the server if provided result does not have feedbacks', () => {
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        getFeedbackDetailsForResultStub.mockReturnValue(of({ body: feedbacks } as HttpResponse<Feedback[]>));

        comp.ngOnInit();

        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledOnce();
        expect(getFeedbackDetailsForResultStub).toHaveBeenCalledWith(55, comp.result);
        expect(comp.isLoading).toBeFalse();
    });

    it('should try to retrieve build logs if the exercise type is PROGRAMMING and a submission was provided which was marked with build failed.', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.participation = { ...comp.participation, submissions: [{ buildFailed: true } as ProgrammingSubmission] };

        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(55, 89);
        expect(comp.buildLogs).toBeArrayOfSize(0);
        expect(comp.isLoading).toBeFalse();
    });

    it('should not try to retrieve build logs if the exercise type is not PROGRAMMING', () => {
        comp.exerciseType = ExerciseType.MODELING;
        comp.result.submission = new ModelingSubmission();

        comp.ngOnInit();

        expect(buildlogsStub).not.toHaveBeenCalled();
        expect(comp.feedbackItemNodes).toBeUndefined();
        expect(comp.isLoading).toBeFalse();
    });

    it('should not try to retrieve build logs if submission was not marked with build failed', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.submission = generateProgrammingSubmission(false);

        comp.ngOnInit();

        expect(buildlogsStub).not.toHaveBeenCalled();
        expect(comp.buildLogs).toBeUndefined();
        expect(comp.isLoading).toBeFalse();
    });

    it('fetchBuildLogs should suppress 403 error', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.participation = { ...comp.participation, submissions: [{ buildFailed: true } as ProgrammingSubmission] };
        const response = new HttpErrorResponse({ status: 403 });
        buildlogsStub.mockReturnValue(throwError(() => response));

        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(55, 89);
        expect(comp.loadingFailed).toBeFalse();
        expect(comp.isLoading).toBeFalse();
    });

    it('fetchBuildLogs should not suppress errors with status other than 403', () => {
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.participation = { ...comp.participation, submissions: [{ buildFailed: true } as ProgrammingSubmission] };
        const response = new HttpErrorResponse({ status: 500 });
        buildlogsStub.mockReturnValue(throwError(() => response));
        comp.ngOnInit();

        expect(buildlogsStub).toHaveBeenCalledOnce();
        expect(buildlogsStub).toHaveBeenCalledWith(55, 89);
        expect(comp.loadingFailed).toBeTrue();
        expect(comp.isLoading).toBeFalse();
    });

    it('should not show test details to students', () => {
        const createSpy = jest.spyOn(feedbackItemService, 'create');
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;

        comp.ngOnInit();

        expect(createSpy).toHaveBeenCalledWith(feedbacks, false);
    });

    it('should show test details to tutors', () => {
        const createSpy = jest.spyOn(feedbackItemService, 'create');
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;

        exercise.isAtLeastTutor = true;
        comp.exercise = exercise;

        comp.ngOnInit();

        expect(createSpy).toHaveBeenCalledWith(feedbacks, true);
    });

    it('should show test details to students for programming exercises with show test names on', () => {
        const createSpy = jest.spyOn(feedbackItemService, 'create');
        const { feedbacks } = generateFeedbacksAndExpectedItems();
        comp.exerciseType = ExerciseType.PROGRAMMING;
        comp.result.feedbacks = feedbacks;

        exercise.showTestNamesToStudents = true;
        comp.exercise = exercise;

        comp.ngOnInit();

        expect(createSpy).toHaveBeenCalledWith(feedbacks, true);
    });

    it('should expand feedback when being printed', () => {
        // @ts-ignore method is private
        const expandFeedbackItemGroupsSpy = jest.spyOn(comp, 'expandFeedbackItemGroups');

        const feedbackItem = generateManualFeedbackPair(true, 'Positive', 'This is good', 4).item;
        const feedbackItem1 = generateManualFeedbackPair(true, 'Positive', 'This is good', 4).item;

        const feedbackGroup: FeedbackGroup = {
            ...feedbackItem,
            members: [feedbackItem1],
            open: false,
        } as unknown as FeedbackGroup;
        comp.feedbackItemNodes = [feedbackGroup];

        // start printing => expand feedback
        const previousValue = undefined;
        const currentValue = true;
        const firstChange = false;
        const startPrinting = new SimpleChange(previousValue, currentValue, firstChange);
        comp.ngOnChanges({ isPrinting: startPrinting });

        expect(expandFeedbackItemGroupsSpy).toHaveBeenCalledOnce();
        expect(feedbackGroup.open).toBeTrue();

        // stop printing => collapse feedback (as it was collapsed before)
        const stopPrinting = new SimpleChange(true, false, false);
        comp.ngOnChanges({ isPrinting: stopPrinting });

        expect(expandFeedbackItemGroupsSpy).toHaveBeenCalledOnce(); // should not have been called again

        /**
         * references were removed during saving old state => cannot use {@link feedbackGroup} for comparison anymore
         */
        expect((comp.feedbackItemNodes[0] as unknown as FeedbackGroup).open).toBeFalse();
    });
});
