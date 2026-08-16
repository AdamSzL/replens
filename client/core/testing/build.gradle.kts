plugins {
    alias(libs.plugins.replens.android.library)
}

android {
    namespace = "com.replens.core.testing"
}

dependencies {
    // Everything here is in a public signature: WorkoutRepository as a supertype,
    // TestWatcher as a supertype, CompletableDeferred as a property type.
    api(projects.core.data)
    api(libs.junit)
    api(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.coroutines.test)
}