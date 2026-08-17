package com.replens.feature.history.navigation

import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import com.replens.feature.history.ui.summary.WorkoutSummaryRoot
import kotlinx.serialization.Serializable

@Serializable
data class WorkoutSummaryRoute(val workoutId: Long) : NavKey

fun EntryProviderScope<NavKey>.historyEntries(
    onBack: () -> Unit,
) {
    entry<WorkoutSummaryRoute> { key ->
        WorkoutSummaryRoot(
            workoutId = key.workoutId,
            onBack = onBack,
        )
    }
}
