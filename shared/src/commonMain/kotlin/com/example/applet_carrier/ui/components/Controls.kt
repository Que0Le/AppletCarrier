package com.example.applet_carrier.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Compact ghost button: muted by default, faint hover illumination. Used in the top bar
 * and prefs footer. Restrained per AGENTS.md §7.
 */
@Composable
fun ToolButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier
            .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
            .background(if (hovered) CarrierColors.HoverOverlay else Color.Transparent)
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = CarrierDimens.gapMd, vertical = CarrierDimens.gapSm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (hovered) CarrierColors.TextPrimary else CarrierColors.TextMuted,
            fontSize = CarrierFontSizes.body,
        )
    }
}

/**
 * Square initial badge used as a fallback applet icon when no vector icon is supplied.
 */
@Composable
fun InitialBadge(name: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(18.dp)
            .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
            .background(CarrierColors.ElevatedSurface)
            .border(CarrierDimens.borderWidth, CarrierColors.Border, RoundedCornerShape(CarrierDimens.radiusSmall)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().firstOrNull()?.uppercase() ?: "?",
            color = CarrierColors.TextMuted,
            fontSize = CarrierFontSizes.secondary,
            fontWeight = FontWeight.Medium,
        )
    }
}

/**
 * A 1px luminance seam between panels. The caller supplies the long-axis fill, e.g.
 * `VerticalSeam(Modifier.fillMaxHeight())` or `HorizontalSeam(Modifier.fillMaxWidth())`.
 */
@Composable
fun VerticalSeam(modifier: Modifier = Modifier) {
    Box(modifier.width(1.dp).background(CarrierColors.Border))
}

@Composable
fun HorizontalSeam(modifier: Modifier = Modifier) {
    Box(modifier.height(1.dp).background(CarrierColors.Border))
}
