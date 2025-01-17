import { NgModule } from '@angular/core';
import { ArtemisSharedCommonModule } from 'app/shared/shared-common.module';
import { ExerciseFeedbackSuggestionOptionsComponent } from 'app/exercises/shared/feedback-suggestion/exercise-feedback-suggestion-options.component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@NgModule({
    imports: [ArtemisSharedCommonModule, ArtemisSharedComponentModule, ExerciseFeedbackSuggestionOptionsComponent],
    exports: [ExerciseFeedbackSuggestionOptionsComponent],
})
export class ExerciseFeedbackSuggestionOptionsModule {}
