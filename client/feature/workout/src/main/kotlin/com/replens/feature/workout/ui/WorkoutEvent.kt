package com.replens.feature.workout.ui

internal sealed interface WorkoutEvent {

    data class NavigateToSummary(val workoutId: Long) : WorkoutEvent

    data object NavigateToHistory : WorkoutEvent
}
