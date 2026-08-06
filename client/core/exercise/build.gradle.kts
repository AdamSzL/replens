plugins {
    alias(libs.plugins.replens.jvm.library)
}

dependencies {
    // BodyPose is the receiver of squatSignals(), so it is part of the API.
    api(projects.core.model)
    implementation(projects.core.posemath)

    testImplementation(libs.junit)
}
