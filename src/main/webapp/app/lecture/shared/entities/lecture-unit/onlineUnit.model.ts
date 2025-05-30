import { LectureUnit, LectureUnitType } from 'app/lecture/shared/entities/lecture-unit/lectureUnit.model';

export class OnlineUnit extends LectureUnit {
    public description?: string;
    public source?: string;

    constructor() {
        super(LectureUnitType.ONLINE);
    }
}
