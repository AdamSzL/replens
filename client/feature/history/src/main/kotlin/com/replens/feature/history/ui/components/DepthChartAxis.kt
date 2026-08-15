package com.replens.feature.history.ui.components

import com.replens.feature.history.ui.model.DepthChartUiModel

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
