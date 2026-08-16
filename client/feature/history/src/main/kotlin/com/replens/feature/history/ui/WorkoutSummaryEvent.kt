package com.replens.feature.history.ui

internal sealed interface WorkoutSummaryEvent {

    data object NavigateBack : WorkoutSummaryEvent
}
