package com.replens.app

import androidx.camera.compose.CameraXViewfinder
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.replens.core.model.PoseFrame
import com.replens.core.pose.PoseCameraDataSource

@Composable
fun WorkoutScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val dataSource = remember(context) { PoseCameraDataSource(context.applicationContext) }
    val surfaceRequest by dataSource.surfaceRequests.collectAsStateWithLifecycle()
    var poseFrame by remember { mutableStateOf<PoseFrame?>(null) }

    LaunchedEffect(dataSource, lifecycleOwner) {
        dataSource.poseFrames(lifecycleOwner).collect { poseFrame = it }
    }

    Box(modifier = modifier.fillMaxSize()) {
        surfaceRequest?.let { request ->
            CameraXViewfinder(
                surfaceRequest = request,
                modifier = Modifier.fillMaxSize(),
            )
        }
        poseFrame?.let { frame ->
            // Front camera: the viewfinder mirrors the preview, analysis frames are not
            // mirrored, so the overlay must mirror to line up.
            PoseOverlay(
                frame = frame,
                mirrored = true,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
