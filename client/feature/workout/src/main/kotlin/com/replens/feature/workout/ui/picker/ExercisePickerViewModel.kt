package com.replens.feature.workout.ui.picker

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.model.Exercise
import com.replens.core.ui.EventChannel
import com.replens.feature.workout.domain.CameraPermissionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
internal class ExercisePickerViewModel @Inject constructor(
    private val cameraPermission: CameraPermissionRepository,
) : ViewModel() {

    val state: StateFlow<ExercisePickerState>
        field = MutableStateFlow(ExercisePickerState())

    private val eventChannel = EventChannel<ExercisePickerEvent>(viewModelScope)
    val events = eventChannel.events

    private val camera = CameraGate()

    /**
     * Cleared the moment it opens, or a later resume that finds the camera
     * granted would launch a workout nobody asked for on that visit.
     */
    private var pendingExercise: Exercise? = null

    init {
        viewModelScope.launch {
            camera.restore(cameraPermission.hasBeenDenied())
        }
    }

    fun onAction(action: ExercisePickerAction) {
        when (action) {
            is ExercisePickerAction.ExerciseClicked -> selectExercise(action.exercise)
            is ExercisePickerAction.ScreenResumed -> onScreenResumed(action)
            is ExercisePickerAction.PermissionAnswered -> onPermissionAnswered(action)
            ExercisePickerAction.OpenSettingsClicked -> openSettings()
            ExercisePickerAction.DialogDismissed -> dismissDialog()
        }
    }

    private fun selectExercise(exercise: Exercise) {
        pendingExercise = exercise
        when (camera.access) {
            CameraAccess.Granted -> openWorkout(exercise)
            CameraAccess.Requestable -> {
                eventChannel.send(ExercisePickerEvent.RequestCameraPermission)
            }
            CameraAccess.Blocked -> showBlockedDialog()
        }
    }

    private fun onScreenResumed(action: ExercisePickerAction.ScreenResumed) {
        record(
            isGranted = action.isCameraGranted,
            canShowRationale = action.canShowCameraRationale,
        )

        // No dialog here, unlike a permission result: the user is coming back
        // from the one screen that could have fixed this, and being told again
        // is nagging.
        val exercise = pendingExercise ?: return
        if (camera.access == CameraAccess.Granted) openWorkout(exercise) else pendingExercise = null
    }

    private fun onPermissionAnswered(action: ExercisePickerAction.PermissionAnswered) {
        record(
            isGranted = action.isGranted,
            canShowRationale = action.canShowRationale,
        )

        val exercise = pendingExercise ?: return
        when (camera.access) {
            CameraAccess.Granted -> openWorkout(exercise)
            // Otherwise the tap that asked for a workout ends in silence.
            CameraAccess.Blocked -> showBlockedDialog()
            // Denied, or backed out of. Either way the user said no to this one.
            CameraAccess.Requestable -> pendingExercise = null
        }
    }

    /** Not awaited: losing the write costs one tap, and every denial rewrites it. */
    private fun record(isGranted: Boolean, canShowRationale: Boolean) {
        val isNewDenial = camera.record(
            isGranted = isGranted,
            canShowRationale = canShowRationale,
        )
        if (isNewDenial) {
            viewModelScope.launch { cameraPermission.markDenied() }
        }
    }

    private fun showBlockedDialog() {
        state.update { it.copy(isCameraBlockedDialogVisible = true) }
    }

    private fun openSettings() {
        state.update { it.copy(isCameraBlockedDialogVisible = false) }
        eventChannel.send(ExercisePickerEvent.OpenAppSettings)
    }

    private fun dismissDialog() {
        pendingExercise = null
        state.update { it.copy(isCameraBlockedDialogVisible = false) }
    }

    private fun openWorkout(exercise: Exercise) {
        pendingExercise = null
        eventChannel.send(ExercisePickerEvent.NavigateToWorkout(exercise))
    }
}
