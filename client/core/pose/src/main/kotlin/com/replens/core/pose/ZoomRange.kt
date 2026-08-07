package com.replens.core.pose

/** [min] below 1 means the device has an ultra-wide lens. */
data class ZoomRange(val min: Float, val max: Float)

/**
 * Framing a whole body is a zoom-*out* problem, so the widest lens is the one
 * that matters; 2x earns its place only in a room big enough that the body is
 * small in frame. Past that you would crop the user out of their own workout.
 */
val ZoomRange.stops: List<Float>
    get() = buildList {
        if (min < 1f) add(min)
        add(1f)
        if (max >= 2f) add(2f)
    }
