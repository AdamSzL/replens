package com.replens.core.exercise

import com.replens.core.model.Rep
import com.replens.core.model.RepPhase
import com.replens.core.model.RepUpdate

/**
 * Something worth correcting, named rather than worded — the words are chosen in
 * the feature module, because this module is pure Kotlin and `UiText` is Android.
 * Same split as [SetupCheck], and for the same reason.
 *
 * Everything here is decided **after** a rep, off a [RepUpdate] the counter has
 * already produced, which is why neither arm needs a threshold of its own or a
 * latency budget. The live rules — valgus, forward lean, heel lift — need research
 * and tuning against footage, and two of them need a vertical reference that only
 * the setup check can vouch for.
 *
 * Whether this vocabulary generalizes past the squat is **unproven**, exactly as
 * for [Rep] and [RepPhase]. Exercise #2 decides it.
 */
enum class FormFault {
    /** It counted — the count is deliberately lenient — but it was short of parallel. */
    SHALLOW_REP,

    /** Turned around before reaching depth, so nothing counted at all. */
    ABANDONED_DESCENT,
}
