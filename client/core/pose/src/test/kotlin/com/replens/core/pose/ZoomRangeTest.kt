package com.replens.core.pose

import org.junit.Assert.assertEquals
import org.junit.Test

class ZoomRangeTest {

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
