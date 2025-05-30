import { of } from 'rxjs';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

export class MockCourseExerciseService {
    startExercise = () => of({} as StudentParticipation);

    startPractice = () => of({} as StudentParticipation);

    resumeProgrammingExercise = () => of({} as StudentParticipation);

    findAllProgrammingExercisesForCourse = () => of([{ id: 456 } as ProgrammingExercise]);

    requestFeedback = () => of({});
}
