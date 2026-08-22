package com.replens.core.pose

import androidx.camera.core.CameraState
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraAvailabilityTest {

    private fun state(
        type: CameraState.Type,
        errorCode: Int? = null,
    ): CameraState {
        return CameraState.create(type, errorCode?.let { CameraState.StateError.create(it) })
    }

    @Test
    fun `an open camera is ready`() {
        assertEquals(CameraAvailability.Ready, state(CameraState.Type.OPEN).toAvailability())
    }

    /**
     * The camera is closed every time the screen is left, and the way back is a
     * fresh bind. Reading that as a failure would report a broken camera on exit.
     */
    @Test
    fun `an ordinary close is ready`() {
        assertEquals(CameraAvailability.Ready, state(CameraState.Type.CLOSED).toAvailability())
        assertEquals(CameraAvailability.Ready, state(CameraState.Type.CLOSING).toAvailability())
    }

    /**
     * CameraX holds the OPENING state while it retries and exposes the error it is
     * recovering from — so an error here is progress, and announcing it would
     * flash a failure at a camera that is about to work.
     */
    @Test
    fun `a recoverable error while opening is still ready`() {
        val opening = state(CameraState.Type.OPENING, CameraState.ERROR_CAMERA_IN_USE)

        assertEquals(CameraAvailability.Ready, opening.toAvailability())
    }

    /**
     * Where a revoked permission lands: camera2 has no permission error code, so
     * the SecurityException is retried until the attempts run out.
     */
    @Test
    fun `a camera that gave up opening is unavailable`() {
        val pending = state(CameraState.Type.PENDING_OPEN)

        assertEquals(CameraAvailability.Unavailable, pending.toAvailability())
    }

    @Test
    fun `a critical error is a failure whichever state reports it`() {
        val closing = state(CameraState.Type.CLOSING, CameraState.ERROR_CAMERA_FATAL_ERROR)
        val closed = state(CameraState.Type.CLOSED, CameraState.ERROR_DO_NOT_DISTURB_MODE_ENABLED)
        val open = state(CameraState.Type.OPEN, CameraState.ERROR_STREAM_CONFIG)

        assertEquals(CameraAvailability.Failed, closing.toAvailability())
        assertEquals(CameraAvailability.Failed, closed.toAvailability())
        assertEquals(CameraAvailability.Failed, open.toAvailability())
    }

    /**
     * The shape a revoked permission actually takes, measured on device:
     * `CLOSING(code=6)` then `PENDING_OPEN(code=6)`. The error describes the
     * attempt that failed; the type says the camera is still waiting, so reading
     * the error first would call a parked camera broken.
     */
    @Test
    fun `a critical error carried into pending open is still unavailable`() {
        val pending = state(CameraState.Type.PENDING_OPEN, CameraState.ERROR_CAMERA_FATAL_ERROR)

        assertEquals(CameraAvailability.Unavailable, pending.toAvailability())
    }
}
