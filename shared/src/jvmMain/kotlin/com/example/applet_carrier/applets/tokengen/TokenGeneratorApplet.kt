package com.example.applet_carrier.applets.tokengen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.platform.KeyGen
import com.example.applet_carrier.platform.KeyGenResult
import com.example.applet_carrier.platform.NativeDialogs
import com.example.applet_carrier.platform.PasswordOptions
import com.example.applet_carrier.platform.copyToClipboard
import com.example.applet_carrier.platform.generatePassword
import com.example.applet_carrier.ui.components.HorizontalSeam
import com.example.applet_carrier.ui.components.ToolButton
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Token generator: password, UUID (v4), and Ed25519 SSH key pairs (via the OpenSSH
 * `ssh-keygen` client). A platform applet (jvmMain) — uses SecureRandom, processes and
 * native file dialogs. Generated secrets are never persisted; only non-secret settings are.
 */
class TokenGeneratorApplet : Applet() {

    override val metadata = AppletMetadata(id = "token-generator", displayName = "Token generator")

    private var context: AppletContext? = null

    // Persisted (non-secret) settings, loaded in onInit, mirrored from the UI, saved on shutdown.
    private var pwLength = 20
    private var pwUpper = true
    private var pwDigit = true
    private var pwSpecial = false
    private var pwSplit = 0
    private var pwDelim = "-"
    private var keygenPath = ""
    private var keyFilename = "id_ed25519"
    private var keyComment = ""

    override fun onInit(context: AppletContext) {
        this.context = context
        context.config.apply {
            pwLength = getInt("pwLength", 20)
            pwUpper = getBoolean("pwUpper", true)
            pwDigit = getBoolean("pwDigit", true)
            pwSpecial = getBoolean("pwSpecial", false)
            pwSplit = getInt("pwSplit", 0)
            pwDelim = getString("pwDelim", "-")
            keygenPath = getString("keygenPath", "").ifBlank { KeyGen.defaultSshKeygenPath() }
            keyFilename = getString("keyFilename", "id_ed25519")
            keyComment = getString("keyComment", "")
        }
    }

    override fun onShutdown() {
        context?.config?.apply {
            putInt("pwLength", pwLength)
            putBoolean("pwUpper", pwUpper)
            putBoolean("pwDigit", pwDigit)
            putBoolean("pwSpecial", pwSpecial)
            putInt("pwSplit", pwSplit)
            putString("pwDelim", pwDelim)
            putString("keygenPath", keygenPath)
            putString("keyFilename", keyFilename)
            putString("keyComment", keyComment)
            flush()
        }
    }

