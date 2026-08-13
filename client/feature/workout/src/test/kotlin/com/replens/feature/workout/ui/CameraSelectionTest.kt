package com.replens.feature.workout.ui

import com.replens.core.pose.CameraFacing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CameraSelectionTest {

    @Test
    fun `front wins wherever it exists`() {
        assertEquals(CameraFacing.FRONT, setOf(CameraFacing.FRONT).preferred)
        assertEquals(
            CameraFacing.FRONT,
            setOf(CameraFacing.BACK, CameraFacing.FRONT).preferred,
        )
    }

    /**
     * The manifest asks for `camera.any` rather than `camera.front` precisely so
     * this device is allowed to install the app, which only means something if the
     * app then picks the lens it has.
     */
    @Test
    fun `a device without a front lens gets the one it has`() {
        assertEquals(CameraFacing.BACK, setOf(CameraFacing.BACK).preferred)
    }

    /**
     * The case with consequences. Null leaves the selection unset, so the facings
     * flow never emits and nothing is ever bound — where a fallback would hand
     * CameraX a lens the device does not have, which throws.
     */
    @Test
    fun `a device reporting no lenses selects nothing`() {
        assertNull(emptySet<CameraFacing>().preferred)
    }
}
