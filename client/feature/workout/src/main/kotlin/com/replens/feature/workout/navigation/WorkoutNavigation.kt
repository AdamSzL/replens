package com.replens.feature.workout.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.replens.core.model.Exercise
import com.replens.core.ui.topLevelNavMetadata
import com.replens.feature.workout.ui.picker.ExercisePickerRoot
import com.replens.feature.workout.ui.workout.WorkoutRoot
import kotlinx.serialization.Serializable

@Serializable
data object ExercisePickerRoute : NavKey

@Serializable
data class WorkoutRoute(val exercise: Exercise) : NavKey

fun EntryProviderScope<NavKey>.workoutEntries(
    navigateToWorkout: (Exercise) -> Unit,
    navigateToSummary: (workoutId: Long) -> Unit,
) {
    entry<ExercisePickerRoute>(metadata = topLevelNavMetadata) {
        ExercisePickerRoot(navigateToWorkout = navigateToWorkout)
    }
    entry<WorkoutRoute> { key ->
        WorkoutRoot(
            exercise = key.exercise,
            navigateToSummary = navigateToSummary,
        )
    }
}
