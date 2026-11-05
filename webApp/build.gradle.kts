import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl
import org.jetbrains.kotlin.gradle.targets.js.webpack.KotlinWebpack
import org.jetbrains.kotlin.gradle.targets.wasm.binaryen.BinaryenExec

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

@CacheableTask
abstract class GeneratePublishingConfig : DefaultTask() {
    @get:Input abstract val repository: Property<String>
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty

    @TaskAction fun generate() {
        val name = repository.get()
        require(name.isEmpty() || Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+").matches(name)) {
            "publishingRepository must be a GitHub owner/repository pair."
        }
        val output = outputDirectory.file("sfg/wseminar/rover/PublishingConfig.kt").get().asFile
        output.parentFile.mkdirs()
        output.writeText("package sfg.wseminar.rover\ninternal object PublishingConfig { const val repository = \"$name\" }\n")
    }
}

val publishingConfig = tasks.register<GeneratePublishingConfig>("generatePublishingConfig") {
    repository.set(providers.gradleProperty("publishingRepository").orElse(""))
    outputDirectory.set(layout.buildDirectory.dir("generated/publishingConfig"))
}

kotlin {
    js {
        browser()
        binaries.executable()
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(projects.shared)

            implementation(libs.compose.ui)
            implementation(libs.kotlinx.serialization.json)
        }
        wasmJsMain { kotlin.srcDir(publishingConfig) }
    }
}

// Kotlin 2.3 already runs Binaryen for production Wasm. Preserve IEEE behaviour:
// the planners deliberately use infinity for unknown/unreachable costs.
tasks.withType<BinaryenExec>().configureEach {
    if (name.contains("ProductionExecutable")) {
        binaryenArgs.remove("--fast-math")
        binaryenArgs.add("--strip-debug")
    }
}
tasks.withType<KotlinWebpack>().configureEach {
    if (name == "wasmJsBrowserProductionWebpack") sourceMaps = false
}

val optimizedWasm = tasks.register<Sync>("buildOptimizedWasm") {
    group = "distribution"
    description = "Build the optimized Wasm site and worker, ready for GitHub Pages."
    dependsOn("wasmJsBrowserDistribution")
    from(layout.buildDirectory.dir("dist/wasmJs/productionExecutable"))
    exclude("**/*.map", "**/*.wat", "**/.DS_Store")
    into(layout.buildDirectory.dir("distributions/wasm"))
    doLast { destinationDir.resolve(".nojekyll").writeText("") }
}
tasks.register<Zip>("wasmReleaseZip") {
    group = "distribution"
    description = "Archive the optimized Wasm site for a release."
    from(optimizedWasm)
    archiveFileName.set("web-wasm-WSMarsRoverSim.zip")
    destinationDirectory.set(layout.buildDirectory.dir("distributions"))
    isPreserveFileTimestamps = false
    isReproducibleFileOrder = true
}

// Both JS and Wasm pages use the same pure Kotlin/JS worker engine.
val licenseAssets = tasks.register<Sync>("prepareLicenseAssets") {
    from(rootProject.file("LICENSE")) { into("legal") }
    from(rootProject.file("licenses")) { into("legal/licenses") }
    into(layout.buildDirectory.dir("generated/licenseResources"))
}
val workerAssets = tasks.register<Sync>("prepareWorkerAssets") {
    dependsOn(":workerApp:jsBrowserProductionWebpack")
    from(project(":workerApp").layout.buildDirectory.dir("kotlin-webpack/js/productionExecutable")) {
        include("rover-worker.js", "*.LICENSE.txt")
    }
    into(layout.buildDirectory.dir("generated/workerResources"))
}
kotlin.sourceSets.configureEach {
    if (name == "webMain") {
        resources.srcDir(workerAssets)
        resources.srcDir(licenseAssets)
    }
}
