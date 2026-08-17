package com.replens.feature.history.ui.summary.model

import com.replens.core.text.UiText

/**
 * The headline: what the whole session came to.
 *
 * [isOpen] is why this screen does not read as a completion certificate. A
 * workout has no finish trigger — the boundary is inferred when the next set
 * starts — so within `WORKOUT_GAP` of the last set this is "today's session so
 * far", and after it, a finished workout. Derived from the data rather than from
 * which screen navigated here, so it is right both straight after a set and
 * three weeks later in history.
 *
 * Counts stay counts: they are always the same resource, and the screen needs
 * them as numbers anyway. Only [duration] is a [UiText], because picking seconds
 * against minutes against hours is a real choice.
 */
internal data class SessionTotalsUiModel(
    val setCount: Int,
    val repCount: Int,
    val repsAtDepth: Int,
    val duration: UiText,
    val isOpen: Boolean,
)
