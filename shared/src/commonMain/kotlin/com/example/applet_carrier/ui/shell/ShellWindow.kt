package com.example.applet_carrier.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.applet_carrier.core.AppletHost
import com.example.applet_carrier.ui.components.VerticalSeam
import com.example.applet_carrier.ui.dialogs.DialogHost
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens

/**
 * Root content of the main window: top bar over a [AppletSidebar] | [AppletViewport]
 * split. Shared dialogs render as an overlay on top.
 */
@Composable
fun ShellRoot(
    host: AppletHost,
    dialogHost: DialogHost,
    version: String,
    onOpenPrefs: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(CarrierColors.Background)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(title = "applet_carrier", version = version, onOpenPrefs = onOpenPrefs)
            Row(Modifier.fillMaxSize()) {
                AppletSidebar(
                    host,
                    Modifier.width(CarrierDimens.sidebarWidth).fillMaxHeight(),
                )
                VerticalSeam(Modifier.fillMaxHeight())
                AppletViewport(
                    host,
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        // Shared dialog overlay (confirm / text input).
        dialogHost.Render()
    }
}
