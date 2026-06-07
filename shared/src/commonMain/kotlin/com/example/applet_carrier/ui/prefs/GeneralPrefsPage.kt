package com.example.applet_carrier.ui.prefs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.core.AppletHost
import com.example.applet_carrier.ui.components.SwitchRow
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Built-in "General" settings. Currently a couple of demonstrative host-level options
 * persisted to host-config.json. Extend as the shell grows (AGENTS.md §3).
 */
@Composable
fun GeneralPrefsPage(host: AppletHost) {
    Column {
        PrefsSectionTitle("General")

        var confirmOnExit by remember {
            mutableStateOf(host.hostConfig.getBoolean("confirmOnExit", true))
        }
        SwitchRow(
            label = "Confirm before exit",
            checked = confirmOnExit,
            onCheckedChange = {
                confirmOnExit = it
                host.hostConfig.putBoolean("confirmOnExit", it)
                host.hostConfig.flush()
            },
        )

        Spacer(Modifier.height(16.dp))
        Text(
            "State and configuration files are stored in ~/.applet_carrier (your home folder), one pair per applet.",
            color = CarrierColors.TextMuted,
            fontSize = CarrierFontSizes.secondary,
        )
    }
}

@Composable
internal fun PrefsSectionTitle(text: String) {
    Text(
        text,
        color = CarrierColors.TextPrimary,
        fontSize = CarrierFontSizes.title,
    )
    Spacer(Modifier.height(12.dp))
}
