package com.replens.feature.workout.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.theme.RepLensTheme
import com.replens.core.exercise.RepPhase

/** The phase line is a debug affordance; it goes when cues become events. */
@Composable
internal fun RepCounter(
    repCount: Int,
    phase: RepPhase,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RepLensTheme.colors.overlayScrim)
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(
            text = repCount.toString(),
            style = RepLensTheme.typography.display,
            color = RepLensTheme.colors.onOverlay,
        )
        Text(
            text = phase.name,
            style = RepLensTheme.typography.label,
            color = RepLensTheme.colors.onOverlayMuted,
        )
    }
}

@Preview
@Composable
private fun RepCounterPreview() {
    RepLensTheme {
        RepCounter(repCount = 12, phase = RepPhase.BOTTOM)
    }
}
