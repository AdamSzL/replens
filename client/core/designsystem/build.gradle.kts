plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.android.compose)
}

android {
    namespace = "com.replens.core.designsystem"
}

dependencies {
    // This module is the app's Compose gateway: UI modules depend on it and get
    // the toolkit with it, so the surface is declared once, here.
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.runtime)
    api(libs.androidx.compose.ui)
    api(libs.androidx.compose.ui.graphics)

    // @DrawableRes on RepLensIcons. Not `api`: the annotation is CLASS-retention,
    // so consumers don't need it to compile.
    implementation(libs.androidx.annotation)
}
