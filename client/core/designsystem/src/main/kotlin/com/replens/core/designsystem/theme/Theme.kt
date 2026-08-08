package com.replens.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

@Composable
fun RepLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRepLensColors provides if (darkTheme) DarkColors else LightColors,
        LocalRepLensTypography provides RepLensTypography(),
        content = content,
    )
}

object RepLensTheme {
    val colors: RepLensColors
        @Composable @ReadOnlyComposable get() = LocalRepLensColors.current

    val typography: RepLensTypography
        @Composable @ReadOnlyComposable get() = LocalRepLensTypography.current
}
