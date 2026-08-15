package com.replens.feature.history.ui.mapper

import com.replens.core.data.WORKOUT_GAP
import com.replens.core.exercise.squat.SquatRepConfig
import com.replens.core.model.Exercise
import com.replens.core.model.Workout
import com.replens.core.text.UiText
import com.replens.feature.history.R
import com.replens.feature.history.ui.WorkoutSummaryState
import com.replens.feature.history.ui.model.DepthChartSetUiModel
import com.replens.feature.history.ui.model.DepthChartUiModel
import com.replens.feature.history.ui.model.SessionTotalsUiModel
import com.replens.feature.history.ui.model.SetRowUiModel
import kotlin.time.Instant

/**
 * Where bodyweight squatters actually bottom out, in interior knee degrees — the
 * reasoning is on [DepthChartUiModel.floorAngle], which this only ever supplies.
 */
private const val DEPTH_FLOOR = 75f

/**
 * The whole screen, from one workout.
 *
 * **Assumes sets are in chronological order and reps in index order**, which the
 * DAO guarantees (`ORDER BY startedAt`, `ORDER BY setId, rep_index`). Set numbers
 * and rest gaps are both read off that order, so re-sorting here would only hide
 * a broken query rather than survive one.
 *
 * [config] is a parameter so a test can assert the axis follows the counter's
 * thresholds rather than assert 115 against a hardcoded 115.
 */
internal fun Workout.toSummaryState(
    now: Instant,
    config: SquatRepConfig = SquatRepConfig(),
): WorkoutSummaryState.Loaded {
    return WorkoutSummaryState.Loaded(
        totals = totals(now),
        depthChart = depthChart(config),
        sets = setRows(),
    )
}

private fun Workout.totals(now: Instant): SessionTotalsUiModel {
    return SessionTotalsUiModel(
        setCount = sets.size,
        repCount = sets.sumOf { it.repCount },
        repsAtDepth = sets.sumOf { it.repsAtDepth },
        duration = (endedAt - startedAt).toUiText(),
        isOpen = now - endedAt < WORKOUT_GAP,
    )
}

/**
 * Sets that produced no rep are dropped rather than plotted empty: they would
 * open a gap in the series with nothing on either side of it, which reads as
 * missing data instead of as a set that never reached depth. The row still
 * reports them — that is what `abandonedCount` is for.
 */
private fun Workout.depthChart(config: SquatRepConfig): DepthChartUiModel? {
    val series = sets
        .filter { it.reps.isNotEmpty() }
        .map { set -> DepthChartSetUiModel(set.reps.map { it.deepestAngle }) }
    if (series.isEmpty()) return null
    return DepthChartUiModel(
        sets = series,
        topAngle = config.bottomEnterAngle,
        floorAngle = minOf(DEPTH_FLOOR, series.minOf { it.angles.min() }),
        thresholdAngle = config.goodDepthAngle,
    )
}

private fun Workout.setRows(): List<SetRowUiModel> {
    return sets.mapIndexed { position, set ->
        SetRowUiModel(
            id = set.id,
            index = position + 1,
            exercise = set.exercise.toUiText(),
            repCount = set.repCount,
            repsAtDepth = set.repsAtDepth,
            duration = (set.endedAt - set.startedAt).toUiText(),
            // getOrNull(-1) on the first set, which follows nothing.
            restBefore = sets.getOrNull(position - 1)
                ?.let { (set.startedAt - it.endedAt).toUiText() },
            abandonedCount = set.abandonedCount,
        )
    }
}

private fun Exercise.toUiText(): UiText = when (this) {
    Exercise.SQUAT -> UiText.Resource(R.string.exercise_squat)
}
