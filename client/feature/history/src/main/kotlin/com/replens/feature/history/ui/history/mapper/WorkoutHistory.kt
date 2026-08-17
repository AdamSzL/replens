package com.replens.feature.history.ui.history.mapper

import com.replens.core.model.WorkoutOverview
import com.replens.feature.history.ui.common.mapper.toUiText
import com.replens.feature.history.ui.history.HistoryState
import com.replens.feature.history.ui.history.model.WorkoutDay
import com.replens.feature.history.ui.history.model.WorkoutRowUiModel
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.time.Instant
import kotlin.time.toJavaInstant

internal fun List<WorkoutOverview>.toHistoryState(
    now: Instant,
    zone: ZoneId,
): HistoryState {
    if (isEmpty()) return HistoryState.Empty
    return HistoryState.Content(
        workouts = map { it.toRow(now, zone) },
    )
}

internal fun WorkoutOverview.toRow(
    now: Instant,
    zone: ZoneId,
): WorkoutRowUiModel {
    val started = startedAt.toJavaInstant().atZone(zone)
    val today = now.toJavaInstant().atZone(zone).toLocalDate()
    return WorkoutRowUiModel(
        id = id,
        day = started.toLocalDate().named(today),
        startedAt = started.toLocalTime(),
        setCount = setCount,
        repCount = repCount,
        repsAtDepth = repsAtDepth,
        duration = (endedAt - startedAt).toUiText(),
    )
}

private fun LocalDate.named(today: LocalDate): WorkoutDay {
    return when (ChronoUnit.DAYS.between(this, today)) {
        0L -> WorkoutDay.Today
        1L -> WorkoutDay.Yesterday
        in 2L..6L -> WorkoutDay.Weekday(dayOfWeek)
        else -> if (year == today.year) WorkoutDay.ThisYear(this) else WorkoutDay.Earlier(this)
    }
}
