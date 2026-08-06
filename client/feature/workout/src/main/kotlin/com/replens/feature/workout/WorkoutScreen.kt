package com.replens.feature.workout

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.exercise.RepPhase
import com.replens.core.model.PoseFrame

/** Public only until Navigation 3 lands and `navigation/` calls it instead. */
@Composable
fun WorkoutRoot(
    modifier: Modifier = Modifier,
    viewModel: WorkoutViewModel = hiltViewModel(),
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequest.collectAsStateWithLifecycle()

    // No `by` on purpose: keeping the State box lets the overlay read the frame at
    // draw time. Unwrapping it here would recompose this screen 30 times a second.
    val poseFrame = viewModel.poseFrame.collectAsStateWithLifecycle()

    LaunchedEffect(lifecycleOwner) {
        viewModel.startSession(lifecycleOwner)
    }

    WorkoutScreen(
        state = state,
        surfaceRequest = surfaceRequest,
        poseFrame = { poseFrame.value },
        modifier = modifier,
    )
}

@Composable
private fun WorkoutScreen(
    state: WorkoutState,
    surfaceRequest: SurfaceRequest?,
    poseFrame: () -> PoseFrame?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Front camera: the viewfinder mirrors the preview, analysis frames are
        // not mirrored, so the overlay must mirror to line up.
        PoseOverlay(
            frame = poseFrame,
            mirrored = true,
            modifier = Modifier.fillMaxSize(),
        )
        RepCounter(
            repCount = state.repCount,
            phase = state.phase,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp),
        )
    }
}

@Composable
private fun RepCounter(
    repCount: Int,
    phase: RepPhase,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = repCount.toString(),
            style = MaterialTheme.typography.displayMedium,
            color = Color.White,
        )
        Text(
            text = phase.name,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White.copy(alpha = 0.7f),
        )
    }
}

@Preview
@Composable
private fun WorkoutScreenPreview() {
    RepLensTheme {
        WorkoutScreen(
            state = WorkoutState(repCount = 12, phase = RepPhase.BOTTOM),
            surfaceRequest = null,
            poseFrame = { null },
        )
    }
}
