package com.replens.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.replens.core.designsystem.R

/** Subset to Latin and Polish by `tools/subset-fonts.sh`. */
private val Montserrat = FontFamily(
    Font(R.font.montserrat_regular, FontWeight.Normal),
    Font(R.font.montserrat_semibold, FontWeight.SemiBold),
)

/**
 * Named for the job, not the size: `Title28` becomes a lie the moment the size
 * changes, or an accessibility setting scales it.
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

val LocalRepLensTypography = staticCompositionLocalOf { RepLensTypography() }
