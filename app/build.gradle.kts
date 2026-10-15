plugins {
    id("jibiki.compose-library")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.domain)
            api(libs.compose.runtime)
            api(libs.compose.foundation)
            api(libs.compose.ui)
            api(libs.compose.material3)
            implementation(libs.compose.resources)
            implementation(libs.compose.icons.core)
            implementation(libs.coroutines.core)
        }
    }
}

compose.resources {
    publicResClass = false
    packageOfResClass = "dev.jibiki.app.resources"
}
