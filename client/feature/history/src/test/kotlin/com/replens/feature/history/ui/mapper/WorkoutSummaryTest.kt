package com.replens.feature.history.ui.mapper

import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.text.UiText
import com.replens.feature.history.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class WorkoutSummaryTest {

    private val now = EPOCH + 10.minutes

    @Test
    fun `totals sum across sets`() {
        val workout = workout(
            set(startedAt = EPOCH, angles = listOf(90f, 100f)),
            set(startedAt = EPOCH + 5.minutes, angles = listOf(80f, 80f, 120f)),
        )

        val totals = workout.toSummaryState(now).totals

        assertEquals(2, totals.setCount)
        assertEquals(5, totals.repCount)
        assertEquals(3, totals.repsAtDepth)
    }

    /**
     * The screen must not read as a completion certificate while another set can
     * still join this workout — and whether it can is the gap rule, not which
     * screen navigated here.
     */
    @Test
    fun `a workout whose last set is inside the gap is still open`() {
        val workout = workout(set(startedAt = EPOCH, angles = listOf(90f)))

        val justAfter = workout.toSummaryState(workout.endedAt + 59.minutes)
        val wellAfter = workout.toSummaryState(workout.endedAt + 61.minutes)

        assertTrue(justAfter.totals.isOpen)
        assertFalse(wellAfter.totals.isOpen)
    }

    @Test
    fun `the first set has no rest before it`() {
        val workout = workout(
            set(startedAt = EPOCH, angles = listOf(90f)),
            set(startedAt = EPOCH + 4.minutes, angles = listOf(90f)),
        )

        val rows = workout.toSummaryState(now).sets

        assertNull(rows[0].restBefore)
    }

    /** The gap between sets, not the gap between starts — sets have duration. */
    @Test
    fun `rest is measured from the previous set's end`() {
        val workout = workout(
            set(startedAt = EPOCH, duration = 60.seconds, angles = listOf(90f)),
            set(startedAt = EPOCH + 4.minutes, angles = listOf(90f)),
        )

        val rows = workout.toSummaryState(now).sets

        assertEquals(UiText.Resource(R.string.duration_minutes, 3), rows[1].restBefore)
    }

    @Test
    fun `sets are numbered from one in the order they happened`() {
        val workout = workout(
            set(id = 7, startedAt = EPOCH, angles = listOf(90f)),
            set(id = 9, startedAt = EPOCH + 4.minutes, angles = listOf(90f)),
        )

        val rows = workout.toSummaryState(now).sets

        assertEquals(listOf(1, 2), rows.map { it.index })
        assertEquals(listOf(7L, 9L), rows.map { it.id })
    }

    /**
     * The axis is the counter's, so two sessions can be compared by eye. Asserted
     * against a config that shares no number with the default, or this would pass
     * against angles that were hardcoded here.
     */
    @Test
    fun `the axis comes from the counter's thresholds`() {
        val config = SquatRepConfig(bottomEnterAngle = 110f, goodDepthAngle = 88f)
        val workout = workout(set(startedAt = EPOCH, angles = listOf(90f)))

        val chart = workout.toSummaryState(now, config).depthChart!!

        assertEquals(110f, chart.topAngle, 0f)
        assertEquals(88f, chart.thresholdAngle, 0f)
    }

    @Test
    fun `a shallow session keeps the standard floor`() {
        val workout = workout(set(startedAt = EPOCH, angles = listOf(112f, 105f)))

        val chart = workout.toSummaryState(now).depthChart!!

        assertEquals(75f, chart.floorAngle, 0f)
    }

    /**
     * A floor, not a clamp: reps below it extend the axis. Clamping would pile the
     * best reps of the session onto the bottom edge, which is exactly where the
     * chart has something to say.
     */
    @Test
    fun `a deeper rep than the floor extends the axis`() {
        val workout = workout(set(startedAt = EPOCH, angles = listOf(90f, 62f)))

        val chart = workout.toSummaryState(now).depthChart!!

        assertEquals(62f, chart.floorAngle, 0f)
    }

    @Test
    fun `reps stay grouped by the set they were done in`() {
        val workout = workout(
            set(startedAt = EPOCH, angles = listOf(90f, 92f)),
            set(startedAt = EPOCH + 4.minutes, angles = listOf(85f)),
        )

        val chart = workout.toSummaryState(now).depthChart!!

        assertEquals(listOf(listOf(90f, 92f), listOf(85f)), chart.sets.map { it.angles })
    }

    /**
     * Zero reps is a real outcome, not an empty state: the descents happened, and
     * they are the evidence per-user calibration reads. The row reports them; the
     * chart has nothing to plot.
     */
    @Test
    fun `a workout of only abandoned descents has no chart but keeps its row`() {
        val workout = workout(
            set(
                startedAt = EPOCH,
                abandonedCount = 2,
                deepestAbandonedAngle = 128f,
            ),
        )

        val state = workout.toSummaryState(now)

        assertNull(state.depthChart)
        assertEquals(2, state.sets.single().abandonedCount)
        assertEquals(0, state.totals.repCount)
    }

    /** An empty group would read as missing data rather than as a failed set. */
    @Test
    fun `a set with no reps is left out of the chart but not the rows`() {
        val workout = workout(
            set(startedAt = EPOCH, angles = listOf(90f)),
            set(startedAt = EPOCH + 4.minutes, abandonedCount = 1),
            set(startedAt = EPOCH + 8.minutes, angles = listOf(85f)),
        )

        val state = workout.toSummaryState(now)

        assertEquals(2, state.depthChart!!.sets.size)
        assertEquals(3, state.sets.size)
    }
}
