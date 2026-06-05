package com.example.applet_carrier.core

import com.example.applet_carrier.api.Applet

/**
 * Holds the hard-coded set of applets (no runtime plugin loading yet). Order here is
 * the sidebar order.
 */
class AppletRegistry(val applets: List<Applet>) {
    init {
        val dupes = applets.groupBy { it.metadata.id }.filterValues { it.size > 1 }.keys
        require(dupes.isEmpty()) { "Duplicate applet ids: $dupes" }
    }

    fun byId(id: String): Applet? = applets.firstOrNull { it.metadata.id == id }
}
