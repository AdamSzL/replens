package com.replens.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every container carries its `on` colour, so contrast is decided here once
 * rather than at each call site.
 */
@Immutable
data class RepLensColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val onAccent: Color,
    /** The solid accent is too dim to read as text; same hue, lighter. */
    val accentText: Color,
    val border: Color,
    /** Theme-independent: a camera feed is whatever the room is. */
    val overlayScrim: Color,
    val onOverlay: Color,
    val onOverlayMuted: Color,
    /** Cyan 11, not the solid 9: a mid-tone dot vanishes over a dim room. */
    val overlayAccent: Color,
    val onOverlayAccent: Color,
    /** Drawn behind the skeleton so its contrast does not depend on the room. */
    val overlayOutline: Color,
)

internal val DarkColors = RepLensColors(
    background = Palette.Dark.Slate1,
    onBackground = Palette.Dark.Slate12,
    surface = Palette.Dark.Slate3,
    onSurface = Palette.Dark.Slate12,
    onSurfaceMuted = Palette.Dark.Slate11,
    accent = Palette.Dark.Cyan9,
    onAccent = Palette.Light.Slate12,
    accentText = Palette.Dark.Cyan11,
    border = Palette.Dark.Slate6,
    overlayScrim = Palette.BlackA50,
    onOverlay = Palette.White,
    onOverlayMuted = Palette.WhiteA70,
    overlayAccent = Palette.Dark.Cyan11,
    onOverlayAccent = Palette.Light.Slate12,
    overlayOutline = Palette.NearBlack,
)

internal val LightColors = RepLensColors(
    background = Palette.Light.Slate1,
    onBackground = Palette.Light.Slate12,
    surface = Palette.Light.Slate3,
    onSurface = Palette.Light.Slate12,
    onSurfaceMuted = Palette.Light.Slate11,
    accent = Palette.Light.Cyan9,
    onAccent = Palette.Light.Slate12,
    accentText = Palette.Light.Cyan11,
    border = Palette.Light.Slate6,
    overlayScrim = Palette.BlackA50,
    onOverlay = Palette.White,
    onOverlayMuted = Palette.WhiteA70,
    overlayAccent = Palette.Dark.Cyan11,
    onOverlayAccent = Palette.Light.Slate12,
    overlayOutline = Palette.NearBlack,
)

val LocalRepLensColors = staticCompositionLocalOf { DarkColors }
