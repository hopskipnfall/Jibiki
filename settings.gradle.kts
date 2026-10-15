rootProject.name = "jibiki"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

include(
    ":core:model",
    ":core:japanese",
    ":core:yomitan",
    ":core:db",
    ":core:radicals",
    ":domain",
    ":app",
    ":androidApp",
    ":webApp",
)
