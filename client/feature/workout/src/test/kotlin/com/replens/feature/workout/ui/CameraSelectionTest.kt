package com.replens.feature.workout.ui

import com.replens.core.pose.CameraFacing
import com.replens.core.pose.ZoomRange
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

    @Test
    fun `an ultra-wide lens is offered as a stop`() {
        assertEquals(listOf(0.7f, 1f), ZoomRange(min = 0.7f, max = 1.9f).stops)
    }

    @Test
    fun `a camera without ultra-wide starts at 1x`() {
        assertEquals(listOf(1f), ZoomRange(min = 1f, max = 1.5f).stops)
    }

    @Test
    fun `2x is offered only when the camera reaches it`() {
        assertEquals(listOf(0.5f, 1f, 2f), ZoomRange(min = 0.5f, max = 10f).stops)
    }

    @Test
    fun `a fixed-zoom camera leaves nothing to choose`() {
        assertEquals(listOf(1f), ZoomRange(min = 1f, max = 1f).stops)
    }
}
