package com.replens.feature.workout

import com.replens.core.model.PoseFrame

/**
 * Everything the workout screen renders. Rep count, rep phase, form warnings and
 * the framing/setup check land here as the exercise logic arrives.
 */
data class WorkoutUiState(
    val poseFrame: PoseFrame? = null,
)
