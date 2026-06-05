package com.example.applet_carrier.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.applet_carrier.ui.components.HorizontalSeam
import com.example.applet_carrier.ui.components.ToolButton
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Global toolbar spanning both panels. Title on the left, preferences button on the
 * right. Intentionally sparse for now (AGENTS.md §3).
 */
@Composable
fun TopBar(title: String, version: String, onOpenPrefs: () -> Unit) {
    Box {
        Row(
            Modifier
                .fillMaxWidth()
                .height(CarrierDimens.topBarHeight)
                .background(CarrierColors.Surface)
                .padding(horizontal = CarrierDimens.gapMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.title,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(CarrierDimens.gapSm))
            Text(
                if (version == "dev") "dev build" else "v$version",
                color = CarrierColors.TextMuted,
                fontSize = CarrierFontSizes.secondary,
            )
            Spacer(Modifier.weight(1f))
            ToolButton(label = "⚙  Preferences", onClick = onOpenPrefs)
        }
        HorizontalSeam(Modifier.fillMaxWidth().align(Alignment.BottomStart))
    }
}
