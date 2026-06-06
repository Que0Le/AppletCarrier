package com.example.applet_carrier.platform

import com.example.applet_carrier.api.ConfigStore
import com.example.applet_carrier.api.StateStore
import com.example.applet_carrier.core.StoreFactory
import java.io.File

/**
 * [StoreFactory] that writes one JSON file per applet/concern to `~/.applet_carrier`
 * (cross-platform — under the user's home directory on Windows, macOS, and Linux). The
 * directory is created lazily on first write. This is the single place to change the
 * storage location (AGENTS.md §5).
 */
class AppDirStoreFactory : StoreFactory {

    private val baseDir: File = File(System.getProperty("user.home"), ".applet_carrier")

    override fun appletState(appletId: String): StateStore =
        JsonStore(File(baseDir, "${normalizeAscii(appletId)}-state.json"))

    override fun appletConfig(appletId: String): ConfigStore =
        JsonStore(File(baseDir, "${normalizeAscii(appletId)}-config.json"))

    override fun hostConfig(): ConfigStore =
        JsonStore(File(baseDir, "host-config.json"))
}

/** Normalize a name into a safe ASCII file stem: lowercase, spaces→hyphens, drop the rest. */
internal fun normalizeAscii(name: String): String =
    name.lowercase()
        .replace(Regex("\\s+"), "-")
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '-' || it == '_' }
        .trim('-')
        .ifBlank { "applet" }
