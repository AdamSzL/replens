package com.replens.feature.history.ui.common.mapper

import com.replens.core.text.UiText
import com.replens.feature.history.R
import kotlin.time.Duration

/**
 * A duration at the coarsest unit that still says something.
 *
 * The unit is chosen here rather than in a composable because choosing is the
 * whole job — that is the line between text a mapper carries as [UiText] and text
 * a composable can name for itself.
 *
 * Truncating rather than rounding: a set of 1 min 59 s reading as "1 min" is a
 * summary being coarse, while one of 59 s reading as "1 min" would be a summary
 * being wrong about which side of a minute it fell on.
 *
 * The second consumer arrived as the history row, which is what puts this in
 * `common` rather than under a screen. It leaves for `:core:text` when the
 * consumer is another *module* — that is when `duration_*` can collide — and it
 * takes those strings with it.
 */
internal fun Duration.toUiText(): UiText {
    val minutes = inWholeMinutes
    return when {
        minutes < 1 -> UiText.Resource(R.string.duration_seconds, inWholeSeconds.toInt())
        minutes < 60 -> UiText.Resource(R.string.duration_minutes, minutes.toInt())
        else -> UiText.Resource(
            R.string.duration_hours,
            (minutes / 60).toInt(),
            (minutes % 60).toInt(),
        )
    }
}
