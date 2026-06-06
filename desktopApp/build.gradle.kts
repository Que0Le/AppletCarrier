import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(projects.shared)

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.example.applet_carrier.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Exe, TargetFormat.Deb)
            packageName = "AppletCarrier"
            packageVersion = "1.0.0"

            // Per-platform launcher / installer icons. Guarded by exists() so packaging
            // never breaks before the image files are added — see desktopApp/icons/README.md.
            val iconsDir = project.file("icons")
            windows {
                val ico = iconsDir.resolve("icon.ico")
                if (ico.exists()) iconFile.set(ico)
            }
            macOS {
                val icns = iconsDir.resolve("icon.icns")
                if (icns.exists()) iconFile.set(icns)
            }
            linux {
                val png = iconsDir.resolve("icon.png")
                if (png.exists()) iconFile.set(png)
            }
        }
    }
}

/**
 * Version stamping. Writes the app version into a properties file that is bundled as a
 * classpath resource and read at startup by `AppVersion`. Pass `-PappVersion=1.2.3`
 * (the release workflow derives this from the `release_*` tag); defaults to "dev" locally.
 * Note: this is separate from jpackage's `packageVersion`, which stays fixed.
 */
val appVersion: String = (findProperty("appVersion") as String?)?.takeIf { it.isNotBlank() } ?: "dev"

val generateVersionProperties = tasks.register("generateVersionProperties") {
    val outFile = layout.buildDirectory.file("generated/version/version.properties").get().asFile
    val versionValue = appVersion
    inputs.property("version", versionValue)
    outputs.file(outFile)
    doLast {
        outFile.parentFile.mkdirs()
        outFile.writeText("version=$versionValue\n")
    }
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/version"))

tasks.named("processResources") { dependsOn(generateVersionProperties) }

/**
 * Convenience task: builds the standalone app image (AppletCarrier.exe + bundled runtime,
 * no installer, no Java needed on the target). Run it from IntelliJ's Gradle tool window
 * under "applet_carrier > desktopApp > Tasks > distribution > buildAppImage", or via
 * `gradlew :desktopApp:buildAppImage`.
 */
tasks.register("buildAppImage") {
    group = "distribution"
    description = "Builds the standalone AppletCarrier.exe app image (folder with bundled runtime)."
    dependsOn("createDistributable")
    // Resolve to a plain File at configuration time so the doLast closure stays
    // configuration-cache friendly (no script-object references captured).
    val appDir = layout.buildDirectory.dir("compose/binaries/main/app/AppletCarrier").get().asFile
    doLast {
        println("App image ready -> $appDir")
        println("Run AppletCarrier.exe in that folder, or zip the folder to share it.")
    }
}