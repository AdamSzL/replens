package com.replens.feature.workout.ui.workout.mapper

import com.replens.core.exercise.SessionState
import com.replens.core.exercise.SetupCheck
import com.replens.core.text.UiText
import com.replens.feature.workout.R
import com.replens.feature.workout.ui.workout.model.SpokenCue
import kotlin.time.Duration.Companion.seconds

/**
 * Long enough that the same instruction twice reads as patience rather than
 * nagging, short enough to catch someone who walked out of position and did not
 * hear the first one. Nothing measured this; it is a guess to be listened to.
 */
private val SETUP_REPEAT = 4.seconds

/**
 * What the screen says while the user gets into position.
 *
 * Chosen here rather than in the composable now that the same line is also
 * spoken: a `stringResource` call can only be made in a composition, and the
 * speaker resolves against the locale its *voice* got, which is not necessarily
 * the app's. Returning [UiText] leaves both resolutions to their own caller.
 */
internal val SetupCheck.message: UiText
    get() = when (this) {
        SetupCheck.READY -> UiText.Resource(R.string.workout_setup_hold_still)
        SetupCheck.NO_POSE -> UiText.Resource(R.string.workout_setup_no_pose)
        SetupCheck.TOO_CLOSE -> UiText.Resource(R.string.workout_setup_too_close)
        SetupCheck.LEGS_NOT_VISIBLE -> UiText.Resource(R.string.workout_setup_legs_not_visible)
    }

/**
 * What to say on entering this state, or null to stay quiet.
 *
 * This is the whole point of the audio channel: from three meters away the screen
 * is unreadable, so a set has to be startable and finishable by ear alone.
 *
 * [repsAtDepth] is read only by [SessionState.Finished]. [repCount] is also what
 * [SessionState.Active] counts out loud, which is the one cue the app repeats
 * often enough for a listener to build a rhythm from.
 */
internal fun SessionState.spokenCue(repCount: Int, repsAtDepth: Int): SpokenCue? = when (this) {
    SessionState.Idle -> null

    // READY is drawn but never spoken. It holds for `SetSession.settleFor` — half
    // a second — and the count-in that follows flushes whatever is still being
    // said, so "hold still" would only ever be heard cut off mid-word. The
    // count-in starting is itself the confirmation that line was going to give.
    is SessionState.Waiting -> reason
        .takeIf { it != SetupCheck.READY }
        ?.let { SpokenCue(it.message, repeatAfter = SETUP_REPEAT) }

    is SessionState.CountingIn -> SpokenCue(
        UiText.Resource(R.string.workout_cue_count_in, secondsLeft),
    )

    // Active carries no fields, so a zero count is the only thing here that can
    // mean "the set has just started" — and it works only because the count never
    // goes down. "Go" is therefore one unchanging cue until the first rep, which
    // is what gets it spoken exactly once rather than every frame.
    SessionState.Active -> SpokenCue(
        if (repCount == 0) UiText.Resource(R.string.workout_cue_go)
        else UiText.Resource(R.string.workout_cue_rep, repCount),
    )

    SessionState.Finished -> SpokenCue(
        UiText.Plural(
            id = R.plurals.workout_cue_set_summary,
            quantity = repCount,
            args = listOf(repCount, repsAtDepth),
        ),
    )
}
