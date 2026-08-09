package com.replens.core.audio

import com.replens.core.ui.UiText

/**
 * Speaks a short coaching cue, or says nothing at all.
 *
 * Speech is a device capability, not a guarantee: an engine may be absent, still
 * initialising, or have no voice for any language this app has strings for. Every
 * one of those ends in silence rather than an error, so **a cue must also exist on
 * screen** — the app has to work for someone who cannot hear it, and for someone
 * whose phone simply cannot say it.
 *
 * Takes a [UiText] rather than a `String` because resolving it is the speaker's
 * job: only it knows which locale the engine actually accepted, and text resolved
 * against any other locale gets read with the wrong phonetics.
 */
interface Speaker {

    /** Replaces anything currently being said; a late cue is worse than silence. */
    fun speak(cue: UiText)
}
