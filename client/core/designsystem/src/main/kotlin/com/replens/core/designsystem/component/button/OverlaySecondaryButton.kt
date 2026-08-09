package com.replens.core.designsystem.component.button

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.theme.RepLensTheme

/**
 * Everything that is not the main action, drawn over the camera feed.
 *
 * Opaque, because it appears in two places — bare on the feed, and on an
 * `overlaySurface` panel — and a translucent fill composites differently in each.
 * The scrim it used to carry stacked with the panel's own scrim and rendered the
 * button darker than the card it sat on.
 *
 * Light rather than dark for the same reason the fill is opaque: every dark
 * candidate measures 1.2-1.4:1 against that panel. It reads as secondary next to
 * [OverlayPrimaryButton] by being neutral, not by being dimmer.
 */
@Composable
fun OverlaySecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RepLensButtonDefaults.shape,
        contentPadding = RepLensButtonDefaults.contentPadding,
        colors = ButtonDefaults.buttonColors(
            containerColor = RepLensTheme.colors.overlaySecondary,
            contentColor = RepLensTheme.colors.onOverlaySecondary,
        ),
    ) {
        Text(
            text = text,
            style = RepLensButtonDefaults.textStyle,
        )
    }
}

@Preview
@Composable
private fun OverlaySecondaryButtonPreview() {
    RepLensTheme {
        Box(
            modifier = Modifier
                .background(Color(0xFF6E7378))
                .padding(24.dp),
        ) {
            OverlaySecondaryButton(text = "Cancel", onClick = {})
        }
    }
}
