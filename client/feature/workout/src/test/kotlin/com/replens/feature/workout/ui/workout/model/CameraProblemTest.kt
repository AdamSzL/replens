package com.replens.feature.workout.ui.workout.model

import com.replens.core.pose.CameraAvailability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraProblemTest {

    @Test
    fun `a working camera is never a problem`() {
        assertNull(cameraProblem(CameraAvailability.Ready, isCameraGranted = true))
        assertNull(cameraProblem(CameraAvailability.Ready, isCameraGranted = false))
        assertNull(cameraProblem(CameraAvailability.Ready, isCameraGranted = null))
    }

    @Test
    fun `a waiting camera without the permission is the permission`() {
        val problem = cameraProblem(CameraAvailability.Unavailable, isCameraGranted = false)

        assertEquals(CameraProblem.PermissionMissing, problem)
    }

    @Test
    fun `a waiting camera with the permission is another app`() {
        val problem = cameraProblem(CameraAvailability.Unavailable, isCameraGranted = true)

        assertEquals(CameraProblem.InUse, problem)
    }

    /**
     * Which error a denial produces is device-specific — a Pixel 10 Pro XL reports
     * a critical `code=6` — so the permission is asked about first rather than
     * trusting CameraX to have classified it as merely waiting.
     */
    @Test
    fun `a missing permission outranks a critical error`() {
        val problem = cameraProblem(CameraAvailability.Failed, isCameraGranted = false)

        assertEquals(CameraProblem.PermissionMissing, problem)
    }

    @Test
    fun `a critical error with the permission held is broken`() {
        val problem = cameraProblem(CameraAvailability.Failed, isCameraGranted = true)

        assertEquals(CameraProblem.Broken, problem)
    }

    /** Settings fixes neither a disabled camera nor a dead service. */
    @Test
    fun `a critical error does not wait for the permission reading`() {
        val problem = cameraProblem(CameraAvailability.Failed, isCameraGranted = null)

        assertEquals(CameraProblem.Broken, problem)
    }

    /**
     * The camera binds on composition and the permission is read on resume, so a
     * failure can arrive first. Guessing would name a revoked permission "in use".
     */
    @Test
    fun `a waiting camera says nothing until the permission is read`() {
        assertNull(cameraProblem(CameraAvailability.Unavailable, isCameraGranted = null))
    }
}
