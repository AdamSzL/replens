package com.replens.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.replens.core.designsystem.R

/**
 * Subset to Latin and Polish by `tools/subset-fonts.sh` — the full family Google
 * Fonts ships is 325 KB per weight against our 101 KB.
 */
private val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
)

/**
 * The app's type scale, in RepLens's own vocabulary rather than Material's.
 *
 * Named for the **job**, not the instance or the size. `display` covers the rep
 * count today and a timer tomorrow; a name like `repCount` would need renaming
 * the moment something else wanted the same treatment, and a name like `Title28`
 * would be a lie as soon as the size changed — or the moment an accessibility
 * setting scaled it, which happens constantly.
 *
 * Four sizes and two weights on purpose. Amateur typography comes from having too
 * many values, not the wrong ones. Add a token when a screen genuinely needs one.
 */
@Immutable
data class RepLensTypography(
    /** Hero numbers. `tnum` keeps digits from reflowing as a count passes 9. */
    val display: TextStyle = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 96.sp,
        lineHeight = 100.sp,
        fontFeatureSettings = "tnum",
    ),
    val title: TextStyle = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
    ),
    val body: TextStyle = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    val label: TextStyle = TextStyle(
        fontFamily = Montserrat,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
)

/**
 * Static because the type scale never changes at runtime — reading it should not
 * cost recomposition tracking.
 */
val LocalRepLensTypography = staticCompositionLocalOf { RepLensTypography() }
