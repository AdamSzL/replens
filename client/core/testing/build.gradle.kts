plugins {
    alias(libs.plugins.replens.android.library)
}

android {
    namespace = "com.replens.core.testing"
}

dependencies {
    // Everything here is in a public signature: WorkoutRepository as a supertype,
    // TestWatcher as a supertype, CompletableDeferred as a property type, and
    // TestScope as a receiver.
    api(projects.core.data)
    api(libs.junit)
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.test)
}