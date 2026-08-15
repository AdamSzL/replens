package com.replens.feature.history.ui.components

import com.replens.core.text.UiText
import com.replens.feature.history.ui.model.DepthChartSetUiModel
import com.replens.feature.history.ui.model.DepthChartUiModel
import org.junit.Assert.assertEquals
import org.junit.Test

class DepthChartAxisTest {

    private val chart = DepthChartUiModel(
        sets = listOf(DepthChartSetUiModel(listOf(90f))),
        description = UiText.Raw("unused: the axis is only arithmetic"),
        topAngle = 115f,
        floorAngle = 75f,
        thresholdAngle = 95f,
    )

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
