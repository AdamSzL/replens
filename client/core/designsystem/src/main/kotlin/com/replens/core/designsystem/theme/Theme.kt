package com.replens.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// TODO: colour tokens. Material's defaults until then, so anything unstyled is
//  obviously unstyled rather than quietly plausible.
private val DarkColorScheme = darkColorScheme()
private val LightColorScheme = lightColorScheme()

@Composable
fun RepLensTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalRepLensTypography provides RepLensTypography()) {
        // Kept underneath, referenced by no UI code, so anything not yet wrapped
        // still renders something. Dynamic colour is deliberately gone: a
        // wallpaper-derived palette cannot coexist with a brand one.
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            content = content,
        )
    }
}

/** `RepLensTheme.typography.display` at call sites, mirroring `MaterialTheme`. */
object RepLensTheme {
    val typography: RepLensTypography
        @Composable @ReadOnlyComposable get() = LocalRepLensTypography.current
}
