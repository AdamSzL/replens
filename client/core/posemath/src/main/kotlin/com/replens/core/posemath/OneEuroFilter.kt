package com.replens.core.posemath

import kotlin.math.PI
import kotlin.math.abs

/**
 * One Euro filter (Casiez, Roussel & Vogel, CHI 2012) — an adaptive low-pass
 * filter for noisy signals sampled over time.
 *
 * A plain low-pass filter forces one choice for the whole signal: smooth enough
 * to kill jitter while standing still, and it lags badly during a fast descent;
 * responsive enough to track the descent, and the skeleton shimmers at rest.
 * This filter picks per sample instead — it raises its cutoff as the signal
 * speeds up, so it is heavily smoothed when slow and barely smoothed when fast.
 *
 * Not thread-safe, and stateful: one instance per independent signal.
 *
 * @param minCutoff cutoff frequency (Hz) at zero speed. Lower = less jitter at
 *   rest, more lag.
 * @param beta how strongly speed raises the cutoff. Higher = less lag when
 *   moving fast. Zero makes this an ordinary low-pass filter.
 * @param derivativeCutoff cutoff (Hz) for smoothing the speed estimate itself,
 *   which is otherwise noisy enough to make the adaptation jump around.
 */
class OneEuroFilter(
    private val minCutoff: Float = DEFAULT_MIN_CUTOFF,
    private val beta: Float = DEFAULT_BETA,
    private val derivativeCutoff: Float = DEFAULT_DERIVATIVE_CUTOFF,
) {
    private val smoothedValue = ExponentialSmoother()
    private val smoothedRate = ExponentialSmoother()
    private var lastTimestampMillis: Long? = null

    /**
     * Filters one sample. [timestampMillis] must come from a monotonic clock;
     * intervals need not be regular — the filter adapts to the actual gap, which
     * is why dropped camera frames don't distort it.
     */
    fun filter(value: Float, timestampMillis: Long): Float {
        val previousTimestamp = lastTimestampMillis
        val previousValue = smoothedValue.last
        lastTimestampMillis = timestampMillis

        // Nothing to compare against yet: the first sample is its own estimate.
        if (previousTimestamp == null || previousValue == null) {
            return smoothedValue.update(value, alpha = 1f)
        }

        val deltaSeconds = (timestampMillis - previousTimestamp) / 1000f
        // Two samples at the same instant carry no rate information.
        if (deltaSeconds <= 0f) return previousValue

        val rate = (value - previousValue) / deltaSeconds
        val estimatedRate = smoothedRate.update(rate, alpha(derivativeCutoff, deltaSeconds))
        val cutoff = minCutoff + beta * abs(estimatedRate)
        return smoothedValue.update(value, alpha(cutoff, deltaSeconds))
    }

    /**
     * Drops all state, so the next sample is passed through untouched. Use when
     * the signal is no longer continuous with what came before — the subject
     * left frame, or analysis was stopped and restarted.
     */
    fun reset() {
        smoothedValue.reset()
        smoothedRate.reset()
        lastTimestampMillis = null
    }

    companion object {
        /**
         * Starting points from the paper, **not yet tuned against real
         * footage**. Tune minCutoff first with beta at 0 until jitter at rest is
         * acceptable, then raise beta until fast reps stop lagging.
         */
        const val DEFAULT_MIN_CUTOFF = 1f
        const val DEFAULT_BETA = 0.5f
        const val DEFAULT_DERIVATIVE_CUTOFF = 1f
    }
}

/**
 * Smoothing factor for a first-order low-pass filter at [cutoffHz], given the
 * actual time step. Deriving it from the time step per sample is what makes the
 * filter frame-rate independent.
 */
private fun alpha(cutoffHz: Float, deltaSeconds: Float): Float {
    val timeConstant = 1f / (2f * PI.toFloat() * cutoffHz)
    return 1f / (1f + timeConstant / deltaSeconds)
}

/** `smoothed = alpha * value + (1 - alpha) * previous`, seeded by its first sample. */
private class ExponentialSmoother {
    var last: Float? = null
        private set

    fun update(value: Float, alpha: Float): Float {
        val previous = last
        val smoothed = if (previous == null) value else alpha * value + (1f - alpha) * previous
        last = smoothed
        return smoothed
    }

    fun reset() {
        last = null
    }
}
