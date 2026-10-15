plugins {
    id("jibiki.android-app")
}

android {
    namespace = "dev.jibiki.android"
    defaultConfig {
        applicationId = "dev.jibiki"
        versionCode = 1
        versionName = "0.1.0"
    }
}

dependencies {
    implementation(projects.app)
    implementation(libs.activity.compose)
    implementation(libs.sqlite.bundled)
}
