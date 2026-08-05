plugins {
    alias(libs.plugins.replens.android.library)
}

android {
    namespace = "com.replens.core.pose"
}

dependencies {
    api(projects.core.model)
    // SurfaceRequest and LifecycleOwner appear in this module's public API.
    api(libs.androidx.camera.core)
    api(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.mlkit.pose.detection.accurate)
    implementation(libs.kotlinx.coroutines.android)
}
