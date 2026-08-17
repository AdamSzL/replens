package com.replens.feature.history.ui.history

internal sealed interface HistoryEvent {

    data object NavigateBack : HistoryEvent

    data class NavigateToWorkout(val workoutId: Long) : HistoryEvent
}
