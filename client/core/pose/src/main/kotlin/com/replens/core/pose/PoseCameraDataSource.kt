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
     *
     * [lifecycleOwners] is a flow because **the owner is not stable for the life
     * of the stream**. Under Navigation 3 each entry gets its own, destroyed when
     * its content leaves composition and replaced by a new instance when it comes
     * back — so a screen returning from elsewhere hands over a different owner,
     * and binding to the old one throws. Null means no screen is showing: the
     * camera is released, and the detector is kept for the return.
     */
    fun poseFrames(
        lifecycleOwners: Flow<LifecycleOwner?>,
        facings: Flow<CameraFacing>,
        zoomRatios: Flow<Float>,
    ): Flow<PoseFrame>
}
