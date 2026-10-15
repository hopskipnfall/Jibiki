plugins {
    id("jibiki.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            api(projects.core.japanese)
            api(projects.core.yomitan)
            api(projects.core.db)
            api(projects.core.radicals)
            implementation(libs.coroutines.core)
            implementation(libs.serialization.json)
        }
        jvmTest.dependencies {
            implementation(libs.sqlite.bundled)
            implementation(libs.coroutines.test)
        }
    }
}
