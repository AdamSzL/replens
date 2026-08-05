package com.replens.feature.workout

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.replens.core.pose.PoseCameraDataSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Owns the camera/pose session for the duration of the screen, and turns the
 * frame stream into [WorkoutUiState].
 */
class WorkoutViewModel(
    private val poseCamera: PoseCameraDataSource,
) : ViewModel() {

    val surfaceRequest: StateFlow<SurfaceRequest?> = poseCamera.surfaceRequests

    private val _uiState = MutableStateFlow(WorkoutUiState())
    val uiState: StateFlow<WorkoutUiState> = _uiState.asStateFlow()

    private var session: Job? = null

    /**
     * Starts analysis, if it isn't running already. Takes a [LifecycleOwner]
     * because CameraX binds the camera to UI visibility — the session must stop
     * when the screen does, not when the ViewModel is cleared.
     */
    fun startSession(lifecycleOwner: LifecycleOwner) {
        if (session?.isActive == true) return
        session = viewModelScope.launch {
            poseCamera.poseFrames(lifecycleOwner).collect { frame ->
                _uiState.update { it.copy(poseFrame = frame) }
            }
        }
    }

    companion object {
        // Replaced by Hilt injection; the constructor shape stays the same.
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val application = checkNotNull(this[APPLICATION_KEY])
                WorkoutViewModel(PoseCameraDataSource(application))
            }
        }
    }
}
