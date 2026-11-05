plugins { alias(libs.plugins.kotlinMultiplatform) }
kotlin {
    js { browser { commonWebpackConfig { outputFileName = "rover-worker.js" } }; binaries.executable() }
    sourceSets {
        commonMain {
            dependencies { implementation(libs.kotlinx.serialization.json) }
            // Compile the same pure Kotlin engine without Compose/DOM dependencies.
            kotlin.srcDir("../shared/src/commonMain/kotlin")
            kotlin.include("sfg/wseminar/rover/terrain/**", "sfg/wseminar/rover/algorithms/**", "sfg/wseminar/rover/graph/**",
                "sfg/wseminar/rover/simulation/**", "sfg/wseminar/rover/SimulationFrame.kt",
                "sfg/wseminar/rover/ui/states/SpaceMode.kt", "sfg/wseminar/rover/ui/states/ExplorationMode.kt",
                "sfg/wseminar/rover/computation/wire/**", "sfg/wseminar/rover/export/RasterPdf.kt")
        }
        jsMain { kotlin.srcDir("../shared/src/webMain/kotlin/sfg/wseminar/rover/algorithms/backend") }
    }
}
