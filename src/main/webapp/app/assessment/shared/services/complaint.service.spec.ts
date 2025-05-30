import { TestBed } from '@angular/core/testing';
import { ComplaintService } from 'app/assessment/shared/services/complaint.service';
import { Complaint, ComplaintType } from 'app/assessment/shared/entities/complaint.model';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import dayjs from 'dayjs/esm';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { ComplaintRequestDTO } from 'app/assessment/shared/entities/complaint-request-dto.model';
import { provideHttpClient } from '@angular/common/http';

describe('ComplaintService', () => {
    let complaintService: ComplaintService;
    let accountService: AccountService;
    let httpMock: HttpTestingController;

    const dayjsTime1 = dayjs().utc().year(2022).month(3).date(14).hour(10).minute(35).second(12).millisecond(332);
    const stringTime1 = '2022-04-14T10:35:12.332Z';
    const dayjsTime2 = dayjs().utc().year(2022).month(4).date(12).hour(18).minute(12).second(11).millisecond(140);
    const stringTime2 = '2022-05-12T18:12:11.140Z';
    const dayjsTime3 = dayjs();

    const clientComplaint1 = new Complaint();
    clientComplaint1.id = 42;
    clientComplaint1.complaintType = ComplaintType.MORE_FEEDBACK;
    clientComplaint1.result = new Result();
    clientComplaint1.submittedTime = dayjsTime1;
    clientComplaint1.complaintText = 'Test text';

    const serverComplaint1 = { ...clientComplaint1, submittedTime: stringTime1 };

    const clientComplaint2 = new Complaint();
    clientComplaint2.id = 42;
    clientComplaint2.complaintType = ComplaintType.MORE_FEEDBACK;
    clientComplaint2.result = new Result();
    clientComplaint2.submittedTime = dayjsTime2;
    clientComplaint2.complaintText = 'Test text';

    const serverComplaint2 = { ...clientComplaint2, submittedTime: stringTime2 };

    const clientComplaintReq = new ComplaintRequestDTO();
    clientComplaintReq.examId = 1337;
    clientComplaintReq.complaintText = 'Test text';
    clientComplaintReq.resultId = 1;
    clientComplaintReq.complaintType = ComplaintType.MORE_FEEDBACK;

    let exercise: Exercise;
    let emptyResult: Result;
    const course: Course = {
        maxComplaintTimeDays: 7,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        })
            .compileComponents()
            .then(() => {
                complaintService = TestBed.inject(ComplaintService);
                accountService = TestBed.inject(AccountService);
                httpMock = TestBed.inject(HttpTestingController);
            });

        exercise = {
            numberOfAssessmentsOfCorrectionRounds: [],
            secondCorrectionEnabled: false,
            studentAssignedTeamIdComputed: false,
        };
        emptyResult = new Result();
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('isComplaintLockedForLoggedInUser', () => {
        it('should be false if no visible lock is present', () => {
            const result = complaintService.isComplaintLockedForLoggedInUser(new Complaint(), new TextExercise(undefined, undefined));
            expect(result).toBeFalse();
        });

        it('should be false if user has the lock', () => {
            const login = 'testUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.reviewer = { login } as User;
            _complaint.complaintResponse.isCurrentlyLocked = true;
            accountService.userIdentity = { login } as User;

            const result = complaintService.isComplaintLockedForLoggedInUser(_complaint, new TextExercise(undefined, undefined));

            expect(result).toBeFalse();
        });

        it('should be true if user has not the lock', () => {
            const login = 'testUser';
            const anotherLogin = 'anotherTestUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.reviewer = { login } as User;
            _complaint.complaintResponse.isCurrentlyLocked = true;
            accountService.userIdentity = { login: anotherLogin } as User;
            jest.spyOn(accountService, 'isAtLeastInstructorForExercise').mockReturnValue(false);

            const result = complaintService.isComplaintLockedForLoggedInUser(_complaint, new TextExercise(undefined, undefined));

            expect(result).toBeTrue();
        });

        it('should be false if user is instructor', () => {
            const login = 'testUser';
            const anotherLogin = 'anotherTestUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.reviewer = { login } as User;
            _complaint.complaintResponse.isCurrentlyLocked = true;
            accountService.userIdentity = { login: anotherLogin } as User;
            jest.spyOn(accountService, 'isAtLeastInstructorForExercise').mockReturnValue(true);

            const result = complaintService.isComplaintLockedForLoggedInUser(_complaint, new TextExercise(undefined, undefined));

            expect(result).toBeFalse();
        });
    });

    describe('isComplaintLockedByLoggedInUser', () => {
        it('should be false if no visible lock is present', () => {
            const result = complaintService.isComplaintLockedByLoggedInUser(new Complaint());
            expect(result).toBeFalse();
        });

        it('should be false if the complaint is not locked', () => {
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBeFalse();
        });

        it('should be false if the complaint has been handled', () => {
            const _complaint = new Complaint();
            _complaint.accepted = true;
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;
            _complaint.complaintResponse.submittedTime = dayjs();

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBeFalse();
        });

        it('should be false if another user has the lock', () => {
            const login = 'testUser';
            const anotherLogin = 'anotherTestUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;
            _complaint.complaintResponse.reviewer = { login } as User;
            accountService.userIdentity = { login: anotherLogin } as User;

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBeFalse();
        });

        it('should be true if the same user has the lock', () => {
            const login = 'testUser';
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;
            _complaint.complaintResponse.reviewer = { login } as User;
            accountService.userIdentity = { login } as User;

            const result = complaintService.isComplaintLockedByLoggedInUser(_complaint);
            expect(result).toBeTrue();
        });
    });

    describe('isComplaintLocked', () => {
        it('should be false if no visible lock is present', () => {
            const result = complaintService.isComplaintLocked(new Complaint());
            expect(result).toBeFalse();
        });

        it('should be true if locked', () => {
            const _complaint = new Complaint();
            _complaint.complaintResponse = new ComplaintResponse();
            _complaint.complaintResponse.isCurrentlyLocked = true;

            const result = complaintService.isComplaintLocked(_complaint);
            expect(result).toBeTrue();
        });
    });

    describe('create', () => {
        it('For course', () => {
            clientComplaintReq.examId = undefined;
            complaintService.create(clientComplaintReq).subscribe((received) => {
                expect(received).toEqual(serverComplaint1);
            });

            const res = httpMock.expectOne({ method: 'POST' });
            expect(res.request.url).toBe('api/assessment/complaints');
            expect(res.request.body).toEqual(clientComplaintReq);

            res.flush(clone(serverComplaint1));
        });

        it('For exam', () => {
            complaintService.create(clientComplaintReq).subscribe((received) => {
                expect(received).toEqual(serverComplaint1);
            });

            const res = httpMock.expectOne({ method: 'POST' });
            expect(res.request.url).toBe(`api/assessment/complaints`);
            expect(res.request.body).toEqual(clientComplaintReq);

            res.flush(clone(serverComplaint1));
        });
    });

    describe('shouldHighlightComplaint', () => {
        it('should not highlight handled complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(1, 'hours'),
                accepted: true,
            } as Complaint;

            const result = complaintService.shouldHighlightComplaint(complaint);

            expect(result).toBeFalse();
        });

        it('should not highlight recent complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(8, 'days').add(1, 'seconds'),
            } as Complaint;

            const result = complaintService.shouldHighlightComplaint(complaint);

            expect(result).toBeFalse();
        });

        it('should highlight old complaints', () => {
            const complaint = {
                id: 42,
                submittedTime: dayjs().subtract(8, 'days'),
            } as Complaint;

            const result = complaintService.shouldHighlightComplaint(complaint);

            expect(result).toBeTrue();
        });
    });

    describe('getIndividualComplaintDueDate', () => {
        it('should return undefined for no results', () => {
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, undefined);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should return undefined for quiz exercise', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3;
            exercise.dueDate = undefined;
            exercise.type = ExerciseType.QUIZ;
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should calculate the correct complaint due date for no exercise due date', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3;
            exercise.dueDate = undefined;
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(7, 'days'));
        });

        it('should return undefined for automatic assessment without complaints', () => {
            emptyResult.rated = true;
            exercise.dueDate = dayjsTime3;
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should calculate the correct complaint due date for automatic assessment with complaints', () => {
            emptyResult.completionDate = dayjsTime3.subtract(1, 'day');
            exercise.allowComplaintsForAutomaticAssessments = true;
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            exercise.dueDate = dayjsTime3;

            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(7, 'days'));
        });

        it('should calculate the correct complaint due date for automatic assessment with complaints before dueDate', () => {
            emptyResult.completionDate = dayjsTime3.subtract(2, 'day');
            exercise.allowComplaintsForAutomaticAssessments = true;
            exercise.assessmentType = AssessmentType.AUTOMATIC;
            exercise.dueDate = dayjsTime3.subtract(1, 'day');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(6, 'days'));
        });

        it('should calculate the correct complaint due date after assessmentDueDate', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3.subtract(3, 'days');
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.dueDate = dayjsTime3.subtract(2, 'days');
            exercise.assessmentDueDate = dayjsTime3.subtract(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(6, 'days'));
        });

        it('should calculate the correct complaint due date after assessmentDueDate for late feedback', () => {
            emptyResult.rated = true;
            emptyResult.completionDate = dayjsTime3;
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.dueDate = dayjsTime3.subtract(2, 'days');
            exercise.assessmentDueDate = dayjsTime3.subtract(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toEqual(dayjsTime3.add(7, 'days'));
        });

        it('should return undefined for complaint due date before assessment dueDate', () => {
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.assessmentDueDate = dayjsTime3.add(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });

        it('should return undefined for complaint due date for unrated result after assessment dueDate', () => {
            emptyResult.rated = false;
            exercise.assessmentType = AssessmentType.MANUAL;
            exercise.assessmentDueDate = dayjsTime3.subtract(1, 'days');
            const individualComplaintDueDate = ComplaintService.getIndividualComplaintDueDate(exercise, course.maxComplaintTimeDays!, emptyResult);
            expect(individualComplaintDueDate).toBeUndefined();
        });
    });

    it('findBySubmissionId', () => {
        const submissionId = 1337;
        complaintService.findBySubmissionId(submissionId).subscribe((received) => {
            expect(received).toEqual(clientComplaint1);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`api/assessment/complaints?submissionId=${submissionId}`);

        res.flush(clone(serverComplaint1));
    });

    it('getComplaintsForTestRun', () => {
        const exerciseId = 1337;
        complaintService.getComplaintsForTestRun(exerciseId).subscribe((received) => {
            expect(received).toIncludeSameMembers([clientComplaint1, clientComplaint2]);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`api/assessment/complaints?exerciseId=${exerciseId}`);

        res.flush([clone(serverComplaint1), clone(serverComplaint2)]);
    });

    it('findAllByTutorIdForCourseId', () => {
        const tutorId = 1337;
        const courseId = 69;
        const complaintType = ComplaintType.COMPLAINT;

        complaintService.findAllByTutorIdForCourseId(tutorId, courseId, complaintType).subscribe((received) => {
            expect(received).toIncludeSameMembers([clientComplaint1, clientComplaint2]);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`api/assessment/complaints?courseId=${courseId}&complaintType=${complaintType}&tutorId=${tutorId}`);

        res.flush([clone(clientComplaint1), clone(clientComplaint2)]);
    });

    it('findAllByTutorIdForExerciseId', () => {
        const tutorId = 1337;
        const exerciseId = 69;
        const complaintType = ComplaintType.COMPLAINT;

        complaintService.findAllByTutorIdForExerciseId(tutorId, exerciseId, complaintType).subscribe((received) => {
            expect(received).toIncludeSameMembers([clientComplaint1, clientComplaint2]);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`api/assessment/complaints?exerciseId=${exerciseId}&complaintType=${complaintType}&tutorId=${tutorId}`);

        res.flush([clone(clientComplaint1), clone(clientComplaint2)]);
    });

    it('findAllByCourseId', () => {
        const courseId = 69;
        const complaintType = ComplaintType.COMPLAINT;

        complaintService.findAllByCourseId(courseId, complaintType).subscribe((received) => {
            expect(received).toIncludeSameMembers([clientComplaint1, clientComplaint2]);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`api/assessment/complaints?courseId=${courseId}&complaintType=${complaintType}`);

        res.flush([clone(clientComplaint1), clone(clientComplaint2)]);
    });

    it('findAllByCourseIdAndExamId', () => {
        const courseId = 69;
        const examId = 42;

        complaintService.findAllByCourseIdAndExamId(courseId, examId).subscribe((received) => {
            expect(received).toIncludeSameMembers([clientComplaint1, clientComplaint2]);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`api/assessment/complaints?courseId=${courseId}&examId=${examId}`);

        res.flush([clone(clientComplaint1), clone(clientComplaint2)]);
    });

    it('findAllByExerciseId', () => {
        const exerciseId = 69;
        const complaintType = ComplaintType.COMPLAINT;

        complaintService.findAllByExerciseId(exerciseId, complaintType).subscribe((received) => {
            expect(received).toIncludeSameMembers([clientComplaint1, clientComplaint2]);
        });

        const res = httpMock.expectOne({ method: 'GET' });
        expect(res.request.url).toBe(`api/assessment/complaints?exerciseId=${exerciseId}&complaintType=${complaintType}`);

        res.flush([clone(clientComplaint1), clone(clientComplaint2)]);
    });

    function clone(object: object): object {
        return Object.assign({}, object);
    }
});
