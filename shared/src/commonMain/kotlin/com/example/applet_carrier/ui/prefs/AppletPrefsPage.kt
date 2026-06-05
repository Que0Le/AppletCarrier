package com.example.applet_carrier.ui.prefs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet

/**
 * Wraps an applet's own [Applet.PrefsUi]. The applet has full authority over the form
 * body; the shell only supplies the section title.
 */
@Composable
fun AppletPrefsPage(applet: Applet) {
    Column {
        PrefsSectionTitle(applet.metadata.displayName)
        applet.PrefsUi()
    }
}

/** Placeholder shown when the "Applet Settings" group header itself is selected. */
@Composable
fun AppletSettingsPlaceholder() {
    Column {
        PrefsSectionTitle("Applet Settings")
        Spacer(Modifier.height(4.dp))
        Text("Select an applet on the left to edit its settings.")
    }
}
