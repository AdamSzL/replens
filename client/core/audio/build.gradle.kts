plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.hilt)
}

android {
    namespace = "com.replens.core.audio"
}

dependencies {
    // Speaker takes a UiText, so it is part of the API.
    api(projects.core.ui)
}
