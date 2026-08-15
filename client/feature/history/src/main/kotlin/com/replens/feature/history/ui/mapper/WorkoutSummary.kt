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
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Instant

/**
 * How far past the outermost rep the axis reaches, and the step both ends snap
 * to, in interior knee degrees.
 *
 * Snapping is what keeps two of *your* sessions comparable: an axis fitted
 * exactly to each session's range would put the same rep at a different height
 * every time. A guessed constant floor was tried first and measured wrong — the
 * author's reps land at 50-73 degrees, so a floor at 75 was dragged down to the
 * deepest rep anyway and left two thirds of the plot empty.
 */
private const val AXIS_PADDING = 5f
private const val AXIS_STEP = 5f

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
    val angles = series.flatMap { it.angles }
    return DepthChartUiModel(
        sets = series,
        description = series.description(),
        // The axis contains the threshold *and* the padding, at both ends. Only
        // guarding the ceiling left a session where every rep was shallow with the
        // line clamped onto the bottom edge — drawn where the floor is rather than
        // where the threshold is, which is the one thing the line must not lie about.
        topAngle = snapUp(maxOf(angles.max(), config.goodDepthAngle) + AXIS_PADDING),
        floorAngle = snapDown(minOf(angles.min(), config.goodDepthAngle) - AXIS_PADDING),
        thresholdAngle = config.goodDepthAngle,
    )
}

/**
 * Positions rather than degrees: an interior angle needs the whole convention
 * explained before it means anything, while "set 3" is something the listener can
 * act on. It also says only what the plot adds — the totals card is announced
 * first and already carries the counts.
 */
private fun List<DepthChartSetUiModel>.description(): UiText {
    val angles = flatMap { it.angles }
    if (angles.size == 1) return UiText.Resource(R.string.workout_summary_chart_single_rep)
    val deepest = angles.min()
    val shallowest = angles.max()
    if (size == 1) {
        val reps = first().angles
        return UiText.Resource(
            R.string.workout_summary_chart_one_set,
            reps.indexOf(deepest) + 1,
            reps.indexOf(shallowest) + 1,
        )
    }
    return UiText.Plural(
        R.plurals.workout_summary_chart_sets,
        size,
        size,
        indexOfFirst { deepest in it.angles } + 1,
        indexOfFirst { shallowest in it.angles } + 1,
    )
}

private fun snapUp(angle: Float): Float = ceil(angle / AXIS_STEP) * AXIS_STEP

private fun snapDown(angle: Float): Float = floor(angle / AXIS_STEP) * AXIS_STEP

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
