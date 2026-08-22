package com.replens.feature.workout.ui.workout

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.LifecycleOwner
import com.replens.core.model.PoseFrame
import com.replens.core.pose.CameraAvailability
import com.replens.core.pose.CameraFacing
import com.replens.core.pose.CameraOptions
import com.replens.core.pose.PoseCameraDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * Publishes [options] before anything asks for a facing, which is the handshake
 * the real data source performs — a fake that resolved them the other way round
 * would let a bug through that the camera would not.
 */
internal class FakePoseCameraDataSource : PoseCameraDataSource {

    override val surfaceRequests: StateFlow<SurfaceRequest?> = MutableStateFlow(null)

    val cameraOptions = MutableStateFlow<CameraOptions?>(
        CameraOptions(facings = setOf(CameraFacing.FRONT, CameraFacing.BACK)),
    )

    override val options: StateFlow<CameraOptions?> = cameraOptions

    val cameraAvailability = MutableStateFlow(CameraAvailability.Ready)

    override val availability: StateFlow<CameraAvailability> = cameraAvailability

    val frames = MutableSharedFlow<PoseFrame>(extraBufferCapacity = 256)

    /** The owner currently bound, so a test can see a rebind onto a new one. */
    var boundTo: LifecycleOwner? = null
        private set

    override fun poseFrames(
        lifecycleOwners: Flow<LifecycleOwner?>,
        facings: Flow<CameraFacing>,
        zoomRatios: Flow<Float>,
    ): Flow<PoseFrame> = channelFlow {
        launch { lifecycleOwners.collect { boundTo = it } }
        // Frames stop while unbound rather than the stream ending — modeled here
        // because that difference is the whole reason the owner is a flow.
        frames.collect { if (boundTo != null) send(it) }
    }
}
