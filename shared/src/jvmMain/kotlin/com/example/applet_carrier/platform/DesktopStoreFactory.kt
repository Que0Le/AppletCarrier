package com.example.applet_carrier.platform

import com.example.applet_carrier.api.ConfigStore
import com.example.applet_carrier.api.StateStore
import com.example.applet_carrier.core.StoreFactory
import java.io.File

/**
 * [StoreFactory] that writes one JSON file per applet/concern to the user's Desktop
 * (AGENTS.md §5). The Desktop directory is the single place to change the storage
 * location later.
 */
class DesktopStoreFactory : StoreFactory {

    private val baseDir: File = File(System.getProperty("user.home"), "Desktop")

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
