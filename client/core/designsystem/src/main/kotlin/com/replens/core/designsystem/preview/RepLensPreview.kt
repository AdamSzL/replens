package com.replens.core.designsystem.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.replens.core.designsystem.theme.RepLensTheme

@Composable
fun RepLensPreview(content: @Composable () -> Unit) {
    RepLensTheme {
        Box(modifier = Modifier.background(RepLensTheme.colors.background)) {
            content()
        }
    }
}

private val CameraFeed = Color(0xFF6E7378)

@Composable
fun RepLensOverlayPreview(content: @Composable () -> Unit) {
    RepLensTheme {
        Box(modifier = Modifier.background(CameraFeed)) {
            content()
        }
    }
}
