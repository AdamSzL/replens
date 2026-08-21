package com.replens.feature.workout.ui.picker

import com.replens.core.model.Exercise

internal sealed interface ExercisePickerEvent {

    data class NavigateToWorkout(val exercise: Exercise) : ExercisePickerEvent

    data object RequestCameraPermission : ExercisePickerEvent

    data object OpenAppSettings : ExercisePickerEvent
}
