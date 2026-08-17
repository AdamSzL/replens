package com.replens.feature.history.ui.summary.model

import com.replens.core.text.UiText

/**
 * One mark per rep, plotted against a marked depth threshold.
 *
 * Angles rather than pre-normalized fractions: `0.42f` is unreadable in a preview
 * and untestable against anything, the threshold line needs the same mapping
 * anyway, and a mark that can be labeled has to keep its degrees.
 *
 * The axis always contains [thresholdAngle] and both ends snap to a fixed step,
 * so two sessions can be compared by eye instead of each being scaled to itself.
 * Within that, a rep outside the range pushes the end it exceeds — see below.
 *
 * The thresholds are squat's. A second exercise makes this a chart per exercise
 * rather than per workout; there is one exercise, so there is nothing to select
 * between yet.
 *
 * @param topAngle the shallowest rep, or [thresholdAngle] when every rep beat it,
 *   plus padding. **Not** the counting threshold, which is lenient by design and
 *   would leave the top of the plot empty on any session worth looking at.
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
 *
 * [position] is the set's number **in the workout**, not its index here: a set
 * that reached no rep is dropped from the chart and still numbered in the list
 * below it, so counting the groups would label the third set "2".
 */
internal data class DepthChartSetUiModel(
    val position: Int,
    val angles: List<Float>,
)
