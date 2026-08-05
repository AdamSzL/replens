package com.replens.core.pose

import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import com.replens.core.model.BodyPose
import com.replens.core.model.Landmark
import com.replens.core.model.LandmarkType

/**
 * The ML Kit -> domain boundary. ML Kit types must not escape this module;
 * everything downstream speaks [BodyPose].
 */
internal fun Pose.toBodyPose(): BodyPose {
    val landmarks = allPoseLandmarks.mapNotNull { landmark ->
        val type = landmark.landmarkType.toLandmarkType() ?: return@mapNotNull null
        Landmark(
            type = type,
            x = landmark.position3D.x,
            y = landmark.position3D.y,
            z = landmark.position3D.z,
            inFrameLikelihood = landmark.inFrameLikelihood,
        )
    }
    return BodyPose(landmarks)
}

private fun Int.toLandmarkType(): LandmarkType? = when (this) {
    PoseLandmark.NOSE -> LandmarkType.NOSE
    PoseLandmark.LEFT_EYE_INNER -> LandmarkType.LEFT_EYE_INNER
    PoseLandmark.LEFT_EYE -> LandmarkType.LEFT_EYE
    PoseLandmark.LEFT_EYE_OUTER -> LandmarkType.LEFT_EYE_OUTER
    PoseLandmark.RIGHT_EYE_INNER -> LandmarkType.RIGHT_EYE_INNER
    PoseLandmark.RIGHT_EYE -> LandmarkType.RIGHT_EYE
    PoseLandmark.RIGHT_EYE_OUTER -> LandmarkType.RIGHT_EYE_OUTER
    PoseLandmark.LEFT_EAR -> LandmarkType.LEFT_EAR
    PoseLandmark.RIGHT_EAR -> LandmarkType.RIGHT_EAR
    PoseLandmark.LEFT_MOUTH -> LandmarkType.LEFT_MOUTH
    PoseLandmark.RIGHT_MOUTH -> LandmarkType.RIGHT_MOUTH
    PoseLandmark.LEFT_SHOULDER -> LandmarkType.LEFT_SHOULDER
    PoseLandmark.RIGHT_SHOULDER -> LandmarkType.RIGHT_SHOULDER
    PoseLandmark.LEFT_ELBOW -> LandmarkType.LEFT_ELBOW
    PoseLandmark.RIGHT_ELBOW -> LandmarkType.RIGHT_ELBOW
    PoseLandmark.LEFT_WRIST -> LandmarkType.LEFT_WRIST
    PoseLandmark.RIGHT_WRIST -> LandmarkType.RIGHT_WRIST
    PoseLandmark.LEFT_PINKY -> LandmarkType.LEFT_PINKY
    PoseLandmark.RIGHT_PINKY -> LandmarkType.RIGHT_PINKY
    PoseLandmark.LEFT_INDEX -> LandmarkType.LEFT_INDEX
    PoseLandmark.RIGHT_INDEX -> LandmarkType.RIGHT_INDEX
    PoseLandmark.LEFT_THUMB -> LandmarkType.LEFT_THUMB
    PoseLandmark.RIGHT_THUMB -> LandmarkType.RIGHT_THUMB
    PoseLandmark.LEFT_HIP -> LandmarkType.LEFT_HIP
    PoseLandmark.RIGHT_HIP -> LandmarkType.RIGHT_HIP
    PoseLandmark.LEFT_KNEE -> LandmarkType.LEFT_KNEE
    PoseLandmark.RIGHT_KNEE -> LandmarkType.RIGHT_KNEE
    PoseLandmark.LEFT_ANKLE -> LandmarkType.LEFT_ANKLE
    PoseLandmark.RIGHT_ANKLE -> LandmarkType.RIGHT_ANKLE
    PoseLandmark.LEFT_HEEL -> LandmarkType.LEFT_HEEL
    PoseLandmark.RIGHT_HEEL -> LandmarkType.RIGHT_HEEL
    PoseLandmark.LEFT_FOOT_INDEX -> LandmarkType.LEFT_FOOT_INDEX
    PoseLandmark.RIGHT_FOOT_INDEX -> LandmarkType.RIGHT_FOOT_INDEX
    else -> null
}
