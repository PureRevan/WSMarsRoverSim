plugins {
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
}

tasks.register("buildOptimizedWasm") {
    group = "distribution"
    description = "Build the optimized Wasm site in webApp/build/distributions/wasm."
    dependsOn(":webApp:buildOptimizedWasm")
}
