plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.hilt)
}

android {
    namespace = "com.replens.core.datastore"
}

dependencies {
    api(libs.androidx.datastore.preferences)
}
