plugins {
    alias(libs.plugins.replens.android.library)
    alias(libs.plugins.replens.hilt)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

android {
    namespace = "com.replens.core.database"
}

room3 {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.sqlite.bundled)

    testImplementation(libs.androidx.sqlite.bundled.jvm)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
