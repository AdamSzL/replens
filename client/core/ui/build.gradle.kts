plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.android.compose)
}

android {
    namespace = "com.replens.core.ui"
}

dependencies {
    // Not `api`, even though UiText carries @Immutable and has a @Composable
    // resolver: both annotations are BINARY-retention, and anything calling the
    // composable overload is a UI module with Compose of its own. Exporting it
    // would put a version-less BOM dependency on consumers that have no UI and so
    // no BOM to resolve it — :core:audio is exactly that.
    implementation(libs.androidx.compose.runtime)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
}
