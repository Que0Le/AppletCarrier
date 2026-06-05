package com.example.applet_carrier.applets.wifiqr

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.platform.NativeDialogs
import com.example.applet_carrier.platform.WifiAuth
import com.example.applet_carrier.platform.WifiQr
import com.example.applet_carrier.platform.buildWifiPayload
import com.example.applet_carrier.ui.components.ToolButton
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes
import java.io.File
import javax.imageio.ImageIO

/**
 * Turn a Wi-Fi network name + password into a scannable QR code (the `WIFI:` URI). Renders
 * locally with ZXing — nothing is sent anywhere. Generated QR/password are not persisted;
 * only the auth type, hidden flag, and file name are.
 */
class WifiQrApplet : Applet() {

    override val metadata = AppletMetadata(id = "wifi-qr", displayName = "Wi-Fi QR")

    private var context: AppletContext? = null
    private var initialAuth = WifiAuth.WPA
    private var initialHidden = false
    private var initialFilename = "wifi"

    private var lastAuth = WifiAuth.WPA
    private var lastHidden = false
    private var lastFilename = "wifi"

    override fun onInit(context: AppletContext) {
        this.context = context
        context.config.apply {
            initialAuth = runCatching { WifiAuth.valueOf(getString("auth", WifiAuth.WPA.name)) }.getOrDefault(WifiAuth.WPA)
            initialHidden = getBoolean("hidden", false)
            initialFilename = getString("filename", "wifi")
        }
        lastAuth = initialAuth; lastHidden = initialHidden; lastFilename = initialFilename
    }

    override fun onShutdown() {
        context?.config?.apply {
            putString("auth", lastAuth.name)
            putBoolean("hidden", lastHidden)
            putString("filename", lastFilename)
            flush()
        }
    }

    @Composable
    override fun Ui() {
        val clipboard = LocalClipboardManager.current

        var ssid by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var showPass by remember { mutableStateOf(false) }
        var auth by remember { mutableStateOf(initialAuth) }
        var hidden by remember { mutableStateOf(initialHidden) }
        var filename by remember { mutableStateOf(initialFilename) }
        var status by remember { mutableStateOf<String?>(null) }
        lastAuth = auth; lastHidden = hidden; lastFilename = filename

        val payload = buildWifiPayload(ssid, password, auth, hidden)
        val image = remember(payload, ssid) {
            if (ssid.isBlank()) null else runCatching { WifiQr.render(payload) }.getOrNull()
        }
        val bitmap = remember(image) { image?.toComposeImageBitmap() }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CarrierDimens.gapLg),
        ) {
            Text("Wi-Fi QR", color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(CarrierDimens.gapMd))

            LabeledField("Network (SSID)") {
                OutlinedTextField(value = ssid, onValueChange = { ssid = it }, singleLine = true, modifier = Modifier.width(280.dp))
            }
            Spacer(Modifier.height(CarrierDimens.gapXs))

            LabeledField("Password") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        enabled = auth != WifiAuth.NONE,
                        visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
                        modifier = Modifier.width(280.dp),
                    )
                    Spacer(Modifier.width(CarrierDimens.gapSm))
                    ToolButton(if (showPass) "Hide" else "Show", enabled = auth != WifiAuth.NONE, onClick = { showPass = !showPass })
                }
            }
            Spacer(Modifier.height(CarrierDimens.gapXs))

            LabeledField("Security") {
                AuthDropdown(auth) { auth = it }
            }
            Spacer(Modifier.height(CarrierDimens.gapXs))

            Row(
                Modifier.toggleable(value = hidden, onValueChange = { hidden = it }, role = Role.Checkbox),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = hidden, onCheckedChange = null)
                Text("Hidden network", color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary)
            }
            Spacer(Modifier.height(CarrierDimens.gapMd))

            // QR preview
            if (ssid.isBlank()) {
                Text("Enter a network name to generate the QR code.", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
            } else if (bitmap == null) {
                Text("⚠  Could not generate QR (input too long?)", color = CarrierColors.Danger, fontSize = CarrierFontSizes.secondary)
            } else {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(CarrierDimens.radius))
                        .background(Color.White)
                        .padding(CarrierDimens.gapSm),
                ) {
                    Image(bitmap = bitmap, contentDescription = "Wi-Fi QR code", modifier = Modifier.size(220.dp))
                }
                Spacer(Modifier.height(CarrierDimens.gapSm))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = filename, onValueChange = { filename = it }, singleLine = true, modifier = Modifier.width(200.dp))
                    Spacer(Modifier.width(CarrierDimens.gapSm))
                    ToolButton("Download PNG", tint = CarrierColors.Accent, onClick = {
                        image?.let { img ->
                            NativeDialogs.save("Save Wi-Fi QR", "$filename.png")?.let { chosen ->
                                val target = if (chosen.extension.equals("png", true)) chosen else File(chosen.parentFile, chosen.name + ".png")
                                runCatching { ImageIO.write(img, "png", target) }
                                    .onSuccess { status = "Saved ${target.name}" }
                                    .onFailure { status = "Save failed: ${it.message}" }
                            }
                        }
                    })
                    status?.let {
                        Spacer(Modifier.width(CarrierDimens.gapMd))
                        Text(it, color = if (it.startsWith("Save failed")) CarrierColors.Danger else CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
                    }
                }
                Spacer(Modifier.height(CarrierDimens.gapMd))

                // Raw WIFI: string (the requested extra)
                Text("Raw payload", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
                Spacer(Modifier.height(CarrierDimens.gapXs))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    SelectionContainer(Modifier.weight(1f)) {
                        Text(payload, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary, fontFamily = FontFamily.Monospace)
                    }
                    Spacer(Modifier.width(CarrierDimens.gapSm))
                    ToolButton("Copy", onClick = { clipboard.setText(AnnotatedString(payload)) })
                }
            }

            Spacer(Modifier.height(CarrierDimens.gapMd))
            Text(
                "The QR contains your password in plain text — treat the image as sensitive. " +
                    "Generated locally; nothing is sent anywhere.",
                color = CarrierColors.TextMuted,
                fontSize = CarrierFontSizes.secondary,
            )
        }
    }
}

@Composable
private fun LabeledField(label: String, field: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.width(120.dp))
        field()
    }
}

@Composable
private fun AuthDropdown(selected: WifiAuth, onSelect: (WifiAuth) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
                .background(CarrierColors.ElevatedSurface)
                .border(CarrierDimens.borderWidth, CarrierColors.Border, RoundedCornerShape(CarrierDimens.radiusSmall))
                .clickable { expanded = true }
                .padding(horizontal = CarrierDimens.gapMd, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(selected.label, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary)
            Spacer(Modifier.width(CarrierDimens.gapSm))
            Text("▾", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WifiAuth.entries.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value.label, fontSize = CarrierFontSizes.secondary) },
                    onClick = { onSelect(value); expanded = false },
                )
            }
        }
    }
}
