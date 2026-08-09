package com.replens.core.exercise

/**
 * Where the user is in a set.
 *
 * This exists because [Framing] only rejects frames that cannot be real — it says
 * nothing about someone at a perfectly normal distance who simply is not working
 * out. Walking to the spot, resting, bending to move a chair: all plausible, none
 * of them reps. Only [Active] counts.
 *
 * [Waiting] is not a countdown that has not started yet. It has no duration — it
 * ends when [SetupCheck] sees the user in position, so the walk out never burns a
 * timer that was only ever a guess at how long that walk takes.
 */
sealed interface SessionState {

    data object Idle : SessionState

    /** Start pressed, but not in position yet — [reason] says why. */
    data class Waiting(val reason: SetupCheck) : SessionState

    data class CountingIn(val secondsLeft: Int) : SessionState

    data object Active : SessionState

    /** The rep count lives outside this machine, so nothing here discards it. */
    data object Finished : SessionState
}
