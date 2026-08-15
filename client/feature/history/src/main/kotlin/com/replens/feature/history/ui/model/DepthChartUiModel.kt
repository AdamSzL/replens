package com.replens.feature.history.ui.model

import com.replens.core.text.UiText

/**
 * One mark per rep, plotted against a marked depth threshold.
 *
 * Angles rather than pre-normalized fractions: `0.42f` is unreadable in a preview
 * and untestable against anything, the threshold line needs the same mapping
 * anyway, and a mark that can be labeled has to keep its degrees.
 *
 * The axis comes from the counter's own thresholds rather than from the data, so
 * two sessions can be compared by eye instead of each being scaled to itself.
 * The exception is [floorAngle], which the data may push down — see below.
 *
 * The thresholds are squat's. A second exercise makes this a chart per exercise
 * rather than per workout; there is one exercise, so there is nothing to select
 * between yet.
 *
 * @param topAngle the counting threshold. A descent that never crossed it was
 *   never a rep, so nothing can plot above the ceiling.
 * @param floorAngle where bodyweight squatters actually bottom out, **not** the
 *   deepest a human can go. Anchoring at true ATG is honest and unreadable: a
 *   session spans maybe 20 degrees, so most of the plot would go to a region
 *   nobody occupies and the per-set trend would collapse into a band. It is a
 *   floor rather than a clamp — someone deeper than it extends the axis instead
 *   of piling up on the bottom edge, which would hide exactly the best reps.
 * @param thresholdAngle roughly parallel; the labeled line, and scoring only.
 */
internal data class DepthChartUiModel(
    val sets: List<DepthChartSetUiModel>,
    /** What TalkBack reads instead of the plot; the marks are not semantics nodes. */
    val description: UiText,
    val topAngle: Float,
    val floorAngle: Float,
    val thresholdAngle: Float,
)

/**
 * Reps stay grouped by the set they were done in, because the gap between sets is
 * what the eye reads the session's shape from. A `List<List<Float>>` would say
 * the same thing and name none of it.
 */
internal data class DepthChartSetUiModel(val angles: List<Float>)
