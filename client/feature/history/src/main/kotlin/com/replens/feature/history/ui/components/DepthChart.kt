package com.replens.feature.history.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.component.card.RepLensCard
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.text.UiText
import com.replens.core.ui.asString
import com.replens.feature.history.R
import com.replens.feature.history.ui.model.DepthChartSetUiModel
import com.replens.feature.history.ui.model.DepthChartUiModel
import kotlin.math.roundToInt

private val ChartHeight = 200.dp
private val MarkRadius = 5.dp

/** Between the plot and the set numbers under it. */
private val LabelGap = 4.dp

@Composable
internal fun DepthChart(
    chart: DepthChartUiModel,
    modifier: Modifier = Modifier,
) {
    val markColor = RepLensTheme.colors.accent
    val lineColor = RepLensTheme.colors.onSurfaceMuted
    val labelStyle = RepLensTheme.typography.label.copy(color = lineColor)
    val measurer = rememberTextMeasurer()
    // Laid out in marks, so spacing is decided before a width is known.
    val groups = chart.markGroups()
    val lastColumn = groups.flatten().lastOrNull()?.column ?: 0f
    val description = chart.description.asString()

    RepLensCard(
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ThresholdLegend(
                label = stringResource(
                    R.string.workout_summary_depth_threshold,
                    chart.thresholdAngle.roundToInt(),
                ),
                color = lineColor,
            )
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ChartHeight)
                    .semantics { contentDescription = description },
            ) {
                val radius = MarkRadius.toPx()
                // Measured rather than a reserved constant: at a 200% font scale
                // the set numbers are taller than any band worth hardcoding, and a
                // fixed one clips them instead of shrinking the plot.
                val labelHeight = measurer.measure("1", labelStyle).size.height
                val plotBottom = size.height - labelHeight - LabelGap.toPx()
                // Inset by a radius so the outermost marks sit inside the plot
                // rather than half outside it.
                val plotHeight = plotBottom - 2 * radius
                val plotWidth = size.width - 2 * radius

                fun y(angle: Float): Float = radius + chart.depthFraction(angle) * plotHeight
                fun x(column: Float): Float = when {
                    lastColumn == 0f -> size.width / 2f
                    else -> radius + column / lastColumn * plotWidth
                }

                val dash = 4.dp.toPx()
                val thresholdY = y(chart.thresholdAngle)
                drawLine(
                    color = lineColor,
                    start = Offset(0f, thresholdY),
                    end = Offset(size.width, thresholdY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
                )

                groups.forEachIndexed { index, marks ->
                    marks.forEach { mark ->
                        drawCircle(
                            color = markColor,
                            radius = radius,
                            center = Offset(x(mark.column), y(mark.angle)),
                        )
                    }
                    val number = measurer.measure("${chart.sets[index].position}", labelStyle)
                    val center = (x(marks.first().column) + x(marks.last().column)) / 2f
                    drawText(
                        textLayoutResult = number,
                        topLeft = Offset(
                            x = center - number.size.width / 2f,
                            y = plotBottom + LabelGap.toPx(),
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Above the plot rather than on the line. An annotation inside the plot has
 * nothing keeping marks off it, and a fifteen-rep set put three of them under
 * the text.
 */
@Composable
private fun ThresholdLegend(
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .width(16.dp)
                .height(1.dp),
        ) {
            val dash = 3.dp.toPx()
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width, size.height / 2f),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash)),
            )
        }
        Text(
            text = label,
            style = RepLensTheme.typography.label,
            color = color,
        )
    }
}

@Composable
private fun ChartPreview(
    darkTheme: Boolean,
    chart: DepthChartUiModel,
) {
    RepLensTheme(darkTheme = darkTheme) {
        DepthChart(chart = chart)
    }
}

private fun chartOf(vararg sets: List<Float>): DepthChartUiModel {
    val angles = sets.toList().flatten()
    return DepthChartUiModel(
        sets = sets.mapIndexed { index, angles ->
            DepthChartSetUiModel(
                position = index + 1,
                angles = angles,
            )
        },
        description = UiText.Raw("Depth of each rep"),
        topAngle = maxOf(angles.max(), 95f) + 5f,
        floorAngle = minOf(angles.min(), 95f) - 5f,
        thresholdAngle = 95f,
    )
}

/** The author's own numbers, straight out of the database. */
private val deepSession = chartOf(
    listOf(66.9f, 59.8f, 73.0f),
    listOf(57.8f, 56.0f),
    listOf(50.8f, 59.6f),
    listOf(57.3f, 71.6f, 52.7f),
)

@Preview
@Composable
private fun DepthChartDeepLightPreview() = ChartPreview(darkTheme = false, chart = deepSession)

@Preview
@Composable
private fun DepthChartDeepDarkPreview() = ChartPreview(darkTheme = true, chart = deepSession)

/** Every rep short of parallel, so the line sits low with marks above it. */
@Preview
@Composable
private fun DepthChartShallowPreview() = ChartPreview(
    darkTheme = true,
    chart = chartOf(
        listOf(112f, 105f, 109f),
        listOf(101f, 108f, 113f),
    ),
)

/** Straddling the line, which is the only case where the line separates anything. */
@Preview
@Composable
private fun DepthChartMixedPreview() = ChartPreview(
    darkTheme = true,
    chart = chartOf(
        listOf(88f, 92f, 97f, 103f),
        listOf(91f, 99f, 108f),
    ),
)

/** Fifteen reps in one set: the density the layout has to survive. */
@Preview
@Composable
private fun DepthChartLongSetPreview() = ChartPreview(
    darkTheme = true,
    chart = chartOf(
        List(15) { 84f + (it % 5) * 4f },
    ),
)

/** One rep has no spread to plot, so it has to land somewhere sensible. */
@Preview
@Composable
private fun DepthChartSingleRepPreview() = ChartPreview(
    darkTheme = true,
    chart = chartOf(listOf(92f)),
)

/** 200% font scale: the set numbers shrink the plot rather than spilling past it. */
@Preview(fontScale = 2f)
@Composable
private fun DepthChartLargeFontPreview() = ChartPreview(darkTheme = true, chart = deepSession)
