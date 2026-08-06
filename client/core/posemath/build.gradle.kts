plugins {
    alias(libs.plugins.replens.jvm.library)
}

dependencies {
    // Landmark / BodyPose appear in this module's public signatures.
    api(projects.core.model)

    testImplementation(libs.junit)
}
