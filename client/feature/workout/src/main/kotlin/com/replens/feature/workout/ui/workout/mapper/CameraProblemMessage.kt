package com.replens.feature.workout.ui.workout.mapper

import androidx.annotation.StringRes
import com.replens.feature.workout.R
import com.replens.feature.workout.ui.workout.model.CameraProblem

@get:StringRes
internal val CameraProblem.titleRes: Int
    get() = when (this) {
        CameraProblem.PermissionMissing -> R.string.workout_camera_blocked_title
        CameraProblem.InUse -> R.string.workout_camera_in_use_title
        CameraProblem.Broken -> R.string.workout_camera_broken_title
    }

@get:StringRes
internal val CameraProblem.messageRes: Int
    get() = when (this) {
        CameraProblem.PermissionMissing -> R.string.workout_camera_blocked_message
        CameraProblem.InUse -> R.string.workout_camera_in_use_message
        CameraProblem.Broken -> R.string.workout_camera_broken_message
    }

@get:StringRes
internal val CameraProblem.actionRes: Int
    get() = when (this) {
        CameraProblem.PermissionMissing -> R.string.workout_camera_open_settings
        CameraProblem.InUse, CameraProblem.Broken -> R.string.workout_camera_go_back
    }
