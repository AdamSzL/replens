plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.hilt)
}

android {
    namespace = "com.replens.core.pose"
}

dependencies {
    // Public API: Flow<PoseFrame> / StateFlow<SurfaceRequest?>, keyed by LifecycleOwner.
    api(projects.core.model)
    api(libs.androidx.camera.core)
    api(libs.androidx.lifecycle.runtime.ktx)
    api(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.mlkit.pose.detection.accurate)
    // CameraInfo.zoomState is LiveData; asFlow() keeps it out of the callback world.
    implementation(libs.androidx.lifecycle.livedata.ktx)

    testImplementation(libs.junit)
}
