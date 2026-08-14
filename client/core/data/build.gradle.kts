plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.hilt)
}

android {
    namespace = "com.replens.core.data"
}

dependencies {
    api(projects.core.model)
    api(libs.kotlinx.coroutines.core)

    implementation(projects.core.database)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
