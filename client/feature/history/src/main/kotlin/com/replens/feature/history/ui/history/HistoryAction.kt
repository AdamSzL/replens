package com.replens.feature.history.ui.history

internal sealed interface HistoryAction {

    data class WorkoutClicked(val workoutId: Long) : HistoryAction
}
