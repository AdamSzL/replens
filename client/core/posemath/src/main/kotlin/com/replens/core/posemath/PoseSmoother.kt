package com.replens.core.posemath

import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType
import com.replens.core.model.PoseFrame

/**
 * Applies a [OneEuroFilter] to every landmark coordinate in a stream of frames.
 * Raw detector output jitters by several pixels frame to frame even when the
 * subject is still, which is enough to make angle thresholds chatter and the
 * skeleton shimmer.
 *
 * Stateful and not thread-safe — one instance per camera session.
 *
 * Coordinates are filtered in units of frame width rather than raw pixels, so
 * that [OneEuroFilter.beta] means the same thing regardless of the analysis
 * resolution the device happens to pick. Both axes use the same divisor, so
 * smoothing stays isotropic — dividing y by height instead would smooth vertical
 * jitter differently from horizontal.
 *
 * @param resetAfterGapMillis a gap longer than this is treated as a new signal
 *   rather than a very slow sample. Without it, someone stepping out of frame
 *   and back would be dragged across the screen from their old position.
 */
class PoseSmoother(
    private val minCutoff: Float = OneEuroFilter.DEFAULT_MIN_CUTOFF,
    private val beta: Float = OneEuroFilter.DEFAULT_BETA,
    private val derivativeCutoff: Float = OneEuroFilter.DEFAULT_DERIVATIVE_CUTOFF,
    private val resetAfterGapMillis: Long = DEFAULT_RESET_AFTER_GAP_MILLIS,
) {
    private val filters = mutableMapOf<LandmarkType, CoordinateFilters>()
    private var lastTimestampMillis: Long? = null

    fun smooth(frame: PoseFrame): PoseFrame {
        val previousTimestamp = lastTimestampMillis
        if (previousTimestamp != null &&
            frame.timestampMillis - previousTimestamp > resetAfterGapMillis
        ) {
            reset()
        }
        lastTimestampMillis = frame.timestampMillis

        // Guards against a malformed frame making every coordinate infinite.
        val scale = frame.sourceWidth.toFloat()
        if (scale <= 0f) return frame

        val smoothed = frame.pose.all.map { landmark ->
            val coordinates = filters.getOrPut(landmark.type) { newFilters() }
            landmark.copy(
                x = coordinates.x.filter(landmark.x / scale, frame.timestampMillis) * scale,
                y = coordinates.y.filter(landmark.y / scale, frame.timestampMillis) * scale,
            )
        }
        return frame.copy(pose = BodyPose(smoothed))
    }

    fun reset() {
        filters.clear()
        lastTimestampMillis = null
    }

    private fun newFilters(): CoordinateFilters {
        return CoordinateFilters(
            x = OneEuroFilter(minCutoff, beta, derivativeCutoff),
            y = OneEuroFilter(minCutoff, beta, derivativeCutoff),
        )
    }

    /**
     * z is passed through untouched — it is relative depth, far less accurate
     * than x/y, and nothing measures with it. So is [Landmark.inFrameLikelihood],
     * which is a confidence, not a position.
     */
    private class CoordinateFilters(val x: OneEuroFilter, val y: OneEuroFilter)

    companion object {
        const val DEFAULT_RESET_AFTER_GAP_MILLIS = 500L
    }
}
