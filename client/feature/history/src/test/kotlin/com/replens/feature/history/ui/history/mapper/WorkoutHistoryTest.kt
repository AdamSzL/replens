package com.replens.feature.history.ui.history.mapper

import com.replens.core.model.WorkoutOverview
import com.replens.core.text.UiText
import com.replens.feature.history.R
import com.replens.feature.history.ui.history.HistoryState
import com.replens.feature.history.ui.history.model.WorkoutDay
import com.replens.feature.history.ui.history.model.WorkoutRowUiModel
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.time.Instant

/**
 * A fixed zone throughout, because every question here is "which calendar day is
 * this instant on" and CI answers that in UTC while a user answers it in theirs.
 * Warsaw is +02:00 in August, so an evening instant lands on the next day.
 */
private val zone = ZoneId.of("Europe/Warsaw")

/** 10:00 on Monday 17 August 2026, Warsaw. */
private val now = Instant.parse("2026-08-17T08:00:00Z")

class WorkoutHistoryTest {

    private fun overview(
        startedAt: String,
        endedAt: String = startedAt,
        setCount: Int = 1,
        repCount: Int = 1,
        repsAtDepth: Int = 1,
    ): WorkoutOverview {
        return WorkoutOverview(
            id = 7L,
            startedAt = Instant.parse(startedAt),
            endedAt = Instant.parse(endedAt),
            setCount = setCount,
            repCount = repCount,
            repsAtDepth = repsAtDepth,
        )
    }

    private fun rowOf(overview: WorkoutOverview): WorkoutRowUiModel {
        val state = listOf(overview).toHistoryState(now, zone)
        return (state as HistoryState.Content).workouts.single()
    }

    private fun dayOf(startedAt: String): WorkoutDay = rowOf(overview(startedAt)).day

    @Test
    fun `a workout earlier today is today`() {
        assertEquals(WorkoutDay.Today, dayOf("2026-08-17T05:00:00Z"))
    }

    @Test
    fun `the day before is yesterday`() {
        assertEquals(WorkoutDay.Yesterday, dayOf("2026-08-16T05:00:00Z"))
    }

    @Test
    fun `two days back is named by its weekday`() {
        assertEquals(WorkoutDay.Weekday(DayOfWeek.SATURDAY), dayOf("2026-08-15T05:00:00Z"))
    }

    /**
     * The boundary the whole rule exists for: at six days the weekday still names
     * one day, at seven it names two and stops being an answer — which is visible
     * here, since seven days back is the Monday that `now` also is.
     */
    @Test
    fun `six days back is still a weekday`() {
        assertEquals(WorkoutDay.Weekday(DayOfWeek.TUESDAY), dayOf("2026-08-11T05:00:00Z"))
    }

    @Test
    fun `seven days back falls back to a date`() {
        assertEquals(WorkoutDay.ThisYear(LocalDate.of(2026, 8, 10)), dayOf("2026-08-10T05:00:00Z"))
    }

    /**
     * A date names one day only within its year, so the arm that carries the year
     * starts where the current one ends — not at a fixed distance, which would put
     * two identically labelled rows next to each other across a new year.
     */
    @Test
    fun `a date in another year is told apart from one in this year`() {
        assertEquals(WorkoutDay.Earlier(LocalDate.of(2025, 12, 31)), dayOf("2025-12-31T05:00:00Z"))
        assertEquals(WorkoutDay.ThisYear(LocalDate.of(2026, 1, 1)), dayOf("2026-01-01T05:00:00Z"))
    }

    /**
     * 22:30 UTC is already the next day in Warsaw. Read in UTC this workout would
     * say "Yesterday" to someone who did it half an hour ago.
     */
    @Test
    fun `the calendar day is the zone's, not the instant's`() {
        assertEquals(WorkoutDay.Today, dayOf("2026-08-16T22:30:00Z"))
    }

    /** A clock set forward, or set back after training. A date is the honest answer. */
    @Test
    fun `a workout dated after today reads as a date`() {
        assertEquals(WorkoutDay.ThisYear(LocalDate.of(2026, 8, 18)), dayOf("2026-08-18T05:00:00Z"))
    }

    @Test
    fun `the clock time is the zone's`() {
        val row = rowOf(overview("2026-08-17T05:12:00Z"))

        assertEquals(LocalTime.of(7, 12), row.startedAt)
    }

    @Test
    fun `an empty list is Empty, not a Content holding nothing`() {
        assertEquals(HistoryState.Empty, emptyList<WorkoutOverview>().toHistoryState(now, zone))
    }

    @Test
    fun `a list maps to rows in the order it arrived`() {
        val state = listOf(
            overview("2026-08-17T05:00:00Z"),
            overview("2026-08-16T05:00:00Z"),
        ).toHistoryState(now, zone)

        val days = (state as HistoryState.Content).workouts.map(WorkoutRowUiModel::day)
        assertEquals(listOf(WorkoutDay.Today, WorkoutDay.Yesterday), days)
    }

    @Test
    fun `the row carries the totals and the duration it spanned`() {
        val row = rowOf(
            overview(
                startedAt = "2026-08-17T05:00:00Z",
                endedAt = "2026-08-17T05:24:00Z",
                setCount = 3,
                repCount = 30,
                repsAtDepth = 21,
            ),
        )

        assertEquals(7L, row.id)
        assertEquals(3, row.setCount)
        assertEquals(30, row.repCount)
        assertEquals(21, row.repsAtDepth)
        assertEquals(UiText.Resource(R.string.duration_minutes, 24), row.duration)
    }
}
