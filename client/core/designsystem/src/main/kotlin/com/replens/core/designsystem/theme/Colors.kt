package com.replens.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every container carries its `on` color, so contrast is decided here once
 * rather than at each call site.
 */
@Immutable
data class RepLensColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurface: Color,
    val onSurfaceMuted: Color,
    /**
     * Inverts with the theme, because what it must separate from does: a dark
     * fill with a light label on a white page, a light fill with a dark label on
     * a dark one. Radix step 11 in both — the number stays, the semantics remap.
     */
    val accent: Color,
    val onAccent: Color,
    val border: Color,
    /** Theme-independent: a camera feed is whatever the room is. */
    val overlayScrim: Color,

    /**
     * Opaque, unlike [overlayScrim], and that is the point: a panel that *covers*
     * the feed rather than tinting it. Nothing can reliably contrast against a
     * translucent surface, because its rendered color is partly the room.
     */
    val overlaySurface: Color,
    val onOverlay: Color,
    val onOverlayMuted: Color,
    /** Cyan 11, not the solid 9: a mid-tone dot vanishes over a dim room. */
    val overlayAccent: Color,
    val onOverlayAccent: Color,

    /**
     * Light rather than dark, because a secondary button appears both bare on the
     * feed and on [overlaySurface] — and dark on that surface measures 1.2:1.
     * Primary and secondary separate by hue, not by lightness.
     */
    val overlaySecondary: Color,
    val onOverlaySecondary: Color,
    /** Drawn behind the skeleton so its contrast does not depend on the room. */
    val overlayOutline: Color,
)

internal val DarkColors = RepLensColors(
    background = Palette.Dark.Slate1,
    onBackground = Palette.Dark.Slate12,
    surface = Palette.Dark.Slate3,
    onSurface = Palette.Dark.Slate12,
    onSurfaceMuted = Palette.Dark.Slate11,
    accent = Palette.Dark.Cyan11,
    onAccent = Palette.Light.Slate12,
    border = Palette.Dark.Slate6,
    overlayScrim = Palette.BlackA50,
    overlaySurface = Palette.Dark.Slate3,
    onOverlay = Palette.White,
    onOverlayMuted = Palette.WhiteA70,
    overlayAccent = Palette.Dark.Cyan11,
    onOverlayAccent = Palette.Light.Slate12,
    overlaySecondary = Palette.Dark.Slate11,
    onOverlaySecondary = Palette.Light.Slate12,
    overlayOutline = Palette.NearBlack,
)

internal val LightColors = RepLensColors(
    background = Palette.Light.Slate1,
    onBackground = Palette.Light.Slate12,
    surface = Palette.Light.Slate3,
    onSurface = Palette.Light.Slate12,
    onSurfaceMuted = Palette.Light.Slate11,
    accent = Palette.Light.Cyan11,
    onAccent = Palette.White,
    border = Palette.Light.Slate6,
    overlayScrim = Palette.BlackA50,
    overlaySurface = Palette.Dark.Slate3,
    onOverlay = Palette.White,
    onOverlayMuted = Palette.WhiteA70,
    overlayAccent = Palette.Dark.Cyan11,
    onOverlayAccent = Palette.Light.Slate12,
    overlaySecondary = Palette.Dark.Slate11,
    onOverlaySecondary = Palette.Light.Slate12,
    overlayOutline = Palette.NearBlack,
)

val LocalRepLensColors = staticCompositionLocalOf { DarkColors }
