package com.replens.feature.workout.ui

internal sealed interface WorkoutAction {

    data class ZoomSelected(val ratio: Float) : WorkoutAction

    data object CameraFlipClicked : WorkoutAction

    data object StartClicked : WorkoutAction

    /** Backing out of [SessionState.Waiting] or [SessionState.CountingIn]. */
    data object CancelClicked : WorkoutAction

    data object FinishClicked : WorkoutAction

    data object DoneClicked : WorkoutAction

    data object GoAgainClicked : WorkoutAction
}
