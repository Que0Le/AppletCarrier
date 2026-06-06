package com.example.applet_carrier.applets.timestamp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.platform.FormatGroup
import com.example.applet_carrier.platform.ForcedMode
import com.example.applet_carrier.platform.OffsetStyles
import com.example.applet_carrier.platform.TsParse
import com.example.applet_carrier.platform.buildFormatGroups
import com.example.applet_carrier.platform.copyToClipboard
import com.example.applet_carrier.platform.parseTimestamp
import com.example.applet_carrier.ui.components.ToolButton
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * Timestamp & timezone converter: one input field, many copyable formats. Pure compute
 * (java.time), so it lives in jvmMain alongside the other platform applets but touches no
 * OS/network. Source-zone, offset styles, and favorite zones are held as instance state so
 * the running UI and the preferences page stay in sync (same applet object).
 */
class TimestampApplet : Applet() {

    override val metadata = AppletMetadata(id = "timestamp", displayName = "Timestamp & timezone")
    override val providesPrefs = true

    private var context: AppletContext? = null
    private var lastInput = ""

    // Shared, observable settings (read by both Ui and PrefsUi).
    private var sourceUtc by mutableStateOf(true)
    private val offsetStyles = mutableStateListOf(OffsetStyles.Z, OffsetStyles.COLON)
    private val favoriteZones = mutableStateListOf<ZoneId>()

    private val sourceZone: ZoneId get() = if (sourceUtc) ZoneOffset.UTC else ZoneId.systemDefault()

    override fun onInit(context: AppletContext) {
        this.context = context
        lastInput = context.state.getString("input", "")

        sourceUtc = context.config.getString("sourceZone", "UTC") != "LOCAL"

        offsetStyles.clear()
        context.config.getString("offsets", "${OffsetStyles.Z},${OffsetStyles.COLON}")
            .split(',').map { it.trim() }.filter { it in OffsetStyles.ALL }
            .let { offsetStyles.addAll(it.ifEmpty { listOf(OffsetStyles.Z) }) }

        favoriteZones.clear()
        favoriteZones.addAll(
            context.config.getString("zones", "UTC,Europe/Berlin,America/New_York,Asia/Tokyo")
                .split(',').mapNotNull { runCatching { ZoneId.of(it.trim()) }.getOrNull() },
        )
    }

    override fun onShutdown() {
        context?.state?.apply { putString("input", lastInput); flush() }
        persistConfig()
    }

    private fun persistConfig() {
        context?.config?.apply {
            putString("sourceZone", if (sourceUtc) "UTC" else "LOCAL")
            putString("offsets", offsetStyles.joinToString(","))
            putString("zones", favoriteZones.joinToString(",") { it.id })
            flush()
        }
    }

    // ---- main UI ----

    @Composable
    override fun Ui() {
        val focus = LocalFocusManager.current

        var input by remember { mutableStateOf(lastInput) }
        var forced by remember { mutableStateOf<ForcedMode?>(null) }
        var copied by remember { mutableStateOf<String?>(null) }
        var nowTick by remember { mutableStateOf(Instant.now()) }
        lastInput = input

        // Live "now" while the field is empty.
        LaunchedEffect(input.isBlank()) {
            while (input.isBlank()) {
                nowTick = Instant.now()
                delay(1000)
            }
        }

        val parse = parseTimestamp(input, sourceZone, forced)

        fun snap() {
            val p = parse
            if (p is TsParse.Ok && p.canonical != null && p.canonical != input) {
                input = p.canonical
            }
        }

        fun copy(value: String) {
            copyToClipboard(value)
            copied = value
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(CarrierDimens.gapLg),
        ) {
            Text(
                "Timestamp & timezone converter",
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.title,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(CarrierDimens.gapMd))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it; forced = null },
                    singleLine = true,
                    placeholder = { Text("YYYYMMDD, epoch, or ISO 8601 — empty = now") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { snap(); focus.clearFocus() }),
                    modifier = Modifier
                        .width(320.dp)
                        .onFocusChanged { if (!it.isFocused) snap() },
                )
                Spacer(Modifier.width(CarrierDimens.gapMd))
                Text("Source", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
                Spacer(Modifier.width(CarrierDimens.gapSm))
                SourceToggle(sourceUtc) { sourceUtc = it; persistConfig() }
            }

            Spacer(Modifier.height(CarrierDimens.gapSm))

            val instant: Instant? = when (val p = parse) {
                is TsParse.Empty -> {
                    LiveNowHeader()
                    nowTick
                }
                is TsParse.Invalid -> {
                    Text("⚠  ${p.message}", color = CarrierColors.Danger, fontSize = CarrierFontSizes.secondary)
                    null
                }
                is TsParse.Ok -> {
                    ReadAsLine(p.readAs)
                    if (p.overrides.isNotEmpty()) {
                        Spacer(Modifier.height(CarrierDimens.gapXs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "not what you meant?",
                                color = CarrierColors.TextMuted,
                                fontSize = CarrierFontSizes.secondary,
                            )
                            p.overrides.forEach { ov ->
                                Spacer(Modifier.width(CarrierDimens.gapXs))
                                OverrideChip(ov.label) { forced = ov.mode }
                            }
                        }
                    }
                    p.instant
                }
            }

            copied?.let {
                Spacer(Modifier.height(CarrierDimens.gapXs))
                Text("Copied  $it", color = CarrierColors.Accent, fontSize = CarrierFontSizes.secondary)
            }

