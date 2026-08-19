plugins {
    alias(libs.plugins.replens.android.application)
    alias(libs.plugins.replens.android.compose)
    alias(libs.plugins.replens.hilt)
}

android {
    namespace = "com.replens.app"

    defaultConfig {
        applicationId = "com.replens.app"
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.savedstate.compose)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.history)
    implementation(projects.feature.workout)

    testImplementation(libs.junit)
}
