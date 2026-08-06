package com.replens.core.posemath

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val TOLERANCE = 0.001f

class GeometryTest {

    @Test
    fun `perpendicular rays measure 90 degrees`() {
        val angle = angleDegrees(Point(0f, 1f), Point(0f, 0f), Point(1f, 0f))
        assertEquals(90f, angle!!, TOLERANCE)
    }

    @Test
    fun `opposite rays measure 180 degrees`() {
        val angle = angleDegrees(Point(-1f, 0f), Point(0f, 0f), Point(1f, 0f))
        assertEquals(180f, angle!!, TOLERANCE)
    }

    @Test
    fun `identical rays measure 0 degrees`() {
        val angle = angleDegrees(Point(1f, 0f), Point(0f, 0f), Point(5f, 0f))
        assertEquals(0f, angle!!, TOLERANCE)
    }

    /** 3-4-5 triangle: the angle opposite the side of length 3 is 36.87 degrees. */
    @Test
    fun `three four five triangle has the known acute angle`() {
        val angle = angleDegrees(Point(0f, 0f), Point(4f, 0f), Point(0f, 3f))
        assertEquals(36.8699f, angle!!, TOLERANCE)
    }

    @Test
    fun `angle is unchanged by scaling the whole pose`() {
        val a = Point(3f, 7f)
        val vertex = Point(-2f, 1f)
        val b = Point(9f, -4f)
        val scale = 13.5f

        val original = angleDegrees(a, vertex, b)!!
        val scaled = angleDegrees(
            Point(a.x * scale, a.y * scale),
            Point(vertex.x * scale, vertex.y * scale),
            Point(b.x * scale, b.y * scale),
        )!!

        assertEquals(original, scaled, TOLERANCE)
    }

    @Test
    fun `angle is unchanged by translating the whole pose`() {
        val a = Point(3f, 7f)
        val vertex = Point(-2f, 1f)
        val b = Point(9f, -4f)
        val dx = 100f
        val dy = -250f

        val original = angleDegrees(a, vertex, b)!!
        val moved = angleDegrees(
            Point(a.x + dx, a.y + dy),
            Point(vertex.x + dx, vertex.y + dy),
            Point(b.x + dx, b.y + dy),
        )!!

        assertEquals(original, moved, TOLERANCE)
    }

    @Test
    fun `angle is null when a ray has zero length`() {
        val vertex = Point(5f, 5f)
        assertNull(angleDegrees(vertex, vertex, Point(1f, 0f)))
        assertNull(angleDegrees(Point(1f, 0f), vertex, vertex))
    }

    @Test
    fun `angle does not depend on the order of the outer points`() {
        val a = Point(3f, 7f)
        val vertex = Point(-2f, 1f)
        val b = Point(9f, -4f)

        assertEquals(angleDegrees(a, vertex, b)!!, angleDegrees(b, vertex, a)!!, TOLERANCE)
    }

    @Test
    fun `vertical segment leans zero degrees`() {
        // y grows downwards, so "up" means a smaller y.
        val angle = angleFromVerticalDegrees(from = Point(0f, 100f), to = Point(0f, 0f))
        assertEquals(0f, angle!!, TOLERANCE)
    }

    @Test
    fun `horizontal segment leans 90 degrees`() {
        val angle = angleFromVerticalDegrees(from = Point(0f, 0f), to = Point(50f, 0f))
        assertEquals(90f, angle!!, TOLERANCE)
    }

    @Test
    fun `inverted segment leans 180 degrees`() {
        val angle = angleFromVerticalDegrees(from = Point(0f, 0f), to = Point(0f, 100f))
        assertEquals(180f, angle!!, TOLERANCE)
    }

    @Test
    fun `lean is the same magnitude whichever way the segment tilts`() {
        val left = angleFromVerticalDegrees(Point(0f, 100f), Point(-30f, 0f))!!
        val right = angleFromVerticalDegrees(Point(0f, 100f), Point(30f, 0f))!!
        assertEquals(left, right, TOLERANCE)
    }

    @Test
    fun `lean is null for a zero length segment`() {
        assertNull(angleFromVerticalDegrees(Point(4f, 4f), Point(4f, 4f)))
    }

    @Test
    fun `deviation is the perpendicular distance to the line`() {
        val deviation = deviationFromLine(
            point = Point(0f, 5f),
            lineStart = Point(0f, 0f),
            lineEnd = Point(10f, 0f),
        )
        assertEquals(5f, deviation!!, TOLERANCE)
    }

    @Test
    fun `deviation is zero on the line and beyond its ends`() {
        val onSegment = deviationFromLine(Point(5f, 0f), Point(0f, 0f), Point(10f, 0f))
        val pastTheEnd = deviationFromLine(Point(999f, 0f), Point(0f, 0f), Point(10f, 0f))

        assertEquals(0f, onSegment!!, TOLERANCE)
        // The line is infinite, not a segment.
        assertEquals(0f, pastTheEnd!!, TOLERANCE)
    }

    @Test
    fun `deviation flips sign on opposite sides of the line`() {
        val above = deviationFromLine(Point(3f, -5f), Point(0f, 0f), Point(10f, 0f))!!
        val below = deviationFromLine(Point(3f, 5f), Point(0f, 0f), Point(10f, 0f))!!

        assertEquals(above, -below, TOLERANCE)
        assertTrue(above * below < 0f)
    }

    @Test
    fun `deviation flips sign when the line direction reverses`() {
        val forwards = deviationFromLine(Point(3f, 5f), Point(0f, 0f), Point(10f, 0f))!!
        val backwards = deviationFromLine(Point(3f, 5f), Point(10f, 0f), Point(0f, 0f))!!

        assertEquals(forwards, -backwards, TOLERANCE)
    }

    @Test
    fun `deviation is null for a zero length line`() {
        assertNull(deviationFromLine(Point(1f, 1f), Point(4f, 4f), Point(4f, 4f)))
    }

    @Test
    fun `midpoint is halfway between the two points`() {
        assertEquals(Point(5f, 10f), midpoint(Point(0f, 0f), Point(10f, 20f)))
        assertEquals(Point(0f, 0f), midpoint(Point(-3f, -8f), Point(3f, 8f)))
    }

    @Test
    fun `distance matches the three four five triangle`() {
        assertEquals(5f, distance(Point(0f, 0f), Point(3f, 4f)), TOLERANCE)
        assertEquals(0f, distance(Point(2f, 2f), Point(2f, 2f)), TOLERANCE)
    }
}
