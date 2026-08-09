package com.replens.core.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * Raw palette — [Radix Colors](https://www.radix-ui.com/colors), `slate` and
 * `cyan`. **Never referenced by UI code**; only [RepLensColors] maps these onto
 * names that mean something.
 *
 * Radix defines what each step is *for*, which is why it is worth borrowing
 * rather than inventing: 1–2 backgrounds, 3–5 component fills, 6–8 borders,
 * 9 the solid brand color, 11 low-contrast text, 12 high-contrast text. Step 11
 * on step 2 is guaranteed to pass WCAG, so contrast is inherited rather than
 * checked by eye.
 *
 * Light and dark use the same step numbers for the same jobs, which is what makes
 * theming a remap instead of a redesign.
 *
 * [Dark] and [Light] name the **surface a color sits on, not the app's theme** —
 * that is Radix's own meaning. So [LightColors] reaching into [Dark] for overlay
 * colors is not a mix-up: the camera feed under a scrim is a dark surface in
 * either theme, so it takes the dark-surface scale in both.
 */
internal object Palette {

    object Dark {
        val Slate1 = Color(0xFF111113)
        val Slate3 = Color(0xFF212225)
        val Slate6 = Color(0xFF363A3F)
        val Slate11 = Color(0xFFB0B4BA)
        val Slate12 = Color(0xFFEDEEF0)

        val Cyan11 = Color(0xFF4CCCE6)
    }

    object Light {
        val Slate1 = Color(0xFFFCFCFD)
        val Slate3 = Color(0xFFF0F0F3)
        val Slate6 = Color(0xFFD9D9E0)
        val Slate11 = Color(0xFF60646C)
        val Slate12 = Color(0xFF1C2024)

        val Cyan11 = Color(0xFF107D98)
    }

    // Outside the Radix scales: neutrals for content drawn over video.
    val White = Color(0xFFFFFFFF)
    val WhiteA70 = Color(0xB3FFFFFF)
    val NearBlack = Color(0xFF0B0F12)
    val BlackA50 = Color(0x80000000)
}
