plugins {
    id("jibiki.kmp-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.japanese)
        }
    }
}
