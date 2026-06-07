package com.example.applet_carrier.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Compact dropdown over [options], rendering each via [label]. The anchor is a bordered,
 * clickable row; selecting an item invokes [onSelect]. Shared by applets that pick from a
 * small fixed set (e.g. input format, security type).
 */
@Composable
fun <T> EnumDropdown(
    selected: T,
    options: List<T>,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    onSelect: (T) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        Row(
            Modifier
                .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
                .background(CarrierColors.ElevatedSurface)
                .border(CarrierDimens.borderWidth, CarrierColors.Border, RoundedCornerShape(CarrierDimens.radiusSmall))
                .clickable { expanded = true }
                .padding(horizontal = CarrierDimens.gapMd, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label(selected), color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary)
            Spacer(Modifier.width(CarrierDimens.gapSm))
            Text("▾", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(label(option), fontSize = CarrierFontSizes.secondary) },
                    onClick = { onSelect(option); expanded = false },
                )
            }
        }
    }
}
