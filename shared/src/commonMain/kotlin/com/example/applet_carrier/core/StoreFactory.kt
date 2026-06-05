package com.example.applet_carrier.core

import com.example.applet_carrier.api.ConfigStore
import com.example.applet_carrier.api.StateStore

/**
 * Creates persistence stores. The desktop implementation backs each store with a JSON
 * file on the user's Desktop (AGENTS.md §5). Kept as an interface so the storage
 * location/format is a single swappable seam.
 */
interface StoreFactory {
    fun appletState(appletId: String): StateStore
    fun appletConfig(appletId: String): ConfigStore
    fun hostConfig(): ConfigStore
}
