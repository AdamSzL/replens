plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.android.compose)
}

android {
    namespace = "com.replens.core.ui"
}

dependencies {
    // UiText appears in public signatures and resolves against Compose or a
    // Context, so both are part of the API.
    api(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
}
