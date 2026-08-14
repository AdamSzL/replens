package com.replens.feature.workout.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.replens.feature.workout.ui.WorkoutRoot
import com.replens.feature.workout.ui.summary.WorkoutSummaryRoot
import kotlinx.serialization.Serializable

@Serializable
data object WorkoutRoute : NavKey

@Serializable
data class WorkoutSummaryRoute(val workoutId: Long) : NavKey

fun EntryProviderScope<NavKey>.workoutEntries(backStack: NavBackStack<NavKey>) {
    entry<WorkoutRoute> {
        WorkoutRoot(
            navigateToSummary = { backStack.add(WorkoutSummaryRoute(it)) },
        )
    }
    entry<WorkoutSummaryRoute> { key ->
        WorkoutSummaryRoot(workoutId = key.workoutId)
    }
}
