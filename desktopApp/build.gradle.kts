import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

val releaseVersion = providers.gradleProperty("appVersion").orElse("1.0.0")

val distributionLicenses = tasks.register<Sync>("prepareDistributionLicenses") {
    from(rootProject.file("LICENSE")) { into("common/legal") }
    from(rootProject.file("licenses")) { into("common/legal/licenses") }
    into(layout.buildDirectory.dir("generated/distributionResources"))
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "sfg.wseminar.rover.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WSMarsRoverSim"
            vendor = "Lucas Wittmann"
            copyright = "Copyright © 2026 Lucas Wittmann"
            licenseFile.set(rootProject.layout.projectDirectory.file("LICENSE"))
            appResourcesRootDir.set(layout.dir(distributionLicenses.map { it.destinationDir }))
            packageVersion = releaseVersion.get()
            description = "Mars rover exploration and pathfinding visualizer"
            // Include the runtime modules used reflectively by Compose, Skia and file dialogs.
            includeAllModules = true
            macOS { bundleID = "sfg.wseminar.rover" }
            linux {
                packageName = "wsmarsroversim"
                shortcut = true
            }
            windows {
                shortcut = true
                menu = true
                perUserInstall = true
                upgradeUuid = "8b362735-7985-442d-95c4-9bcc3c2422d2"
            }
        }
    }
}
