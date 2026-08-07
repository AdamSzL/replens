package com.replens.feature.workout

internal sealed interface WorkoutAction {

    data class ZoomSelected(val ratio: Float) : WorkoutAction

    data object CameraFlipClicked : WorkoutAction
}
