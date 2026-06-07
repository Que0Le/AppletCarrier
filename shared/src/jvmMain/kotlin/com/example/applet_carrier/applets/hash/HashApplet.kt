package com.example.applet_carrier.applets.hash

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.platform.DecodeResult
import com.example.applet_carrier.platform.InputFormat
import com.example.applet_carrier.platform.copyToClipboard
import com.example.applet_carrier.platform.decodeInput
import com.example.applet_carrier.platform.hashAll
import com.example.applet_carrier.ui.components.CopyableMonoRow
import com.example.applet_carrier.ui.components.EnumDropdown
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Hash an input to all common digests (MD5, SHA-1/2/3 family, CRC32). A dropdown selects
 * how the input text is decoded to bytes first: UTF-8, UTF-16 (LE/BE), or Base64.
 * Pure compute (jvmMain). Only the format choice is persisted — never the input.
 */
class HashApplet : Applet() {

    override val metadata = AppletMetadata(id = "hash", displayName = "Hash")

    private var context: AppletContext? = null
    private var initialFormat = InputFormat.UTF8
    private var lastFormat = InputFormat.UTF8

    override fun onInit(context: AppletContext) {
        this.context = context
        initialFormat = runCatching { InputFormat.valueOf(context.config.getString("format", InputFormat.UTF8.name)) }
            .getOrDefault(InputFormat.UTF8)
        lastFormat = initialFormat
    }

    override fun onShutdown() {
        context?.config?.apply { putString("format", lastFormat.name); flush() }
    }

    @Composable
    override fun Ui() {
        var input by remember { mutableStateOf("") }
        var format by remember { mutableStateOf(initialFormat) }
        lastFormat = format

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CarrierDimens.gapLg),
        ) {
            Text(
                "Hash",
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.title,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(CarrierDimens.gapMd))

            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                singleLine = false,
                placeholder = { Text("Enter text to hash…") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
            )
            Spacer(Modifier.height(CarrierDimens.gapSm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Input format", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
                Spacer(Modifier.width(CarrierDimens.gapSm))
                EnumDropdown(format, InputFormat.entries, { it.label }) { format = it }
            }
            Spacer(Modifier.height(CarrierDimens.gapMd))

            when (val decoded = decodeInput(input, format)) {
                is DecodeResult.Invalid -> Text(
                    "⚠  ${decoded.message}",
                    color = CarrierColors.Danger,
                    fontSize = CarrierFontSizes.secondary,
                )
                is DecodeResult.Ok -> {
                    Text(
                        "Input: ${decoded.bytes.size} bytes",
                        color = CarrierColors.TextMuted,
                        fontSize = CarrierFontSizes.secondary,
                    )
                    Spacer(Modifier.height(CarrierDimens.gapSm))
                    hashAll(decoded.bytes).forEach { row ->
                        CopyableMonoRow(
                            value = row.hex,
                            label = row.algorithm,
                            onCopy = { copyToClipboard(row.hex) },
                            modifier = Modifier.padding(vertical = 3.dp),
                        )
                    }
                }
            }
        }
    }
}
