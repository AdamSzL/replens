package com.replens.feature.workout.ui.workout

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/**
 * A distinct owner per call, because that is what a real return produces:
 * Navigation 3 destroys an entry's owner along with its composition and builds a
 * new one on the way back. Reusing one instance would hide the rebind the camera
 * has to perform.
 *
 * The fake camera never binds, so nothing may read the lifecycle itself.
 */
internal fun fakeScreenLifecycleOwner(): LifecycleOwner = object : LifecycleOwner {
    override val lifecycle: Lifecycle get() = error("the fake camera does not bind")
}