            if (instant != null) {
                Spacer(Modifier.height(CarrierDimens.gapMd))
                val groups = buildFormatGroups(instant, sourceZone, favoriteZones.toList(), offsetStyles.toList(), nowTick)
                groups.forEach { group ->
                    FormatGroupView(group, onCopy = ::copy)
                    Spacer(Modifier.height(CarrierDimens.gapMd))
                }
            }
        }
    }

    // ---- preferences page ----

    @Composable
    override fun PrefsUi() {
        Column {
            Text("Default source zone", color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.body)
            Spacer(Modifier.height(CarrierDimens.gapXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioRow("UTC", sourceUtc) { sourceUtc = true; persistConfig() }
                Spacer(Modifier.width(CarrierDimens.gapMd))
                RadioRow("Local", !sourceUtc) { sourceUtc = false; persistConfig() }
            }

            Spacer(Modifier.height(CarrierDimens.gapMd))
            Text("Offset style", color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.body)
            Spacer(Modifier.height(CarrierDimens.gapXs))
            Row {
                OffsetStyles.ALL.forEach { style ->
                    CheckRow(style, style in offsetStyles) { on ->
                        if (on) { if (style !in offsetStyles) offsetStyles.add(style) } else offsetStyles.remove(style)
                        if (offsetStyles.isEmpty()) offsetStyles.add(OffsetStyles.Z)
                        persistConfig()
                    }
                    Spacer(Modifier.width(CarrierDimens.gapMd))
                }
            }

            Spacer(Modifier.height(CarrierDimens.gapMd))
            Text("Favorite time zones", color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.body)
            Spacer(Modifier.height(CarrierDimens.gapXs))
            favoriteZones.toList().forEach { zone ->
                Row(Modifier.fillMaxWidth().height(26.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(zone.id, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.weight(1f))
                    ToolButton("✕", onClick = { favoriteZones.remove(zone); persistConfig() })
                }
            }
            AddZoneRow { id ->
                val zone = runCatching { ZoneId.of(id) }.getOrNull()
                if (zone != null && favoriteZones.none { it.id == zone.id }) {
                    favoriteZones.add(zone)
                    persistConfig()
                    true
                } else {
                    false
                }
            }
        }
    }
}

// ---- small composables ----

@Composable
private fun SourceToggle(utc: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
            .border(CarrierDimens.borderWidth, CarrierColors.Border, RoundedCornerShape(CarrierDimens.radiusSmall)),
    ) {
        Segment("UTC", utc) { onChange(true) }
        Segment("Local", !utc) { onChange(false) }
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .background(if (selected) CarrierColors.SelectionBg else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = CarrierDimens.gapMd, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) CarrierColors.TextPrimary else CarrierColors.TextMuted,
            fontSize = CarrierFontSizes.secondary,
        )
    }
}

@Composable
private fun LiveNowHeader() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(6.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(CarrierColors.Accent))
        Spacer(Modifier.width(CarrierDimens.gapSm))
        Text("Live — current time, updating each second. Type to freeze.",
            color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
    }
}

@Composable
private fun ReadAsLine(text: String) {
    Text("Read as · $text", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
}

@Composable
private fun OverrideChip(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
            .background(CarrierColors.ElevatedSurface)
            .border(CarrierDimens.borderWidth, CarrierColors.Border, RoundedCornerShape(CarrierDimens.radiusSmall))
            .clickable(onClick = onClick)
            .padding(horizontal = CarrierDimens.gapSm, vertical = 2.dp),
    ) {
        Text(label, color = CarrierColors.Accent, fontSize = CarrierFontSizes.secondary)
    }
}

@Composable
private fun FormatGroupView(group: FormatGroup, onCopy: (String) -> Unit) {
    val isZones = group.title == "Time zones"
    Text(group.title, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, fontWeight = FontWeight.Medium)
    Spacer(Modifier.height(CarrierDimens.gapXs))
    group.rows.forEach { row ->
        val interaction = remember { MutableInteractionSource() }
        val hovered by interaction.collectIsHoveredAsState()
        Row(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
                .background(if (hovered) CarrierColors.HoverOverlay else Color.Transparent)
                .hoverable(interaction)
                .clickable { onCopy(row.value) }
                .padding(horizontal = CarrierDimens.gapSm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isZones) {
                // Zone id on the left, ISO value next.
                Text(row.label ?: "", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary, modifier = Modifier.width(150.dp))
                Text(row.value, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
            } else {
                Text(row.value, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                row.label?.let {
                    Text(it, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
                    Spacer(Modifier.width(CarrierDimens.gapMd))
                }
            }
            Text(if (hovered) "⧉ Copy" else "⧉", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
        }
    }
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (selected) "◉" else "○", color = if (selected) CarrierColors.Accent else CarrierColors.TextMuted)
        Spacer(Modifier.width(CarrierDimens.gapXs))
        Text(label, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.body)
    }
}

@Composable
private fun CheckRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (checked) "☑" else "☐", color = if (checked) CarrierColors.Accent else CarrierColors.TextMuted)
        Spacer(Modifier.width(CarrierDimens.gapXs))
        Text(label, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.secondary)
    }
}

@Composable
private fun AddZoneRow(onAdd: (String) -> Boolean) {
    var text by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Start) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it; error = false },
            singleLine = true,
            isError = error,
            placeholder = { Text("Area/City, e.g. Asia/Tokyo", style = LocalTextStyle.current) },
            modifier = Modifier.width(220.dp).height(52.dp),
        )
        Spacer(Modifier.width(CarrierDimens.gapSm))
        ToolButton("+ Add zone", onClick = {
            if (text.isNotBlank()) {
                val ok = onAdd(text.trim())
                if (ok) text = "" else error = true
            }
        })
    }
}