    @Composable
    override fun Ui() {
        val scope = rememberCoroutineScope()
        val random = remember { SecureRandom() }

        fun copy(value: String) {
            if (value.isNotEmpty()) copyToClipboard(value)
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CarrierDimens.gapLg),
        ) {
            Text(
                "Token generator",
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.title,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(CarrierDimens.gapMd))

            // ===== Password =====
            SectionHeader("Password")

            var length by remember { mutableStateOf(pwLength) }
            var upper by remember { mutableStateOf(pwUpper) }
            var digit by remember { mutableStateOf(pwDigit) }
            var special by remember { mutableStateOf(pwSpecial) }
            var splitText by remember { mutableStateOf(pwSplit.toString()) }
            var delimiter by remember { mutableStateOf(pwDelim) }
            var regenCount by remember { mutableStateOf(0) }
            var password by remember { mutableStateOf("") }

            // Mirror to persisted fields.
            pwLength = length; pwUpper = upper; pwDigit = digit; pwSpecial = special
            pwSplit = splitText.toIntOrNull() ?: 0; pwDelim = delimiter

            // Regenerate on any setting change or on the button.
            LaunchedEffect(length, upper, digit, special, splitText, delimiter, regenCount) {
                password = generatePassword(
                    PasswordOptions(length, upper, digit, special, splitText.toIntOrNull() ?: 0, delimiter),
                    random,
                )
            }

            OutputRow(value = password, onCopy = { copy(password) })
            Spacer(Modifier.height(CarrierDimens.gapSm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Length", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.width(64.dp))
                Slider(
                    value = length.toFloat(),
                    onValueChange = { length = it.roundToInt() },
                    valueRange = 4f..128f,
                    modifier = Modifier.width(260.dp),
                )
                Spacer(Modifier.width(CarrierDimens.gapSm))
                OutlinedTextField(
                    value = length.toString(),
                    onValueChange = { it.toIntOrNull()?.let { n -> length = n.coerceIn(4, 128) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp),
                )
            }
            Spacer(Modifier.height(CarrierDimens.gapXs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                LabeledCheckbox("A-Z", upper) { upper = it }
                Spacer(Modifier.width(CarrierDimens.gapMd))
                LabeledCheckbox("0-9", digit) { digit = it }
                Spacer(Modifier.width(CarrierDimens.gapMd))
                LabeledCheckbox("!@#", special) { special = it }
            }
            Spacer(Modifier.height(CarrierDimens.gapXs))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Split every", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
                Spacer(Modifier.width(CarrierDimens.gapSm))
                OutlinedTextField(
                    value = splitText,
                    onValueChange = { if (it.all(Char::isDigit) && it.length <= 3) splitText = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(72.dp),
                )
                Spacer(Modifier.width(CarrierDimens.gapSm))
                Text("chars with", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
                Spacer(Modifier.width(CarrierDimens.gapSm))
                OutlinedTextField(
                    value = delimiter,
                    onValueChange = { if (it.length <= 3) delimiter = it },
                    singleLine = true,
                    modifier = Modifier.width(72.dp),
                )
                Spacer(Modifier.width(CarrierDimens.gapMd))
                ToolButton("Regenerate", onClick = { regenCount++ }, tint = CarrierColors.Accent)
            }

            Spacer(Modifier.height(CarrierDimens.gapLg))

            // ===== UUID =====
            SectionHeader("UUID (v4)")
            var uuid by remember { mutableStateOf(UUID.randomUUID().toString()) }
            OutputRow(value = uuid, onCopy = { copy(uuid) })
            Spacer(Modifier.height(CarrierDimens.gapSm))
            ToolButton("Generate", onClick = { uuid = UUID.randomUUID().toString() }, tint = CarrierColors.Accent)

            Spacer(Modifier.height(CarrierDimens.gapLg))

            // ===== Ed25519 keys =====
            SectionHeader("Ed25519 key pair")
            KeySection(
                scope = scope,
                onCopy = ::copy,
                initialPath = keygenPath,
                initialFilename = keyFilename,
                initialComment = keyComment,
                onState = { p, f, c -> keygenPath = p; keyFilename = f; keyComment = c },
            )
        }
    }
}

// ---- key section (kept separate to contain its local state) ----

@Composable
private fun KeySection(
    scope: kotlinx.coroutines.CoroutineScope,
    onCopy: (String) -> Unit,
    initialPath: String,
    initialFilename: String,
    initialComment: String,
    onState: (path: String, filename: String, comment: String) -> Unit,
) {
    var keygenPath by remember { mutableStateOf(initialPath) }
    var filename by remember { mutableStateOf(initialFilename) }
    var comment by remember { mutableStateOf(initialComment) }
    var passphrase by remember { mutableStateOf("") }
    var showPass by remember { mutableStateOf(false) }
    var publicKey by remember { mutableStateOf("") }
    var privateKey by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    onState(keygenPath, filename, comment)

    // ssh-keygen path + browse
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("ssh-keygen", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.width(96.dp))
        OutlinedTextField(
            value = keygenPath,
            onValueChange = { keygenPath = it },
            singleLine = true,
            modifier = Modifier.width(360.dp),
        )
        Spacer(Modifier.width(CarrierDimens.gapSm))
        ToolButton("Browse", onClick = {
            NativeDialogs.open("Locate ssh-keygen.exe")?.let { keygenPath = it.absolutePath }
        })
    }
    Spacer(Modifier.height(CarrierDimens.gapXs))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Comment", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.width(96.dp))
        OutlinedTextField(
            value = comment,
            onValueChange = { comment = it },
            singleLine = true,
            placeholder = { Text("optional, e.g. me@host") },
            modifier = Modifier.width(280.dp),
        )
    }
    Spacer(Modifier.height(CarrierDimens.gapXs))

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Passphrase", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.width(96.dp))
        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            singleLine = true,
            visualTransformation = if (showPass) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            placeholder = { Text("optional — empty = unencrypted") },
            modifier = Modifier.width(280.dp),
        )
        Spacer(Modifier.width(CarrierDimens.gapSm))
        ToolButton(if (showPass) "Hide" else "Show", onClick = { showPass = !showPass })
    }
    Spacer(Modifier.height(CarrierDimens.gapSm))

    Row(verticalAlignment = Alignment.CenterVertically) {
        ToolButton(if (busy) "Generating…" else "Generate key pair", enabled = !busy, tint = CarrierColors.Accent, onClick = {
            busy = true
            status = "Generating…"
            scope.launch {
                when (val r = KeyGen.generateEd25519(keygenPath.trim(), comment.trim(), passphrase)) {
                    is KeyGenResult.Ok -> { publicKey = r.pair.publicKey; privateKey = r.pair.privateKey; status = "Generated." }
                    is KeyGenResult.Failed -> { publicKey = ""; privateKey = ""; status = "Failed: ${r.message}" }
                }
                busy = false
            }
        })
        status?.let {
            Spacer(Modifier.width(CarrierDimens.gapMd))
            Text(it, color = if (it.startsWith("Failed")) CarrierColors.Danger else CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
        }
    }
    Spacer(Modifier.height(CarrierDimens.gapSm))

    // File name (base) used as the Save dialog suggestion.
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("File name", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.width(96.dp))
        OutlinedTextField(
            value = filename,
            onValueChange = { filename = it },
            singleLine = true,
            modifier = Modifier.width(280.dp),
        )
    }
    Spacer(Modifier.height(CarrierDimens.gapSm))

    KeyField(
        label = "Public key  (saved as <name>.pub)",
        value = publicKey,
        fixedHeight = null,  // one long line → wraps and grows
        onCopy = { onCopy(publicKey) },
        onDownload = {
            if (publicKey.isNotEmpty()) {
                NativeDialogs.save("Save public key", "${filename}.pub")?.let { it.writeText(publicKey); status = "Saved ${it.name}" }
            }
        },
    )
    Spacer(Modifier.height(CarrierDimens.gapSm))

    KeyField(
        label = "Private key  (saved as <name>)",
        value = privateKey,
        fixedHeight = 180.dp,  // multi-line block → fixed scroll height
        onCopy = { onCopy(privateKey) },
        onDownload = {
            if (privateKey.isNotEmpty()) {
                NativeDialogs.save("Save private key", filename)?.let { it.writeText(privateKey); status = "Saved ${it.name}" }
            }
        },
    )
    Spacer(Modifier.height(CarrierDimens.gapXs))
    Text(
        "Keys are generated locally and never stored by this app.",
        color = CarrierColors.TextMuted,
        fontSize = CarrierFontSizes.secondary,
    )
}

