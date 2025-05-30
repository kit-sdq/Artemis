import { Injectable } from '@angular/core';
import { Feedback } from 'app/assessment/shared/entities/feedback.model';
import { BaseApiHttpService } from 'app/shared/service/base-api-http.service';

@Injectable({ providedIn: 'root' })
export class FeedbackService extends BaseApiHttpService {
    /**
     * Filters the feedback based on the filter input
     * Used e.g. when we want to show certain test cases viewed from the exercise description
     * @param feedbacks The full list of feedback
     * @param filter an array of test case ids that the feedback needs to contain in its testcase.id attribute.
     */
    public filterFeedback = (feedbacks: Feedback[], filter: number[]): Feedback[] => {
        if (!filter) {
            return [...feedbacks];
        }
        return feedbacks.filter((feedback) => feedback.testCase?.id && filter.includes(feedback.testCase.id));
    };

    public async getLongFeedbackText(feedbackId: number): Promise<string> {
        const url = `assessment/feedbacks/${feedbackId}/long-feedback`;
        return await this.get<string>(url, { responseType: 'text' });
    }
}
