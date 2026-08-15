package com.replens.feature.history.ui.model

import com.replens.core.text.UiText

/**
 * One set, as a row.
 *
 * [restBefore] is why this type exists: it is this set's start minus the previous
 * set's end, so it cannot be worked out from a set alone and a row composable has
 * no way to reach its neighbor.
 *
 * [abandonedCount] stays a number and stays zero rather than going null — whether
 * to draw a line for it is a rendering rule, not state.
 */
internal data class SetRowUiModel(
    val id: Long,
    val index: Int,
    /** Null when every set in the workout is the same exercise: repeating it says nothing. */
    val exercise: UiText?,
    val repCount: Int,
    val repsAtDepth: Int,
    val duration: UiText,
    /** Null on the first set of the workout, which follows nothing. */
    val restBefore: UiText?,
    val abandonedCount: Int,
)
