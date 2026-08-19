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
import androidx.compose.ui.tooling.preview.PreviewLightDark
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.preview.RepLensPreview
import com.replens.core.designsystem.theme.RepLensTheme

private val CardShape = RoundedCornerShape(16.dp)
private val CardPadding = PaddingValues(16.dp)

@Composable
fun RepLensCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = CardPadding,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = CardShape,
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

@Composable
fun RepLensCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = CardPadding,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CardShape,
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

@PreviewLightDark
@Composable
private fun RepLensCardPreview() {
    RepLensPreview {
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
