package com.replens.feature.history.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.replens.feature.history.ui.history.HistoryRoot
import com.replens.feature.history.ui.summary.WorkoutSummaryRoot
import kotlinx.serialization.Serializable

@Serializable
data object HistoryRoute : NavKey

@Serializable
data class WorkoutSummaryRoute(val workoutId: Long) : NavKey

fun EntryProviderScope<NavKey>.historyEntries(
    onBack: () -> Unit,
    onWorkoutClick: (workoutId: Long) -> Unit,
) {
    entry<HistoryRoute> {
        HistoryRoot(
            onBack = onBack,
            onWorkoutClick = onWorkoutClick,
        )
    }
    entry<WorkoutSummaryRoute> { key ->
        WorkoutSummaryRoot(
            workoutId = key.workoutId,
            onBack = onBack,
        )
    }
}
