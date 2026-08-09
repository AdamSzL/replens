package com.replens.feature.workout.ui

import androidx.compose.runtime.Immutable
import com.replens.core.exercise.SetupCheck

/**
 * Where the user is in a set.
 *
 * This exists because the framing gate only rejects frames that are physically
 * impossible — it says nothing about someone standing at a perfectly normal
 * distance who simply is not working out. Walking to the spot, resting, bending
 * to move a chair: all plausible, none of them reps. Only [Active] feeds the
 * counter.
 *
 * [Waiting] is not a countdown that has not started yet. It has no duration —
 * it ends when the camera can actually see you in position, so the walk out
 * never burns a timer that was a guess at how long the walk takes.
 */
@Immutable
internal sealed interface SessionState {

    data object Idle : SessionState

    /** Start pressed, but you are not in position yet — [reason] says why. */
    data class Waiting(val reason: SetupCheck) : SessionState

    data class CountingIn(val secondsLeft: Int) : SessionState

    data object Active : SessionState

    /** The count is still in `WorkoutState.repCount`; nothing is reset until the next set. */
    data object Finished : SessionState
}
