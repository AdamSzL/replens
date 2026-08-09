package com.replens.core.designsystem.component.button

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.replens.core.designsystem.theme.RepLensTheme

internal object RepLensButtonDefaults {

    val shape: Shape = CircleShape

    val contentPadding = PaddingValues(horizontal = 32.dp, vertical = 16.dp)

    val textStyle: TextStyle
        @Composable @ReadOnlyComposable get() = RepLensTheme.typography.label
}
