package com.replens.feature.history.ui.summary.components

import com.replens.core.text.UiText
import com.replens.feature.history.ui.summary.model.DepthChartSetUiModel
import com.replens.feature.history.ui.summary.model.DepthChartUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DepthChartLayoutTest {

    private val chart = DepthChartUiModel(
        sets = listOf(
            DepthChartSetUiModel(
                position = 1,
                angles = listOf(90f),
            ),
        ),
        description = UiText.Raw("unused: the axis is only arithmetic"),
        topAngle = 115f,
        floorAngle = 75f,
        thresholdAngle = 95f,
    )

    private fun chartOf(vararg sets: List<Float>): DepthChartUiModel {
        return chart.copy(
            sets = sets.mapIndexed { index, angles ->
                DepthChartSetUiModel(
                    position = index + 1,
                    angles = angles,
                )
            },
        )
    }

    @Test
    fun `reps in a set take one column each`() {
        val groups = chartOf(listOf(90f, 91f, 92f)).markGroups()

        assertEquals(listOf(0f, 1f, 2f), groups.single().map { it.column })
    }

    /** Before the first set there is no boundary to mark, so no gap is spent. */
    @Test
    fun `a gap falls between sets, never before the first`() {
        val groups = chartOf(listOf(90f, 91f), listOf(92f)).markGroups()

        assertEquals(listOf(0f, 1f), groups[0].map { it.column })
        assertEquals(listOf(3.6f), groups[1].map { it.column })
    }

    /** The grouping is what the set numbers are drawn under, so it has to survive. */
    @Test
    fun `marks keep the sets they arrived in`() {
        val groups = chartOf(listOf(90f, 91f), listOf(92f, 93f, 94f)).markGroups()

        assertEquals(listOf(2, 3), groups.map { it.size })
        assertEquals(listOf(92f, 93f, 94f), groups[1].map { it.angle })
    }

    @Test
    fun `the ceiling is the top of the plot`() {
        assertEquals(0f, chart.depthFraction(115f), 0.001f)
    }

    @Test
    fun `the floor is the bottom of the plot`() {
        assertEquals(1f, chart.depthFraction(75f), 0.001f)
    }

    /** Deeper is lower: a rep past the threshold sits below the line, not above. */
    @Test
    fun `a deeper rep sits further down`() {
        assertEquals(0.5f, chart.depthFraction(95f), 0.001f)
        assertEquals(0.75f, chart.depthFraction(85f), 0.001f)
    }

    /**
     * Only reachable by tuning the counting threshold after the fact. The axis
     * keeps saying so; the plot still has to stay inside its box.
     */
    @Test
    fun `an angle above the ceiling is clamped rather than drawn outside`() {
        assertEquals(0f, chart.depthFraction(130f), 0.001f)
    }

    @Test
    fun `an angle below the floor is clamped`() {
        assertEquals(1f, chart.depthFraction(40f), 0.001f)
    }

    /** A degenerate axis must not divide by zero on its way to the screen. */
    @Test
    fun `a collapsed axis reads as the top`() {
        val flat = chart.copy(topAngle = 90f, floorAngle = 90f)

        assertEquals(0f, flat.depthFraction(90f), 0.001f)
    }
}
