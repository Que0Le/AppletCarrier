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
        }
    }
}

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