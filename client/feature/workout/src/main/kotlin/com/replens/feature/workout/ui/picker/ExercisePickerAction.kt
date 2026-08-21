package com.replens.feature.workout.ui.picker

import com.replens.core.model.Exercise

internal sealed interface ExercisePickerAction {

    data class ExerciseClicked(val exercise: Exercise) : ExercisePickerAction

    /**
     * Carries what only a composable can observe, and fires on resume because
     * that is when both can have changed: the system permission dialog and the
     * Settings screen each pause this one.
     */
    data class ScreenResumed(
        val isCameraGranted: Boolean,
        val canShowCameraRationale: Boolean,
    ) : ExercisePickerAction

    /**
     * [canShowRationale] is read after the result, where true means Android has a
     * denial on record — the last moment it says so, since a second denial flips
     * it back to false.
     */
    data class PermissionAnswered(
        val isGranted: Boolean,
        val canShowRationale: Boolean,
    ) : ExercisePickerAction

    data object OpenSettingsClicked : ExercisePickerAction

    data object DialogDismissed : ExercisePickerAction
}
