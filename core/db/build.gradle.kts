plugins {
    id("jibiki.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.yomitan)
            api(libs.sqlite)
            api(libs.sqlite.async)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.sqlite.bundled)
            implementation(libs.coroutines.test)
        }
    }
}
