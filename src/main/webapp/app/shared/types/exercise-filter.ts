import { ExerciseCategory } from 'app/exercise/shared/entities/exercise/exercise-category.model';
import { DifficultyLevel, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { SidebarData } from 'app/shared/types/sidebar';

/**
 * isDisplayed - whether the filter is in the filter modal (e.g. for no sidebar element the difficulty is defined, so the difficulty filter is not displayed)
 */
export type FilterOption<T> = { isDisplayed: boolean; options: T[] };
export type ExerciseCategoryFilterOption = { category: ExerciseCategory; searched: boolean };
export type ExerciseTypeFilterOption = { name: string; value: ExerciseType; checked: boolean; icon: IconProp };
export type DifficultyFilterOption = { name: string; value: DifficultyLevel; checked: boolean };

export type RangeFilter = {
    isDisplayed: boolean;
    filter: {
        generalMin: number;
        generalMax: number;
        selectedMin: number;
        selectedMax: number;
        step: number;
    };
};

export type ExerciseFilterOptions = {
    categoryFilter?: FilterOption<ExerciseCategoryFilterOption>;
    exerciseTypesFilter?: FilterOption<ExerciseTypeFilterOption>;
    difficultyFilter?: FilterOption<DifficultyFilterOption>;
    achievedScore?: RangeFilter;
    achievablePoints?: RangeFilter;
};

export type ExerciseFilterResults = { filteredSidebarData?: SidebarData; appliedExerciseFilters?: ExerciseFilterOptions; isFilterActive: boolean };

export type FilterDetails = {
    searchedTypes?: ExerciseType[];
    selectedCategories: ExerciseCategory[];
    searchedDifficulties?: DifficultyLevel[];
    isScoreFilterApplied: boolean;
    isPointsFilterApplied: boolean;
    achievedScore?: RangeFilter;
    achievablePoints?: RangeFilter;
};
