package com.replens.feature.history.ui.summary.mapper

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
     * The mixed case cannot be written yet — there is one exercise — so this pins
     * the half that is reachable, and the row will name it as soon as there are two.
     */
    @Test
    fun `a workout of one exercise does not name it on every set`() {
        val workout = workout(
            set(startedAt = EPOCH, angles = listOf(90f)),
            set(startedAt = EPOCH + 4.minutes, angles = listOf(90f)),
        )

        val rows = workout.toSummaryState(now).sets

        assertEquals(listOf(null, null), rows.map { it.exercise })
    }

    /** The line is the counter's, whatever the data does around it. */
    @Test
    fun `the threshold comes from the counter's config`() {
        val config = SquatRepConfig(goodDepthAngle = 88f)
        val workout = workout(set(startedAt = EPOCH, angles = listOf(90f)))

        val chart = workout.toSummaryState(now, config).depthChart!!

        assertEquals(88f, chart.thresholdAngle, 0f)
    }

    /**
     * The author's own reps land at 50-73 degrees, which is what killed the fixed
     * floor: it was dragged to the deepest rep anyway and left the plot empty.
     */
    @Test
    fun `the axis fits the session, padded and snapped`() {
        val workout = workout(set(startedAt = EPOCH, angles = listOf(50.8f, 73f)))

        val chart = workout.toSummaryState(now).depthChart!!

        assertEquals(45f, chart.floorAngle, 0f)
        assertEquals(100f, chart.topAngle, 0f)
    }

    /**
     * Snapping is the whole reason two of your own sessions still line up: an axis
     * fitted exactly would put the same rep at a different height every time. It
     * buys steps, not equality — sessions either side of a boundary still differ.
     */
    @Test
    fun `sessions inside the same step share an axis`() {
        val monday = workout(set(startedAt = EPOCH, angles = listOf(61f, 72f)))
        val tuesday = workout(set(startedAt = EPOCH, angles = listOf(62f, 73f)))

        val first = monday.toSummaryState(now).depthChart!!
        val second = tuesday.toSummaryState(now).depthChart!!

        assertEquals(first.floorAngle, second.floorAngle, 0f)
        assertEquals(first.topAngle, second.topAngle, 0f)
    }

    /**
     * Every rep shallower than the line: the floor has to drop below the threshold
     * so it is drawn where it is, rather than clamped onto the bottom edge.
     */
    @Test
    fun `a session shallower than the threshold keeps the line inside the axis`() {
        val workout = workout(set(startedAt = EPOCH, angles = listOf(112f, 105f)))

        val chart = workout.toSummaryState(now).depthChart!!

        assertEquals(120f, chart.topAngle, 0f)
        assertEquals(90f, chart.floorAngle, 0f)
        assertTrue(chart.floorAngle < chart.thresholdAngle)
    }

    /**
     * Positions rather than degrees, and nothing the totals card already said —
     * that card is announced first.
     */
    @Test
    fun `the chart describes itself by where the extremes happened`() {
        val workout = workout(
            set(startedAt = EPOCH, angles = listOf(88f, 91f)),
            set(startedAt = EPOCH + 4.minutes, angles = listOf(72f, 96f)),
        )

        val chart = workout.toSummaryState(now).depthChart!!

        val expected = UiText.Resource(R.string.workout_summary_chart_sets, 2, 2)
        assertEquals(expected, chart.description)
    }

    /** One set has no set to name, so the reps are what can be pointed at. */
    @Test
    fun `a single set is described by rep position`() {
        val workout = workout(set(startedAt = EPOCH, angles = listOf(88f, 72f, 91f)))

        val chart = workout.toSummaryState(now).depthChart!!

        val expected = UiText.Resource(R.string.workout_summary_chart_one_set, 2, 3)
        assertEquals(expected, chart.description)
    }

    /** One rep has no spread, so there is nothing to locate. */
    @Test
    fun `a single rep is described without extremes`() {
        val workout = workout(set(startedAt = EPOCH, angles = listOf(88f)))

        val chart = workout.toSummaryState(now).depthChart!!

        assertEquals(UiText.Resource(R.string.workout_summary_chart_single_rep), chart.description)
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
        // The second plotted set is the list's third, and both have to say so.
        assertEquals(listOf(1, 3), state.depthChart!!.sets.map { it.position })
        assertEquals(listOf(1, 2, 3), state.sets.map { it.index })
    }

    /**
     * The description names sets the listener can scroll to, so it has to survive
     * the same filter the labels do.
     */
    @Test
    fun `the description numbers sets as the list does`() {
        val workout = workout(
            set(startedAt = EPOCH, angles = listOf(90f)),
            set(startedAt = EPOCH + 4.minutes, abandonedCount = 1),
            set(startedAt = EPOCH + 8.minutes, angles = listOf(70f)),
        )

        val chart = workout.toSummaryState(now).depthChart!!

        val expected = UiText.Resource(R.string.workout_summary_chart_sets, 3, 1)
        assertEquals(expected, chart.description)
    }
}
