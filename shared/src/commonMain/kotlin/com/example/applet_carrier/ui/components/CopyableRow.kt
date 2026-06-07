package com.example.applet_carrier.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * A selectable, monospaced value with an optional left [label] and a Copy button. Wraps and
 * grows vertically at the available width. Shared by applets that present read-only output
 * (hashes, payloads, …).
 */
@Composable
fun CopyableMonoRow(
    value: String,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    labelWidth: Dp = 84.dp,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (label != null) {
            Text(
                label,
                color = CarrierColors.TextMuted,
                fontSize = CarrierFontSizes.secondary,
                modifier = Modifier.width(labelWidth),
            )
        }
        SelectionContainer(Modifier.weight(1f)) {
            Text(
                value,
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.secondary,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(CarrierDimens.gapSm))
        ToolButton("Copy", onClick = onCopy)
    }
}
