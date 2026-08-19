package com.replens.core.designsystem.component.button

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.replens.core.designsystem.icon.RepLensIcons
import com.replens.core.designsystem.preview.RepLensOverlayPreview
import com.replens.core.designsystem.theme.RepLensTheme

/**
 * An icon-only control over the camera feed.
 *
 * Wrapping matters more here than for the filled buttons: `IconButton` takes its
 * container and *disabled* colors from `IconButtonDefaults`, which reads
 * `MaterialTheme` — and there is no `MaterialTheme` in this app, so an un-wrapped
 * one silently falls back to Material's palette rather than ours.
 *
 * The scrim is the fill rather than a modifier at the call site because it has to
 * clip with the shape; a background applied outside the button paints a square
 * behind a round ripple.
 */
@Composable
fun OverlayIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .clip(CircleShape)
            .background(RepLensTheme.colors.overlayScrim),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = Color.Transparent,
            contentColor = RepLensTheme.colors.onOverlay,
        ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            tint = RepLensTheme.colors.onOverlay,
        )
    }
}

// Mid-gray stands in for the camera feed: previewing overlay colors on a
// flat white background says nothing about what they sit on.
@Preview
@Composable
private fun OverlayIconButtonPreview() {
    RepLensOverlayPreview {
        OverlayIconButton(
            icon = RepLensIcons.CameraSwitch,
            contentDescription = "Switch camera",
            onClick = {},
        )
    }
}
