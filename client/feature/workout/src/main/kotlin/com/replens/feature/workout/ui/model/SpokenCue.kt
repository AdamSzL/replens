package com.replens.feature.workout.ui.model

import com.replens.core.text.UiText
import kotlin.time.Duration

/**
 * A line to say out loud, and whether it is worth saying again.
 *
 * The repeat policy belongs to the cue rather than to whoever speaks it, because
 * cues are of two kinds. Some describe a condition that is still true — you are
 * still standing too close — and saying those once is not enough, since the one
 * time it mattered the user was facing away from the phone. Others mark a moment
 * that has passed: repeating "go" four seconds into a set is nonsense.
 *
 * @param repeatAfter how long before the same line may be said again; null says
 *   it exactly once, however long the condition lasts.
 */
internal data class SpokenCue(
    val text: UiText,
    val repeatAfter: Duration? = null,
)
