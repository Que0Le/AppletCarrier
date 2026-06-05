package com.example.applet_carrier.ui.prefs

import com.example.applet_carrier.api.Applet

/** A selectable node in the preferences tree. */
sealed interface PrefsNode {
    /** Built-in host settings. */
    data object General : PrefsNode

    /** The "Applet Settings" group header (no settings of its own). */
    data object AppletSettingsGroup : PrefsNode

    /** One applet's settings page (child of [AppletSettingsGroup]). */
    data class AppletPrefs(val applet: Applet) : PrefsNode
}
