package com.example.applet_carrier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.rememberWindowState
import com.example.applet_carrier.ui.prefs.PrefsContent
import com.example.applet_carrier.ui.shell.ShellRoot
import com.example.applet_carrier.ui.theme.CarrierTheme

/**
 * Declares the application's windows: the main shell window, and the preferences window
 * (opened on demand). Lives at application scope because the prefs page is a separate
 * window (AGENTS.md §3).
 */
@Composable
fun ApplicationScope.CarrierApplication() {
    val runtime = remember { CarrierBootstrap.create() }
    var showPrefs by remember { mutableStateOf(false) }

    val mainState = rememberWindowState(width = 1040.dp, height = 680.dp)
    Window(
        onCloseRequest = {
            runtime.host.shutdownAll()
            exitApplication()
        },
        title = "applet_carrier",
        state = mainState,
    ) {
        CarrierTheme {
            ShellRoot(
                host = runtime.host,
                dialogHost = runtime.dialogHost,
                version = AppVersion.value,
                onOpenPrefs = { showPrefs = true },
            )
        }
    }

    if (showPrefs) {
        val prefsState = rememberWindowState(
            width = 720.dp,
            height = 520.dp,
            position = WindowPosition(Alignment.Center),
        )
        Window(
            onCloseRequest = { showPrefs = false },
            title = "Preferences — applet_carrier",
            state = prefsState,
        ) {
            CarrierTheme {
                PrefsContent(host = runtime.host, onClose = { showPrefs = false })
            }
        }
    }
}
