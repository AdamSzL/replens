package com.replens.feature.workout.ui

import com.replens.core.exercise.SessionState
import com.replens.core.exercise.SetupCheck
import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.model.AbandonedDescent
import com.replens.core.model.Rep
import com.replens.core.model.RepPhase
import com.replens.core.model.RepUpdate
import com.replens.core.text.UiText
import com.replens.feature.workout.R
import com.replens.feature.workout.ui.mapper.message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

private val config = SquatRepConfig()

private const val COOLDOWN_MILLIS = 10_000L

private val GO_DEEPER = UiText.Resource(R.string.workout_cue_go_deeper)
private val ALL_THE_WAY_DOWN = UiText.Resource(R.string.workout_cue_all_the_way_down)

private fun count(repCount: Int) = UiText.Resource(R.string.workout_cue_rep, repCount)

/** Most frames: the counter is watching, nothing has happened. */
private val nothing = RepUpdate(RepPhase.DESCENDING)

private fun completed(deepestAngle: Float): RepUpdate {
    return RepUpdate(
        phase = RepPhase.STANDING,
        completedRep = Rep(
            index = 1,
            deepestAngle = deepestAngle,
            descent = 500.milliseconds,
            ascent = 500.milliseconds,
        ),
    )
}

private val deepRep = completed(deepestAngle = 70f)
private val shallowRep = completed(deepestAngle = config.bottomEnterAngle)

private val bailedOut = RepUpdate(
    phase = RepPhase.STANDING,
    abandonedDescent = AbandonedDescent(deepestAngle = 140f),
)

class CueEngineTest {

    private val engine = CueEngine(config)

    private fun active(
        repUpdate: RepUpdate,
        repCount: Int,
        timestampMillis: Long,
    ): UiText? = engine.onFrame(
        session = SessionState.Active,
        repUpdate = repUpdate,
        repCount = repCount,
        repsAtDepth = 0,
        timestampMillis = timestampMillis,
    )

    @Test
    fun `a clean rep is counted out loud`() {
        assertEquals(count(1), active(deepRep, repCount = 1, timestampMillis = 0))
    }

    /**
     * The reason a correction is held rather than fired once. The fault lands on a
     * single frame but the rep number is true on every frame until the next rep, so
     * releasing the correction immediately would let "eight" follow "go deeper" one
     * frame later and QUEUE_FLUSH would cut the correction off mid-word.
     */
    @Test
    fun `a correction stands in for the number of the rep it belongs to`() {
        assertEquals(GO_DEEPER, active(shallowRep, repCount = 8, timestampMillis = 0))

        // Not merely "nothing new" — specifically not the rep number.
        assertNull(active(nothing, repCount = 8, timestampMillis = 33))
        assertNull(active(nothing, repCount = 8, timestampMillis = 2_000))
    }

    @Test
    fun `the next rep gets its number back`() {
        active(shallowRep, repCount = 8, timestampMillis = 0)

        assertEquals(count(9), active(deepRep, repCount = 9, timestampMillis = 2_500))
    }

    /**
     * The failure this guards is silence, not noise. Every rep of a shallow set
     * faults, and a held correction suppresses the number of the rep it belongs to —
     * so without the cooldown the app would say "go deeper" once and then never
     * speak again, because every subsequent number would be suppressed too.
     */
    @Test
    fun `a shallow rep inside the cooldown keeps its number`() {
        active(shallowRep, repCount = 8, timestampMillis = 0)

        assertEquals(count(9), active(shallowRep, repCount = 9, timestampMillis = 2_500))
        assertEquals(count(10), active(shallowRep, repCount = 10, timestampMillis = 5_000))
    }

    /**
     * And the numbers in between are what make it audible again: the announcer
     * speaks on inequality, so the run of counts is what stops the returning
     * correction from being swallowed as something already said.
     */
    @Test
    fun `the correction comes back once the cooldown passes`() {
        active(shallowRep, repCount = 8, timestampMillis = 0)
        active(shallowRep, repCount = 9, timestampMillis = 2_500)

        assertEquals(
            GO_DEEPER,
            active(shallowRep, repCount = 12, timestampMillis = COOLDOWN_MILLIS),
        )
    }

    /**
     * An abandoned descent counts nothing, so it pins the count it interrupted.
     * That is what stops the previous rep's number being announced into the silence
     * behind the correction — the session cue at that count is still "five".
     */
    @Test
    fun `an abandoned descent holds against an unchanged count`() {
        active(deepRep, repCount = 5, timestampMillis = 0)

        assertEquals(ALL_THE_WAY_DOWN, active(bailedOut, repCount = 5, timestampMillis = 4_000))
        assertNull(active(nothing, repCount = 5, timestampMillis = 4_033))
        assertEquals(count(6), active(deepRep, repCount = 6, timestampMillis = 8_000))
    }

    /** A different fault is worth hearing, but not at the cost of the one just given. */
    @Test
    fun `a second kind of fault still waits for the cooldown`() {
        active(shallowRep, repCount = 8, timestampMillis = 0)
        active(deepRep, repCount = 9, timestampMillis = 2_500)

        // Silence rather than ALL_THE_WAY_DOWN: nine was announced a moment ago, so
        // the fall-through to the rep number has nothing new to say either.
        assertNull(active(bailedOut, repCount = 9, timestampMillis = 4_000))
    }

    /**
     * The camera restarting on a new time base, as after a flip. Coaching again is
     * recoverable; staying quiet until the clock catches up is not, and the clock
     * may never catch up.
     */
    @Test
    fun `a backwards timestamp does not lock the correction out`() {
        active(shallowRep, repCount = 8, timestampMillis = 900_000)

        assertEquals(ALL_THE_WAY_DOWN, active(bailedOut, repCount = 8, timestampMillis = 40))
    }

    /**
     * The whole reason corrections are read only while counting: the fault is still
     * held at this point, and the summary is the one line a user walks over to hear.
     */
    @Test
    fun `a held correction does not replace the set summary`() {
        active(shallowRep, repCount = 8, timestampMillis = 0)

        val summary = engine.onFrame(
            session = SessionState.Finished,
            repUpdate = null,
            repCount = 8,
            repsAtDepth = 3,
            timestampMillis = 1_000,
        )

        assertEquals(
            UiText.Plural(
                id = R.plurals.workout_cue_set_summary,
                quantity = 8,
                args = listOf(8, 3),
            ),
            summary,
        )
    }

    @Test
    fun `setup guidance is spoken when no set is running`() {
        val cue = engine.onFrame(
            session = SessionState.Waiting(SetupCheck.TOO_CLOSE),
            repUpdate = null,
            repCount = 0,
            repsAtDepth = 0,
            timestampMillis = 0,
        )

        assertEquals(SetupCheck.TOO_CLOSE.message, cue)
    }

    /**
     * Covers both halves of the reset: the hold is dropped, and the announcer has
     * forgotten it ever said this — without which the first correction of the new
     * set would be swallowed as a repeat of the last set's.
     */
    @Test
    fun `a new set corrects again from scratch`() {
        active(shallowRep, repCount = 8, timestampMillis = 0)
        engine.reset()

        assertEquals(GO_DEEPER, active(shallowRep, repCount = 1, timestampMillis = 2_000))
    }
}
