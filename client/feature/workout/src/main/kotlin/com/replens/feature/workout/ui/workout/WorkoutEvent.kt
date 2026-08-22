package com.replens.feature.workout.ui.workout

internal sealed interface WorkoutEvent {

    data class NavigateToSummary(val workoutId: Long) : WorkoutEvent

    data object OpenAppSettings : WorkoutEvent

    data object NavigateBack : WorkoutEvent
}
