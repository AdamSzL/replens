package com.replens.feature.workout.ui.picker.mapper

import androidx.annotation.StringRes
import com.replens.core.model.Exercise
import com.replens.feature.workout.R

@get:StringRes
internal val Exercise.labelRes: Int
    get() = when (this) {
        Exercise.SQUAT -> R.string.exercise_squat
    }
