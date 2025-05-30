import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { map } from 'rxjs/operators';
import { createRequestOption } from 'app/shared/util/request.util';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { Participation, ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { AccountService } from 'app/core/auth/account.service';
import { convertDateFromClient, convertDateFromServer } from 'app/shared/util/date.utils';
import dayjs from 'dayjs/esm';

export type EntityResponseType = HttpResponse<StudentParticipation>;
export type EntityArrayResponseType = HttpResponse<StudentParticipation[]>;

@Injectable({ providedIn: 'root' })
export class ParticipationService {
    private http = inject(HttpClient);
    private submissionService = inject(SubmissionService);
    private accountService = inject(AccountService);

    public resourceUrl = 'api/exercise/participations';

    update(exercise: Exercise, participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertParticipationForServer(participation, exercise);
        return this.http
            .put<StudentParticipation>(`api/exercise/exercises/${exercise.id}/participations`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    updateIndividualDueDates(exercise: Exercise, participations: StudentParticipation[]): Observable<EntityArrayResponseType> {
        const copies = participations.map((participation) => this.convertParticipationForServer(participation, exercise));
        return this.http
            .put<StudentParticipation[]>(`api/exercise/exercises/${exercise.id}/participations/update-individual-due-date`, copies, { observe: 'response' })
            .pipe(map((res: EntityArrayResponseType) => this.processParticipationEntityArrayResponseType(res)));
    }

    private convertParticipationForServer(participation: StudentParticipation, exercise: Exercise): StudentParticipation {
        // make sure participation and exercise are connected, because this is expected by the server
        participation.exercise = ExerciseService.convertExerciseFromClient(exercise);
        return this.convertParticipationDatesFromClient(participation);
    }

    find(participationId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`${this.resourceUrl}/${participationId}`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    /*
     * Finds one participation for the currently logged-in user for the given exercise in the given course
     */
    findParticipationForCurrentUser(exerciseId: number): Observable<EntityResponseType> {
        return this.http
            .get<StudentParticipation>(`api/exercise/exercises/${exerciseId}/participation`, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    /**
     * starts the student participation for the quiz exercise with the identifier quizExerciseId
     * @param quizExerciseId The unique identifier of the quiz exercise
     */
    startQuizParticipation(quizExerciseId: number): Observable<EntityResponseType> {
        return this.http
            .post<StudentParticipation>(`api/quiz/quiz-exercises/${quizExerciseId}/start-participation`, {}, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.processParticipationEntityResponseType(res)));
    }

    findAllParticipationsByExercise(exerciseId: number, withLatestResults = false): Observable<EntityArrayResponseType> {
        const options = createRequestOption({ withLatestResults });
        return this.http
            .get<StudentParticipation[]>(`api/exercise/exercises/${exerciseId}/participations`, {
                params: options,
                observe: 'response',
            })
            .pipe(map((res: EntityArrayResponseType) => this.processParticipationEntityArrayResponseType(res)));
    }

    delete(participationId: number, req?: any): Observable<HttpResponse<any>> {
        const options = createRequestOption(req);
        return this.http.delete<void>(`${this.resourceUrl}/${participationId}`, { params: options, observe: 'response' });
    }

    cleanupBuildPlan(participation: StudentParticipation): Observable<EntityResponseType> {
        const copy = this.convertParticipationDatesFromClient(participation);
        return this.http
            .put<StudentParticipation>(`${this.resourceUrl}/${participation.id}/cleanup-build-plan`, copy, { observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.convertParticipationResponseDatesFromServer(res)));
    }

    shouldPreferPractice(exercise?: Exercise): boolean {
        return !!exercise?.dueDate && dayjs().isAfter(exercise.dueDate);
    }

    getBuildJobIdsForResultsOfParticipation(participationId: number): Observable<{ [key: string]: string }> {
        return this.http.get<{ [key: string]: string }>(`api/assessment/participations/${participationId}/results/build-job-ids`);
    }

    protected convertParticipationDatesFromClient(participation: StudentParticipation): StudentParticipation {
        // return a copy of the object
        return Object.assign({}, participation, {
            initializationDate: convertDateFromClient(participation.initializationDate),
            individualDueDate: convertDateFromClient(participation.individualDueDate),
        });
    }

    protected convertParticipationResponseDatesFromServer(res: EntityResponseType): EntityResponseType {
        if (res.body) {
            ParticipationService.convertParticipationDatesFromServer(res.body);
            res.body.submissions = this.submissionService.convertSubmissionArrayDatesFromServer(res.body.submissions);
            res.body.exercise = ExerciseService.convertExerciseDatesFromServer(res.body.exercise);
        }
        return res;
    }

    protected convertParticipationResponseArrayDatesFromServer(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((participation: StudentParticipation) => {
                ParticipationService.convertParticipationDatesFromServer(participation);
            });
        }
        return res;
    }

    /**
     * Converts the dates that are part of the participation into a usable format.
     *
     * Does not convert dates in dependant attributes (e.g. results, submissions)!
     * @param participation for which the dates should be converted into the dayjs format.
     */
    public static convertParticipationDatesFromServer(participation?: Participation) {
        if (participation) {
            participation.initializationDate = convertDateFromServer(participation.initializationDate);
            participation.individualDueDate = convertDateFromServer(participation.individualDueDate);
            if (participation.exercise) {
                participation.exercise = ExerciseService.convertExerciseDatesFromServer(participation.exercise);
            }
        }
        return participation;
    }

    public static convertParticipationArrayDatesFromServer(participations?: StudentParticipation[]) {
        const convertedParticipations: StudentParticipation[] = [];
        if (participations?.length) {
            participations.forEach((participation: StudentParticipation) => {
                convertedParticipations.push(ParticipationService.convertParticipationDatesFromServer(participation)!);
            });
        }
        return convertedParticipations;
    }

    public mergeStudentParticipations(participations: StudentParticipation[]): StudentParticipation[] {
        const mergedParticipations: StudentParticipation[] = [];

        if (participations?.length) {
            const nonTestRunParticipations = participations.filter((participation: StudentParticipation) => !participation.testRun);
            const testRunParticipations = participations.filter((participation: StudentParticipation) => participation.testRun);

            if (participations[0].type === ParticipationType.STUDENT) {
                if (nonTestRunParticipations.length) {
                    const combinedParticipation = new StudentParticipation();
                    this.mergeResultsAndSubmissions(combinedParticipation, nonTestRunParticipations);
                    mergedParticipations.push(combinedParticipation);
                }
                if (testRunParticipations.length) {
                    const combinedParticipationTestRun = new StudentParticipation();
                    this.mergeResultsAndSubmissions(combinedParticipationTestRun, testRunParticipations);
                    mergedParticipations.push(combinedParticipationTestRun);
                }
            } else if (participations[0].type === ParticipationType.PROGRAMMING) {
                if (nonTestRunParticipations.length) {
                    const combinedParticipation = this.mergeProgrammingParticipations(nonTestRunParticipations as ProgrammingExerciseStudentParticipation[]);
                    mergedParticipations.push(combinedParticipation);
                }
                if (testRunParticipations.length) {
                    const combinedParticipationTestRun = this.mergeProgrammingParticipations(testRunParticipations as ProgrammingExerciseStudentParticipation[]);
                    mergedParticipations.push(combinedParticipationTestRun);
                }
            }
        }
        return mergedParticipations;
    }

    private mergeProgrammingParticipations(participations: ProgrammingExerciseStudentParticipation[]): ProgrammingExerciseStudentParticipation {
        const combinedParticipation = new ProgrammingExerciseStudentParticipation();
        if (participations?.length) {
            combinedParticipation.repositoryUri = participations[0].repositoryUri;
            combinedParticipation.buildPlanId = participations[0].buildPlanId;
            combinedParticipation.buildPlanUrl = participations[0].buildPlanUrl;
            this.mergeResultsAndSubmissions(combinedParticipation, participations);
        }
        return combinedParticipation;
    }

    private mergeResultsAndSubmissions(combinedParticipation: StudentParticipation, participations: StudentParticipation[]) {
        combinedParticipation.id = participations[0].id;
        combinedParticipation.initializationState = participations[0].initializationState;
        combinedParticipation.initializationDate = participations[0].initializationDate;
        combinedParticipation.individualDueDate = participations[0].individualDueDate;
        combinedParticipation.presentationScore = participations[0].presentationScore;
        combinedParticipation.exercise = participations[0].exercise;
        combinedParticipation.type = participations[0].type;
        combinedParticipation.testRun = participations[0].testRun;

        if (participations[0].student) {
            combinedParticipation.student = participations[0].student;
        }
        if (participations[0].team) {
            combinedParticipation.team = participations[0].team;
        }
        if (participations[0].participantIdentifier) {
            combinedParticipation.participantIdentifier = participations[0].participantIdentifier;
        }
        if (participations[0].participantName) {
            combinedParticipation.participantName = participations[0].participantName;
        }

        participations.forEach((participation) => {
            if (participation.submissions) {
                combinedParticipation.submissions = combinedParticipation.submissions
                    ? combinedParticipation.submissions.concat(participation.submissions)
                    : participation.submissions;
            }
        });

        // make sure that results and submissions are connected with the participation because some components need this
        if (combinedParticipation.submissions?.length) {
            combinedParticipation.submissions.forEach((submission) => {
                submission.participation = combinedParticipation;
            });
        }
    }

    public getSpecificStudentParticipation(studentParticipations: StudentParticipation[], testRun: boolean): StudentParticipation | undefined {
        return studentParticipations.filter((participation) => !!participation.testRun === testRun).first();
    }

    /**
     * This method bundles recurring conversion steps for Participation EntityArrayResponses.
     * @param participationRes
     */
    private processParticipationEntityArrayResponseType(participationRes: EntityArrayResponseType): EntityArrayResponseType {
        this.convertParticipationResponseArrayDatesFromServer(participationRes);
        this.setAccessRightsParticipationEntityArrayResponseType(participationRes);
        return participationRes;
    }

    /**
     * This method bundles recurring conversion steps for Participation EntityResponses.
     * @param participationRes
     */
    private processParticipationEntityResponseType(participationRes: EntityResponseType): EntityResponseType {
        this.convertParticipationResponseDatesFromServer(participationRes);
        this.setAccessRightsParticipationEntityResponseType(participationRes);
        return participationRes;
    }

    private setAccessRightsParticipationEntityResponseType(res: EntityResponseType): EntityResponseType {
        if (res.body?.exercise) {
            this.accountService.setAccessRightsForExercise(res.body.exercise);
        }
        return res;
    }

    private setAccessRightsParticipationEntityArrayResponseType(res: EntityArrayResponseType): EntityArrayResponseType {
        if (res.body) {
            res.body.forEach((participation) => {
                if (participation.exercise) {
                    this.accountService.setAccessRightsForExercise(participation.exercise);
                }
            });
        }
        return res;
    }
}
