plugins {
    id("jibiki.kmp-library")
    id("jibiki.serialization")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.core.model)
            implementation(libs.serialization.json)
            implementation(libs.coroutines.core)
        }
        val jvmAndAndroidMain by creating {
            dependsOn(commonMain.get())
        }
        jvmMain.get().dependsOn(jvmAndAndroidMain)
        androidMain.get().dependsOn(jvmAndAndroidMain)
        jvmTest.dependencies {
            implementation(libs.coroutines.test)
        }
    }
}
