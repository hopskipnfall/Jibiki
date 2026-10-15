import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import javax.inject.Inject

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
abstract class SyncLocalNpmPackages @Inject constructor(private val fs: FileSystemOperations) : DefaultTask() {
    @get:Internal abstract val sources: MapProperty<String, Directory>
    @get:Internal abstract val target: DirectoryProperty

    @TaskAction
    fun run() {
        // Declared as no outputs on purpose: node_modules belongs to kotlinWasmNpmInstall, and Gradle
        // would delete it as a "stale output" of this task on a clean build.
        sources.get().forEach { (name, dir) ->
            fs.copy {
                from(dir)
                into(target.dir(name))
            }
        }
    }
}

val syncLocalNpmPackages by tasks.registering(SyncLocalNpmPackages::class) {
    sources.put("jibiki-sqlite-worker", layout.projectDirectory.dir("sqlite-worker"))
    sources.put("jibiki-web", layout.projectDirectory.dir("web-helpers"))
    target.set(rootProject.layout.buildDirectory.dir("wasm/node_modules"))
    outputs.upToDateWhen { false }
    mustRunAfter(":kotlinWasmNpmInstall")
}
tasks.matching { it.name.startsWith("wasmJsBrowser") && (it.name.endsWith("Webpack") || it.name.endsWith("Run")) }
    .configureEach { dependsOn(syncLocalNpmPackages) }
