package com.example.applet_carrier.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import com.example.applet_carrier.core.AppletHost
import com.example.applet_carrier.ui.theme.CarrierColors

/**
 * Right panel — hosts applet UIs. This is the "browser-tab" mechanism (AGENTS.md §4):
 *
 *  - Every STARTED applet stays in the composition simultaneously, so its remembered UI
 *    state (scroll position, text fields) survives switching away and back, with no
 *    serialization.
 *  - Only the active applet is visible (alpha 1, opaque background) and on top; inactive
 *    applets are drawn with alpha 0 but remain composed and laid out.
 *  - `key(id)` ties each applet's composition to its identity, so reordering the draw
 *    list (active drawn last/on top) never scrambles state.
 */
@Composable
fun AppletViewport(host: AppletHost, modifier: Modifier = Modifier) {
    Box(modifier.background(CarrierColors.Background)) {
        // Draw inactive applets first, the active one last so it sits on top and
        // receives pointer input.
        val ordered = host.startedApplets.sortedBy { it.metadata.id == host.activeId }
        ordered.forEach { applet ->
            key(applet.metadata.id) {
                val active = applet.metadata.id == host.activeId
                Box(
                    Modifier
                        .fillMaxSize()
                        .alpha(if (active) 1f else 0f)
                        .background(CarrierColors.Background),
                ) {
                    applet.Ui()
                }
            }
        }
    }
}
