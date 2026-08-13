package com.replens.feature.workout.ui.mapper

import com.replens.core.exercise.FormFault
import com.replens.core.ui.UiText
import com.replens.feature.workout.R
import com.replens.feature.workout.ui.model.SpokenCue

/**
 * What to say about a fault.
 *
 * No `repeatAfter` on either: [com.replens.feature.workout.ui.FormCueSelector]
 * already answers how often a correction is worth making, and a repeat interval
 * here would be a second answer to the same question that could disagree with it.
 */
internal val FormFault.spokenCue: SpokenCue
    get() = SpokenCue(
        when (this) {
            FormFault.SHALLOW_REP -> UiText.Resource(R.string.workout_cue_go_deeper)
            FormFault.ABANDONED_DESCENT ->
                UiText.Resource(R.string.workout_cue_all_the_way_down)
        },
    )
