package com.replens.core.exercise

import kotlin.math.ceil
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Drives [SessionState] from button presses and a stream of [SetupCheck]s.
 *
 * **Frame-driven, not clock-driven**, the same way [squat.SquatRepCounter] is:
 * the count-in advances only on [onFrame], using the timestamps the frames
 * already carry. A `delay`-based countdown would run on a second clock, and would
 * happily reach zero and start a set while the camera was delivering nothing.
 *
 * Stateful and not thread-safe; one instance per screen.
 *
 * @param settleFor how long [SetupCheck.READY] must hold before counting in. A
 *   duration rather than a frame count, because the pipeline's frame rate varies
 *   with the device and "14 frames" would mean half a second on one phone and a
 *   full second on another.
 * @param maxFrameGap a silence longer than this breaks continuity. Readiness
 *   means *continuously* ready, but the machine only sees the frames it is given,
 *   so without this a stall between two ready frames would read as sustained
 *   readiness. [posemath.PoseSmoother] guards the same way for the same reason.
 */
class SetSession(
    private val countIn: Duration = 3.seconds,
    private val settleFor: Duration = 500.milliseconds,
    private val maxFrameGap: Duration = 500.milliseconds,
) {

    var state: SessionState = SessionState.Idle
        private set

    private var lastCheck: SetupCheck = SetupCheck.NO_POSE
    private var lastFrameMillis: Long? = null
    private var readySinceMillis: Long? = null
    private var countInStartedMillis: Long? = null

    /** From [SessionState.Idle], or again from [SessionState.Finished]. */
    fun start(): SessionState {
        if (state != SessionState.Idle && state != SessionState.Finished) return state
        clearProgress()
        return moveTo(SessionState.Waiting(lastCheck))
    }

    fun cancel(): SessionState {
        if (state !is SessionState.Waiting && state !is SessionState.CountingIn) return state
        clearProgress()
        return moveTo(SessionState.Idle)
    }

    fun finish(): SessionState {
        if (state != SessionState.Active) return state
        return moveTo(SessionState.Finished)
    }

    fun dismiss(): SessionState {
        if (state != SessionState.Finished) return state
        return moveTo(SessionState.Idle)
    }

    fun onFrame(check: SetupCheck, timestampMillis: Long): SessionState {
        val gap = lastFrameMillis?.let { (timestampMillis - it).milliseconds }
        lastFrameMillis = timestampMillis
        lastCheck = check
        if (gap != null && gap > maxFrameGap) clearProgress()

        return when (state) {
            // Frames never start or end a set; only the user does.
            SessionState.Idle, SessionState.Active, SessionState.Finished -> state
            is SessionState.Waiting -> waitForPosition(check, timestampMillis)
            is SessionState.CountingIn -> continueCountIn(check, timestampMillis)
        }
    }

    private fun waitForPosition(check: SetupCheck, timestampMillis: Long): SessionState {
        if (check != SetupCheck.READY) {
            readySinceMillis = null
            return moveTo(SessionState.Waiting(check))
        }
        val readySince = readySinceMillis ?: timestampMillis.also { readySinceMillis = it }
        if ((timestampMillis - readySince).milliseconds < settleFor) {
            return moveTo(SessionState.Waiting(check))
        }
        countInStartedMillis = timestampMillis
        return moveTo(SessionState.CountingIn(countIn.wholeSecondsUp()))
    }

    /**
     * One bad frame ends the count-in and it restarts from the top rather than
     * resuming: three seconds is short enough that resuming would mean starting a
     * set the user only half stood still for.
     */
    private fun continueCountIn(check: SetupCheck, timestampMillis: Long): SessionState {
        val startedAt = countInStartedMillis
        if (check != SetupCheck.READY || startedAt == null) {
            clearProgress()
            return moveTo(SessionState.Waiting(check))
        }
        val remaining = countIn - (timestampMillis - startedAt).milliseconds
        if (remaining <= Duration.ZERO) {
            clearProgress()
            return moveTo(SessionState.Active)
        }
        return moveTo(SessionState.CountingIn(remaining.wholeSecondsUp()))
    }

    private fun moveTo(next: SessionState): SessionState {
        state = next
        return next
    }

    private fun clearProgress() {
        readySinceMillis = null
        countInStartedMillis = null
    }
}

/** 3.0s and 2.1s both read "3": the number is what is left, not what has passed. */
private fun Duration.wholeSecondsUp(): Int = ceil(this / 1.seconds).toInt()
