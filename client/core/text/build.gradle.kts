plugins {
    alias(libs.plugins.replens.android.library)
}

android {
    namespace = "com.replens.core.text"
}

dependencies {
    implementation(libs.androidx.annotation)

    testImplementation(libs.junit)
}
