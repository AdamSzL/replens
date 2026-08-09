package com.replens.feature.workout.ui

import androidx.camera.compose.CameraXViewfinder
import androidx.camera.core.SurfaceRequest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replens.core.designsystem.component.button.OverlayIconButton
import com.replens.core.designsystem.icon.RepLensIcons
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.exercise.RepPhase
import com.replens.core.exercise.SessionState
import com.replens.core.model.PoseFrame
import com.replens.core.pose.CameraFacing
import com.replens.core.pose.CameraOptions
import com.replens.core.pose.ZoomRange
import com.replens.core.pose.stops
import com.replens.feature.workout.R
import com.replens.feature.workout.ui.components.PoseOverlay
import com.replens.feature.workout.ui.components.RepCounter
import com.replens.feature.workout.ui.components.SessionControls
import com.replens.feature.workout.ui.components.ZoomControl

/** Public only until Navigation 3 lands and `navigation/` calls it instead. */
@Composable
fun WorkoutRoot(modifier: Modifier = Modifier) {
    val viewModel: WorkoutViewModel = hiltViewModel()
    val lifecycleOwner = LocalLifecycleOwner.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val surfaceRequest by viewModel.surfaceRequests.collectAsStateWithLifecycle()

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
        onAction = viewModel::onAction,
        modifier = modifier,
    )
}

@Composable
private fun WorkoutScreen(
    state: WorkoutState,
    surfaceRequest: SurfaceRequest?,
    poseFrame: () -> PoseFrame?,
    onAction: (WorkoutAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current

    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // The viewfinder mirrors the front preview but analysis frames are never
        // mirrored, so the overlay has to compensate — and stop when we flip.
        PoseOverlay(
            frame = poseFrame,
            mirrored = state.cameraFacing == CameraFacing.FRONT,
            modifier = Modifier.fillMaxSize(),
        )
        // The preview and overlay stay edge to edge; only the controls are inset,
        // or the system bars and a punch-hole cutout sit on top of them.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding(),
        ) {
            if (state.cameraOptions?.facings.orEmpty().size > 1) {
                OverlayIconButton(
                    icon = RepLensIcons.CameraSwitch,
                    contentDescription = stringResource(R.string.workout_flip_camera),
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                        onAction(WorkoutAction.CameraFlipClicked)
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(24.dp),
                )
            }
            if (state.session == SessionState.Active) {
                RepCounter(
                    repCount = state.repCount,
                    phase = state.phase,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(24.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Framing yourself is the point of the pre-set states, so the camera
                // controls stay until the set is over.
                val zoomStops = state.cameraOptions?.zoomRange?.stops.orEmpty()
                // One stop is nothing to choose between.
                if (zoomStops.size > 1 && state.session != SessionState.Finished) {
                    ZoomControl(
                        stops = zoomStops,
                        selected = state.zoomRatio,
                        onSelect = { onAction(WorkoutAction.ZoomSelected(it)) },
                    )
                }
                SessionControls(
                    session = state.session,
                    repCount = state.repCount,
                    repsAtDepth = state.repsAtDepth,
                    onAction = onAction,
                )
            }
        }
    }
}

@Preview
@Composable
private fun WorkoutScreenPreview() {
    RepLensTheme {
        WorkoutScreen(
            state = WorkoutState(
                session = SessionState.Active,
                repCount = 12,
                phase = RepPhase.BOTTOM,
                cameraFacing = CameraFacing.FRONT,
                cameraOptions = CameraOptions(
                    facings = setOf(CameraFacing.FRONT, CameraFacing.BACK),
                    zoomRange = ZoomRange(min = 0.5f, max = 10f),
                ),
            ),
            surfaceRequest = null,
            poseFrame = { null },
            onAction = {},
        )
    }
}
