import { of } from 'rxjs';
import { LectureUnitInformationDTO } from 'app/lecture/manage/lecture-units/attachment-video-units/attachment-video-units.component';

export class MockAttachmentVideoUnitsService {
    getSplitUnitsData = (lectureId: number, filename: string) => of({});

    createUnits = (lectureId: number, filename: string, lectureUnitInformation: LectureUnitInformationDTO) => of({});

    uploadSlidesForProcessing = (lectureId: number, file: File) => of({});

    getSlidesToRemove = (lectureId: number, filename: string, keyPhrases: string) => of({});
}
