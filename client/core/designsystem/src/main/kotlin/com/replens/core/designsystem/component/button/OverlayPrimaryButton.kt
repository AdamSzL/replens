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
 * The action a screen exists for, drawn over the camera feed.
 *
 * Identical to [PrimaryButton] in dark mode and lighter in light mode, which is
 * the whole difference between them: **a camera feed under a scrim is a dark
 * surface permanently**, so overlay colours are the dark-theme values frozen.
 * [PrimaryButton] inverts with the theme because our own background does; the
 * room behind this one does not care what theme is set.
 *
 * Reads 9.96:1 against a dark room and 8.65:1 on its label.
 */
@Composable
fun OverlayPrimaryButton(
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
            containerColor = RepLensTheme.colors.overlayAccent,
            contentColor = RepLensTheme.colors.onOverlayAccent,
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
private fun OverlayPrimaryButtonPreview() {
    RepLensTheme {
        // Mid-grey stands in for the camera feed: previewing overlay colours on a
        // flat white background says nothing about what they sit on.
        Box(
            modifier = Modifier
                .background(Color(0xFF6E7378))
                .padding(24.dp),
        ) {
            OverlayPrimaryButton(text = "Start set", onClick = {})
        }
    }
}
