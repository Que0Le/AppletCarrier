package com.example.applet_carrier

import java.util.Properties

/**
 * App version, read once at startup from `/version.properties` on the classpath. That file
 * is generated at build time by the desktopApp `generateVersionProperties` task from the
 * `-PappVersion` property (the release workflow derives it from the `release_*` tag).
 * Falls back to "dev" for local/unstamped builds.
 */
object AppVersion {
    val value: String by lazy {
        runCatching {
            AppVersion::class.java.getResourceAsStream("/version.properties")?.use { stream ->
                Properties().apply { load(stream) }.getProperty("version")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "dev"
    }
}
