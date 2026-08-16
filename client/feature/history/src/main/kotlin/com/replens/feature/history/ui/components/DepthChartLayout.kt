package com.replens.feature.history.ui.components

import com.replens.feature.history.ui.model.DepthChartUiModel

/** Blank columns between sets, in marks. Enough to read as a break, not a gap. */
private const val SET_GAP = 1.6f

/** One rep: [column] in mark units along x, [angle] to be mapped to y. */
internal data class Mark(val column: Float, val angle: Float)

/**
 * Reps in mark units, spending [SET_GAP] blank columns at each set boundary, so
 * the same layout holds at any width. Kept grouped because the set numbers are
 * drawn under the middle of each group.
 */
internal fun DepthChartUiModel.markGroups(): List<List<Mark>> {
    var column = 0f
    return sets.mapIndexed { index, set ->
        if (index > 0) column += SET_GAP
        set.angles.map { angle ->
            Mark(column = column, angle = angle).also { column += 1f }
        }
    }
}

/**
 * Where an angle sits between the ceiling and the floor: 0 at the top of the
 * plot, 1 at the bottom. Deeper reps have *lower* angles, so a deeper rep lands
 * further down, which is the only reading of the chart that matches the movement.
 *
 * Clamped, unlike the axis itself. A rep recorded before the counting threshold
 * was tuned can sit above the ceiling, and the model is deliberately honest about
 * that — but a mark drawn at a negative offset is just a bug on screen.
 */
internal fun DepthChartUiModel.depthFraction(angle: Float): Float {
    val span = topAngle - floorAngle
    if (span <= 0f) return 0f
    return ((topAngle - angle) / span).coerceIn(0f, 1f)
}
