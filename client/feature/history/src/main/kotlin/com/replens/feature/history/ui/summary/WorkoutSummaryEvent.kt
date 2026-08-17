package com.replens.feature.history.ui.summary

internal sealed interface WorkoutSummaryEvent {

    data object NavigateBack : WorkoutSummaryEvent
}
