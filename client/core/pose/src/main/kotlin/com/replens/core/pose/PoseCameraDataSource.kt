package com.replens.core.pose

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.replens.core.model.PoseFrame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Streams live poses from the camera. Collecting [poseFrames] starts the camera
 * and the detector; canceling the collection releases both. The preview surface
 * is published via [surfaceRequests] for the UI to render alongside.
 */
interface PoseCameraDataSource {

    val surfaceRequests: StateFlow<SurfaceRequest?>

    /** Null until the camera provider resolves. */
    val options: StateFlow<CameraOptions?>

    /**
     * [facings] is awaited *after* [options] first publishes, so a caller can
     * resolve a lens that exists before anything binds. A fake must keep that
     * order or it will describe a handshake the real camera does not perform.
     */
    fun poseFrames(
        lifecycleOwner: LifecycleOwner,
        facings: Flow<CameraFacing>,
        zoomRatios: Flow<Float>,
    ): Flow<PoseFrame>
}
