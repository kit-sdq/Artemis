import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpClient, HttpParams } from '@angular/common/http';
import { ComplaintResponse } from 'app/assessment/shared/entities/complaint-response.model';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { EntityResponseType, ResultService } from 'app/exercise/result/result.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { map } from 'rxjs/operators';

@Injectable({ providedIn: 'root' })
export class ProgrammingAssessmentManualResultService {
    private http = inject(HttpClient);
    private resultService = inject(ResultService);

    private resourceUrl = 'api/programming';

    // TODO: It would be good to refactor the convertDate methods into a separate service, so that we don't have to import the result service here.

    /**
     * Saves a new manual result and stores it in the server
     * @param {number} participationId - Id of the participation
     * @param {Result} result - The result to be created and sent to the server
     * @param {submit} submit - Indicates whether submit or save is called
     */
    // TODO: make consistent with other *.assessment.service.ts file
    saveAssessment(participationId: number, result: Result, submit = false): Observable<EntityResponseType> {
        let params = new HttpParams();
        if (submit) {
            params = params.set('submit', 'true');
        }

        const url = `${this.resourceUrl}/participations/${participationId}/manual-results`;
        const copy = this.resultService.convertResultDatesFromClient(result);
        return this.http
            .put<Result>(url, copy, { params, observe: 'response' })
            .pipe(map((res: EntityResponseType) => this.resultService.convertResultResponseDatesFromServer(res)));
    }

    /**
     * Send the request to update the assessment after the complaint (only done once per complaint). The result score, string
     * and feedbacks will be updated. Original result will be stored as a string on complaint.
     * @param feedbacks list of feedback items (the score is not evaluated from them, as we pass score directly from the result)
     * @param complaintResponse contains main information about the complaint response (time, responseText, reviewer)
     * @param submissionId the id of the submission
     * @param assessmentNote the assessment note of the submission, if one exists
     * @return updated result with updated feedbacks and score
     */
    updateAfterComplaint(feedbacks: Feedback[], complaintResponse: ComplaintResponse, submissionId: number, assessmentNote?: string): Observable<Result> {
        const url = `${this.resourceUrl}/programming-submissions/${submissionId}/assessment-after-complaint`;
        const assessmentUpdate = {
            feedbacks,
            complaintResponse,
            assessmentNote,
        };
        return this.http.put<Result>(url, assessmentUpdate);
    }

    cancelAssessment(submissionId: number): Observable<void> {
        return this.http.put<void>(`${this.resourceUrl}/programming-submissions/${submissionId}/cancel-assessment`, null);
    }

    /**
     * Deletes an assessment.
     * @param participationId id of the participation, to which the assessment and the submission belong to
     * @param submissionId id of the submission, to which the assessment belongs to
     * @param resultId     id of the result which is deleted
     */
    deleteAssessment(participationId: number, submissionId: number, resultId: number): Observable<void> {
        return this.http.delete<void>(`${this.resourceUrl}/participations/${participationId}/programming-submissions/${submissionId}/results/${resultId}`);
    }
}
