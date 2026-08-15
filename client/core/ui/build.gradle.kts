plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.android.compose)
}

android {
    namespace = "com.replens.core.ui"
}

dependencies {
    api(projects.core.text)
    api(libs.androidx.compose.animation)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.kotlinx.coroutines.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
}
