package com.replens.feature.history.ui.summary

internal sealed interface WorkoutSummaryAction {

    data object BackClicked : WorkoutSummaryAction
}
