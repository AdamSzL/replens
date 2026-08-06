package com.replens.feature.workout

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.replens.core.pose.PoseCameraDataSource
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Owns the camera/pose session for the duration of the screen, and turns the
 * frame stream into [WorkoutUiState].
 */
@HiltViewModel
class WorkoutViewModel @Inject constructor(
    private val poseCamera: PoseCameraDataSource,
) : ViewModel() {

    val surfaceRequest: StateFlow<SurfaceRequest?> = poseCamera.surfaceRequests

    val uiState: StateFlow<WorkoutUiState>
        field = MutableStateFlow(WorkoutUiState())

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
                uiState.update { it.copy(poseFrame = frame) }
            }
        }
    }
}
