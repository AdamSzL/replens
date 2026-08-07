package com.replens.feature.workout

sealed interface WorkoutAction {

    data class ZoomSelected(val ratio: Float) : WorkoutAction
}