// ---- small reusable pieces ----

@Composable
private fun SectionHeader(title: String) {
    Text(title, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.body, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(CarrierDimens.gapXs))
    HorizontalSeam(Modifier.fillMaxWidth())
    Spacer(Modifier.height(CarrierDimens.gapSm))
}

@Composable
private fun OutputRow(value: String, onCopy: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = false,  // fixed width, wraps + grows vertically
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = CarrierFontSizes.secondary),
            modifier = Modifier.width(420.dp),
        )
        Spacer(Modifier.width(CarrierDimens.gapSm))
        ToolButton("Copy", onClick = onCopy)
    }
}

@Composable
private fun LabeledCheckbox(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.toggleable(value = checked, onValueChange = onChange, role = Role.Checkbox),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(label, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun KeyField(
    label: String,
    value: String,
    fixedHeight: androidx.compose.ui.unit.Dp?,  // null = grow vertically with content
    onCopy: () -> Unit,
    onDownload: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
            Row {
                ToolButton("Copy", onClick = onCopy)
                Spacer(Modifier.width(CarrierDimens.gapXs))
                ToolButton("Download", onClick = onDownload, tint = CarrierColors.Accent)
            }
        }
        Spacer(Modifier.height(CarrierDimens.gapXs))
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = false,  // always wrap; width fixed by fillMaxWidth
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = CarrierFontSizes.secondary),
            modifier = Modifier.fillMaxWidth().then(if (fixedHeight != null) Modifier.height(fixedHeight) else Modifier),
        )
    }
}
