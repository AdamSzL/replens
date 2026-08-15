package com.replens.feature.history.ui

internal sealed interface WorkoutSummaryAction {

    data object BackClicked : WorkoutSummaryAction
}
