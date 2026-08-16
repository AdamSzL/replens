package com.replens.core.designsystem.component.card

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun RepLensCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = RepLensTheme.colors.surface,
        contentColor = RepLensTheme.colors.onSurface,
    ) {
        Box(
            modifier = Modifier.padding(contentPadding),
        ) {
            content()
        }
    }
}

@Preview
@Composable
private fun RepLensCardLightThemePreview() {
    RepLensTheme(
        darkTheme = false,
    ) {
        RepLensCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = "12 reps",
                    style = RepLensTheme.typography.title,
                    color = RepLensTheme.colors.onSurface,
                )
                Text(
                    text = "8 reached depth",
                    style = RepLensTheme.typography.body,
                    color = RepLensTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Preview
@Composable
private fun RepLensCardDarkThemePreview() {
    RepLensTheme(
        darkTheme = true,
    ) {
        RepLensCard(
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "12 reps",
                style = RepLensTheme.typography.title,
                color = RepLensTheme.colors.onSurface,
            )
        }
    }
}
