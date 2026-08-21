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

    /**
     * Android reports no "permanently denied" state:
     * `shouldShowRequestPermissionRationale` is false both before the first denial
     * and after the system stops asking. Only a memory of the denial in between
     * separates them, so it has to outlive the process.
     */
    private var hasDeniedCamera = false

    private var isCameraGranted = false
    private var canShowCameraRationale = false

    /**
     * What to open once the permission arrives. Cleared whenever it does, or the
     * next resume would send the user straight back into the camera they just
     * left.
     */
    private var pendingExercise: Exercise? = null

    init {
        viewModelScope.launch {
            hasDeniedCamera = cameraPermission.hasBeenDenied()
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
        if (isCameraGranted) {
            openWorkout(exercise)
            return
        }
        pendingExercise = exercise
        if (!canShowCameraRationale && hasDeniedCamera) {
            state.update { it.copy(isCameraBlockedDialogVisible = true) }
        } else {
            eventChannel.send(ExercisePickerEvent.RequestCameraPermission)
        }
    }

    private fun onScreenResumed(action: ExercisePickerAction.ScreenResumed) {
        isCameraGranted = action.isCameraGranted
        observeRationale(action.canShowCameraRationale)

        // Set only by a request this screen made, so this is the return from
        // Settings rather than any other way back to the picker.
        val exercise = pendingExercise ?: return
        if (isCameraGranted) openWorkout(exercise)
    }

    private fun onPermissionAnswered(action: ExercisePickerAction.PermissionAnswered) {
        isCameraGranted = action.isGranted
        observeRationale(action.canShowRationale)

        val exercise = pendingExercise
        pendingExercise = null
        if (action.isGranted && exercise != null) openWorkout(exercise)
    }

    /**
     * True is the only informative reading, so false decides nothing: it means
     * either that nothing has been denied yet or that the system has stopped
     * asking, and those are told apart by the flag rather than by this.
     *
     * Not awaited. The flag is read again only after the system stops offering
     * its dialog, which takes a second denial, so a process death here costs one
     * tap that shows a dialog the user can dismiss.
     */
    private fun observeRationale(canShow: Boolean) {
        canShowCameraRationale = canShow
        if (!canShow || hasDeniedCamera) return
        hasDeniedCamera = true
        viewModelScope.launch { cameraPermission.markDenied() }
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
