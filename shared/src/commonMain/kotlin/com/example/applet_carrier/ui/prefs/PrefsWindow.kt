package com.example.applet_carrier.ui.prefs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.core.AppletHost
import com.example.applet_carrier.ui.components.HorizontalSeam
import com.example.applet_carrier.ui.components.ToolButton
import com.example.applet_carrier.ui.components.VerticalSeam
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Content of the preferences window: a selection tree on the left, the selected settings
 * page on the right (AGENTS.md §3). Two fixed top-level nodes — "General" and
 * "Applet Settings" — with each prefs-providing applet as a child of the latter.
 */
@Composable
fun PrefsContent(host: AppletHost, onClose: () -> Unit) {
    var selected by remember { mutableStateOf<PrefsNode>(PrefsNode.General) }
    var groupExpanded by remember { mutableStateOf(true) }

    val prefsApplets = remember(host) { host.applets.filter { it.providesPrefs } }

    Column(Modifier.fillMaxSize().background(CarrierColors.Background)) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            // ---- Left: selection tree ----
            Column(
                Modifier
                    .width(CarrierDimens.prefsTreeWidth)
                    .fillMaxHeight()
                    .background(CarrierColors.Surface)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = CarrierDimens.gapSm),
            ) {
                TreeRow(
                    label = "General",
                    indent = 0,
                    selected = selected is PrefsNode.General,
                    onClick = { selected = PrefsNode.General },
                )
                TreeRow(
                    label = "Applet Settings",
                    indent = 0,
                    selected = selected is PrefsNode.AppletSettingsGroup,
                    expandable = prefsApplets.isNotEmpty(),
                    expanded = groupExpanded,
                    onToggleExpand = { groupExpanded = !groupExpanded },
                    onClick = { selected = PrefsNode.AppletSettingsGroup },
                )
                if (groupExpanded) {
                    prefsApplets.forEach { applet ->
                        TreeRow(
                            label = applet.metadata.displayName,
                            indent = 1,
                            selected = (selected as? PrefsNode.AppletPrefs)?.applet?.metadata?.id == applet.metadata.id,
                            onClick = { selected = PrefsNode.AppletPrefs(applet) },
                        )
                    }
                }
            }

            VerticalSeam(Modifier.fillMaxHeight())

            // ---- Right: selected settings page ----
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(CarrierColors.Background)
                    .verticalScroll(rememberScrollState())
                    .padding(CarrierDimens.gapLg),
            ) {
                when (val node = selected) {
                    is PrefsNode.General -> GeneralPrefsPage(host)
                    is PrefsNode.AppletSettingsGroup -> AppletSettingsPlaceholder()
                    is PrefsNode.AppletPrefs -> AppletPrefsPage(node.applet)
                }
            }
        }

        // ---- Footer ----
        HorizontalSeam(Modifier.fillMaxWidth())
        Row(
            Modifier
                .fillMaxWidth()
                .height(CarrierDimens.topBarHeight)
                .background(CarrierColors.Surface)
                .padding(horizontal = CarrierDimens.gapMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1f))
            ToolButton(label = "Close", onClick = onClose)
        }
    }
}

@Composable
private fun TreeRow(
    label: String,
    indent: Int,
    selected: Boolean,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onToggleExpand: () -> Unit = {},
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val rowBg = when {
        selected -> CarrierColors.SelectionBg
        hovered -> CarrierColors.HoverOverlay
        else -> Color.Transparent
    }
    Row(
        Modifier
            .fillMaxWidth()
            .height(CarrierDimens.rowHeight)
            .background(rowBg)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(start = CarrierDimens.gapMd + (indent * 14).dp, end = CarrierDimens.gapSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (expandable) {
            Text(
                if (expanded) "▾" else "▸",
                color = CarrierColors.TextMuted,
                fontSize = CarrierFontSizes.secondary,
                modifier = Modifier
                    .width(14.dp)
                    .clickable(onClick = onToggleExpand),
            )
        } else if (indent > 0) {
            Spacer(Modifier.width(14.dp))
        }
        Text(
            label,
            color = if (selected) CarrierColors.TextPrimary else CarrierColors.TextMuted,
            fontSize = CarrierFontSizes.sidebar,
            fontWeight = if (indent == 0) FontWeight.Medium else FontWeight.Normal,
        )
    }
}
