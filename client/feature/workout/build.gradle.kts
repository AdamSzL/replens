plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.android.compose)
    alias(libs.plugins.replens.hilt)
}

android {
    namespace = "com.replens.feature.workout"
}

dependencies {
    implementation(projects.core.designsystem)
    implementation(projects.core.exercise)
    implementation(projects.core.pose)
    implementation(projects.core.posemath)

    implementation(libs.androidx.camera.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
}
