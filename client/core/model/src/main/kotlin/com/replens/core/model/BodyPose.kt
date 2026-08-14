package com.replens.core.model

/**
 * A full-body pose at one instant: whatever subset of the 33 landmarks the
 * detector produced.
 */
data class BodyPose(
    private val landmarks: Map<LandmarkType, Landmark>,
) {
    constructor(landmarks: Collection<Landmark>) :
        this(landmarks.associateBy(Landmark::type))

    val all: Collection<Landmark> get() = landmarks.values

    operator fun get(type: LandmarkType): Landmark? = landmarks[type]

    companion object {
        val Empty = BodyPose(emptyMap())
    }
}
