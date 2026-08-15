plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.android.compose)
    alias(libs.plugins.replens.hilt)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.replens.feature.history"
}

dependencies {
    implementation(projects.core.data)
    implementation(projects.core.designsystem)
    implementation(projects.core.exercise)
    implementation(projects.core.ui)

    api(libs.androidx.navigation3.runtime)
    implementation(libs.kotlinx.serialization.core)

    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
