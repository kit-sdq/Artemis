import { of } from 'rxjs';
import { fileUploadExercise } from './mock-file-upload-exercise.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

export const fileUploadParticipation = new StudentParticipation();
fileUploadParticipation.exercise = fileUploadExercise;
fileUploadParticipation.id = 1;

export const createFileUploadSubmission = () => {
    const fileUploadSubmission = new FileUploadSubmission();
    fileUploadSubmission.submitted = false;
    fileUploadSubmission.participation = fileUploadParticipation;
    fileUploadSubmission.id = 1;
    return fileUploadSubmission;
};

export class MockFileUploadSubmissionService {
    getDataForFileUploadEditor = (participationId: number) => of(createFileUploadSubmission());
    update = (fileUploadSubmission: FileUploadSubmission, exerciseId: number) => {
        fileUploadSubmission.results = [new Result()];
        return of({ body: fileUploadSubmission });
    };
}
