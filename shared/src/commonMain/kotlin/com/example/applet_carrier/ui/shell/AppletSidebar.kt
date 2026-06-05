package com.example.applet_carrier.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.core.AppletHost
import com.example.applet_carrier.ui.components.InitialBadge
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Left vertical tab bar: a scrollable list of selectable applets. Rows are low-contrast
 * until hovered/selected; the active row gets a thin accent indicator (AGENTS.md §3, §7).
 */
@Composable
fun AppletSidebar(host: AppletHost, modifier: Modifier = Modifier) {
    Box(modifier.background(CarrierColors.Surface)) {
        androidx.compose.foundation.layout.Column(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(vertical = CarrierDimens.gapSm),
        ) {
            host.applets.forEach { applet ->
                SidebarRow(
                    applet = applet,
                    selected = applet.metadata.id == host.activeId,
                    onClick = { host.select(applet.metadata.id) },
                )
            }
        }
    }
}

@Composable
private fun SidebarRow(applet: Applet, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val rowBg = when {
        selected -> CarrierColors.SelectionBg
        hovered -> CarrierColors.HoverOverlay
        else -> Color.Transparent
    }

    Box(
        Modifier
            .fillMaxWidth()
            .height(CarrierDimens.rowHeight)
            .background(rowBg)
            .hoverable(interaction)
            .clickable(onClick = onClick),
    ) {
        // Thin accent indicator on the left edge when active.
        if (selected) {
            Box(
                Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart)
                    .background(CarrierColors.Accent),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .padding(horizontal = CarrierDimens.gapMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = applet.metadata.icon
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (selected) CarrierColors.TextPrimary else CarrierColors.TextMuted,
                    modifier = Modifier.width(18.dp),
                )
            } else {
                InitialBadge(applet.metadata.displayName)
            }
            Spacer(Modifier.width(CarrierDimens.gapSm))
            Text(
                applet.metadata.displayName,
                color = if (selected) CarrierColors.TextPrimary else CarrierColors.TextMuted,
                fontSize = CarrierFontSizes.sidebar,
            )
        }
    }
}
