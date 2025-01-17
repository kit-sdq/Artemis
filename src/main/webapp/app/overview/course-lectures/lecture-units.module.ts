import { NgModule } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisSharedPipesModule } from 'app/shared/pipes/shared-pipes.module';
import { ArtemisCourseExerciseRowModule } from 'app/overview/course-exercises/course-exercise-row.module';
import { ExerciseUnitComponent } from 'app/overview/course-lectures/exercise-unit/exercise-unit.component';
import { ArtemisMarkdownModule } from 'app/shared/markdown.module';

@NgModule({
    imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisSharedPipesModule, ArtemisCourseExerciseRowModule, ArtemisMarkdownModule, ExerciseUnitComponent],
    exports: [ExerciseUnitComponent],
})
export class ArtemisLectureUnitsModule {}
