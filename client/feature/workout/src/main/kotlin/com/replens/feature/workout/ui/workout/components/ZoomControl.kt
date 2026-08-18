package com.replens.feature.workout.ui.workout.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
internal fun ZoomControl(
    stops: List<Float>,
    selected: Float,
    onSelect: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(RepLensTheme.colors.overlayScrim)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (stop in stops) {
            val isSelected = stop == selected
            ZoomStop(
                ratio = stop,
                selected = isSelected,
                onClick = {
                    if (!isSelected) haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onSelect(stop)
                },
            )
        }
    }
}

@Composable
private fun ZoomStop(
    ratio: Float,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = formatRatio(ratio),
        style = RepLensTheme.typography.label,
        color = if (selected) {
            RepLensTheme.colors.onOverlayAccent
        } else {
            RepLensTheme.colors.onOverlay
        },
        modifier = Modifier
            .clip(CircleShape)
            .background(
                if (selected) RepLensTheme.colors.overlayAccent else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

/** Locale-aware: Polish writes `0,7`. */
private fun formatRatio(ratio: Float): String =
    if (ratio == ratio.toInt().toFloat()) "${ratio.toInt()}×" else "%.1f×".format(ratio)

@Preview
@Composable
private fun ZoomControlPreview() {
    RepLensTheme {
        ZoomControl(
            stops = listOf(0.7f, 1f, 2f),
            selected = 1f,
            onSelect = {},
        )
    }
}
