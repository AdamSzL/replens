import com.android.build.api.dsl.CommonExtension
import org.gradle.api.JavaVersion

/**
 * Android config shared by every module, application and library alike.
 * Kotlin's jvmTarget follows compileOptions automatically (AGP built-in Kotlin),
 * so Java 21 here configures both compilers.
 */
internal fun configureCommonAndroid(extension: CommonExtension) {
    extension.apply {
        compileSdk {
            version = release(37)
        }

        defaultConfig.minSdk = 26

        compileOptions.sourceCompatibility = JavaVersion.VERSION_21
        compileOptions.targetCompatibility = JavaVersion.VERSION_21
    }
}
