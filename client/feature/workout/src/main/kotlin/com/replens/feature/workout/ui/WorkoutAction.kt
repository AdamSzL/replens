package com.replens.feature.workout.ui

import androidx.lifecycle.LifecycleOwner

internal sealed interface WorkoutAction {

    /**
     * The screen entered composition, carrying the owner the camera binds to.
     * Composition, not visibility: backgrounding leaves the screen composed and
     * CameraX already unbinds on the lifecycle. This fires when the screen is
     * built — including on the way back from the summary, which hands over a new
     * owner because the previous one was destroyed with its composition.
     */
    data class ScreenAttached(val lifecycleOwner: LifecycleOwner) : WorkoutAction

    data object ScreenDetached : WorkoutAction

    data class ZoomSelected(val ratio: Float) : WorkoutAction

    data object CameraFlipClicked : WorkoutAction

    data object StartClicked : WorkoutAction

    /** Backing out of [SessionState.Waiting] or [SessionState.CountingIn]. */
    data object CancelClicked : WorkoutAction

    data object FinishClicked : WorkoutAction

    data object DoneClicked : WorkoutAction

    data object GoAgainClicked : WorkoutAction
}
