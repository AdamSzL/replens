package com.replens.feature.workout.ui.picker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraGateTest {

    private val gate = CameraGate()

    @Test
    fun `a granted camera needs nothing`() {
        gate.record(isGranted = true, canShowRationale = false)

        assertEquals(CameraAccess.Granted, gate.access)
    }

    @Test
    fun `a camera never asked for is requestable`() {
        gate.record(isGranted = false, canShowRationale = false)

        assertEquals(CameraAccess.Requestable, gate.access)
    }

    /** The system still offers its own dialog, which beats anything we could draw. */
    @Test
    fun `a camera denied once is still requestable`() {
        gate.record(isGranted = false, canShowRationale = true)

        assertEquals(CameraAccess.Requestable, gate.access)
    }

    @Test
    fun `a denial the system will still ask about is worth persisting`() {
        assertTrue(gate.record(isGranted = false, canShowRationale = true))
    }

    /**
     * Backing out of the system dialog reads exactly like a denial that has
     * silenced it, and only the memory tells them apart.
     */
    @Test
    fun `a reading with no rationale teaches nothing`() {
        assertFalse(gate.record(isGranted = false, canShowRationale = false))
        assertEquals(CameraAccess.Requestable, gate.access)
    }

    @Test
    fun `a remembered denial and no rationale is blocked`() {
        gate.record(isGranted = false, canShowRationale = true)

        gate.record(isGranted = false, canShowRationale = false)

        assertEquals(CameraAccess.Blocked, gate.access)
    }

    @Test
    fun `a denial is persisted once`() {
        gate.record(isGranted = false, canShowRationale = true)

        assertFalse(gate.record(isGranted = false, canShowRationale = true))
    }

    @Test
    fun `a denial remembered from an earlier process blocks with no reading`() {
        gate.restore(wasDenied = true)

        assertEquals(CameraAccess.Blocked, gate.access)
    }

    /**
     * The read that supplies [CameraGate.restore] suspends, so a resume can record
     * a denial while it is still in flight. Assigning would erase it, leaving the
     * process convinced nothing was ever denied.
     */
    @Test
    fun `a denial recorded while the memory was loading survives it`() {
        gate.record(isGranted = false, canShowRationale = true)

        gate.restore(wasDenied = false)
        gate.record(isGranted = false, canShowRationale = false)

        assertEquals(CameraAccess.Blocked, gate.access)
    }

    /** A permission granted from Settings outranks anything remembered. */
    @Test
    fun `a remembered denial does not block a granted camera`() {
        gate.restore(wasDenied = true)

        gate.record(isGranted = true, canShowRationale = false)

        assertEquals(CameraAccess.Granted, gate.access)
    }
}
