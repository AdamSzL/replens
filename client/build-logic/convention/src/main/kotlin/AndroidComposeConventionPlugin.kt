import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Additive Compose support: apply on top of replens.android.application or
 * replens.android.library. Modules without UI must not apply it.
 */
class AndroidComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

            val android: CommonExtension =
                extensions.findByType(ApplicationExtension::class.java)
                    ?: extensions.getByType(LibraryExtension::class.java)
            android.buildFeatures.compose = true

            // Core modules stay Compose-free, so types they own cannot carry
            // @Immutable. Declaring them here is the alternative to giving
            // :core:model and :core:exercise a Compose dependency.
            extensions.configure<ComposeCompilerGradlePluginExtension> {
                stabilityConfigurationFiles.add(
                    rootProject.layout.projectDirectory.file("compose-stability.conf")
                )
            }

            dependencies {
                val bom = libs.findLibrary("androidx-compose-bom").get()
                add("implementation", platform(bom))
                add("androidTestImplementation", platform(bom))

                // Previews work everywhere; the Compose toolkit itself comes from
                // :core:designsystem, which re-exports it.
                add(
                    "implementation",
                    libs.findLibrary("androidx-compose-ui-tooling-preview").get(),
                )
                add("debugImplementation", libs.findLibrary("androidx-compose-ui-tooling").get())
            }
        }
    }
}
