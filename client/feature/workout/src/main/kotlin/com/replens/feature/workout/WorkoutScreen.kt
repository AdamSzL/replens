package com.replens.feature.workout

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun WorkoutScreen(
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = viewModel(factory = WorkoutViewModel.Factory),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleOwner) {
        viewModel.startSession(lifecycleOwner)
    }

    WorkoutContent(
        uiState = uiState,
        surfaceRequest = surfaceRequest,
        modifier = modifier,
    )
}

@Composable
private fun WorkoutContent(
    uiState: WorkoutUiState,
    surfaceRequest: SurfaceRequest?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }
        uiState.poseFrame?.let { frame ->
            // Front camera: the viewfinder mirrors the preview, analysis frames are
            // not mirrored, so the overlay must mirror to line up.
            PoseOverlay(
                frame = frame,
                mirrored = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
