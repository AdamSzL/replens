plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.hilt)
}

android {
    namespace = "com.replens.core.audio"
}

dependencies {
    api(projects.core.text)
}
