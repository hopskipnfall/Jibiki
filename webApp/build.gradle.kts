import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    id("jibiki.web-app")
}

kotlin {
    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        outputModuleName = "jibiki"
        browser {
            commonWebpackConfig {
                outputFileName = "jibiki.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        wasmJsMain.dependencies {
            implementation(projects.app)
            implementation(libs.sqlite.web)
            implementation(libs.compose.resources)
            implementation(libs.kotlinx.browser)
            implementation(libs.coroutines.core)
            implementation(npm("@sqlite.org/sqlite-wasm", "3.53.4-build2"))
            implementation(npm("fflate", "0.8.3"))
            implementation(npm("jibiki-sqlite-worker", layout.projectDirectory.dir("sqlite-worker").asFile))
            implementation(npm("jibiki-web", layout.projectDirectory.dir("web-helpers").asFile))
        }
    }
}

compose.resources {
    packageOfResClass = "dev.jibiki.web.resources"
    generateResClass = always
}

// Local npm packages are copied into node_modules only on (re)install, so keep them in sync
// with the sources before every webpack run.
val syncLocalNpmPackages by tasks.registering(Copy::class) {
    from(layout.projectDirectory.dir("sqlite-worker")) { into("jibiki-sqlite-worker") }
    from(layout.projectDirectory.dir("web-helpers")) { into("jibiki-web") }
    into(rootProject.layout.buildDirectory.dir("wasm/node_modules"))
    mustRunAfter(":kotlinWasmNpmInstall")
}
tasks.matching { it.name.startsWith("wasmJsBrowser") && (it.name.endsWith("Webpack") || it.name.endsWith("Run")) }
    .configureEach { dependsOn(syncLocalNpmPackages) }
