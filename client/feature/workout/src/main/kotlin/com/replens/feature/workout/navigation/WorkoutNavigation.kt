package com.replens.feature.workout.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.replens.feature.workout.ui.WorkoutRoot
import kotlinx.serialization.Serializable

@Serializable
data object WorkoutRoute : NavKey

fun EntryProviderScope<NavKey>.workoutEntries(
    navigateToSummary: (workoutId: Long) -> Unit,
) {
    entry<WorkoutRoute> {
        WorkoutRoot(
            navigateToSummary = navigateToSummary,
        )
    }
}
