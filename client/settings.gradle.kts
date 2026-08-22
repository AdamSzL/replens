pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "RepLens"
include(":app")
include(":core:audio")
include(":core:designsystem")
include(":core:model")
include(":core:pose")
include(":core:posemath")
include(":core:testing")
include(":core:text")
include(":core:ui")
include(":core:data")
include(":core:database")
include(":core:datastore")
include(":core:exercise")
include(":feature:history")
include(":feature:workout")
 