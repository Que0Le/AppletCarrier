package com.example.applet_carrier.applets.findport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.platform.KillOutcome
import com.example.applet_carrier.platform.PortProcessLookup
import com.example.applet_carrier.platform.copyToClipboard
import com.example.applet_carrier.platform.ProcessResult
import com.example.applet_carrier.ui.components.ToolButton
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Find which process owns a TCP/UDP port. Enter a port and press Enter (or the Search
 * button) → the lookup runs off the UI thread and lists each owning process with full
 * details, plus Copy-PID and Kill (with confirm + elevation fallback) actions.
 *
 * Lives in jvmMain because it needs OS process APIs (AGENTS.md "platform applet" pattern).
 */
class FindProcessByPortApplet : Applet() {

    override val metadata = AppletMetadata(id = "find-process-by-port", displayName = "Find process by port")

    private var context: AppletContext? = null
    private var initialPort = ""
    private var lastPort = ""

    override fun onInit(context: AppletContext) {
        this.context = context
        initialPort = context.state.getString("port", "")
        lastPort = initialPort
    }

    override fun onShutdown() {
        context?.state?.apply {
            putString("port", lastPort)
            flush()
        }
    }

    @Composable
    override fun Ui() {
        val scope = rememberCoroutineScope()
        val dialogs = context?.dialogs

        var portText by remember { mutableStateOf(initialPort) }
        var results by remember { mutableStateOf<List<ProcessResult>>(emptyList()) }
        var status by remember { mutableStateOf<SearchStatus>(SearchStatus.Idle) }
        var actionStatus by remember { mutableStateOf<String?>(null) }
        var searchJob by remember { mutableStateOf<Job?>(null) }

        // Manual search — triggered by the Search button or Enter while the field is focused.
        fun triggerSearch() {
            val port = portText.trim().toIntOrNull()
            when {
                portText.isBlank() -> { results = emptyList(); status = SearchStatus.Idle }
                port == null || port !in 1..65535 -> { results = emptyList(); status = SearchStatus.Invalid }
                else -> {
                    searchJob?.cancel()
                    searchJob = scope.launch {
                        status = SearchStatus.Searching
                        val found = PortProcessLookup.find(port)
                        results = found
                        status = if (found.isEmpty()) SearchStatus.Empty(port) else SearchStatus.Found(port)
                    }
                }
            }
        }

        suspend fun refresh() {
            portText.trim().toIntOrNull()?.let { results = PortProcessLookup.find(it) }
        }

        fun onKill(result: ProcessResult) {
            dialogs?.confirm(
                title = "Kill process",
                message = "Force-kill ${result.imageName} (PID ${result.pid})?",
            ) { confirmed ->
                if (!confirmed) return@confirm
                scope.launch {
                    actionStatus = "Killing PID ${result.pid}…"
                    var outcome = PortProcessLookup.kill(result.pid)
                    if (outcome is KillOutcome.Failed) {
                        actionStatus = "Access denied — requesting elevation (approve the UAC prompt)…"
                        outcome = PortProcessLookup.killElevated(result.pid)
                    }
                    actionStatus = when (val o = outcome) {
                        is KillOutcome.Success -> "Killed PID ${result.pid}."
                        is KillOutcome.Failed -> "Could not kill PID ${result.pid}: ${o.message}"
                    }
                    refresh()
                }
            }
        }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(CarrierDimens.gapLg),
        ) {
            Text(
                "Find process by port",
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.title,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(CarrierDimens.gapMd))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = portText,
                    onValueChange = { new ->
                        if (new.all(Char::isDigit) && new.length <= 5) {
                            portText = new
                            lastPort = new
                            // Results no longer match the edited port until re-run.
                            results = emptyList()
                            status = SearchStatus.Idle
                        }
                    },
                    label = { Text("Port") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { triggerSearch() }),
                    modifier = Modifier.width(200.dp),
                )
                Spacer(Modifier.width(CarrierDimens.gapSm))
                ToolButton(label = "Search", onClick = { triggerSearch() }, tint = CarrierColors.Accent)
            }

            Spacer(Modifier.height(CarrierDimens.gapSm))
            StatusLine(status)
            actionStatus?.let {
                Spacer(Modifier.height(CarrierDimens.gapXs))
                Text(it, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
            }

            Spacer(Modifier.height(CarrierDimens.gapMd))
            results.forEach { result ->
                ProcessCard(
                    result = result,
                    onCopyPid = {
                        copyToClipboard(result.pid.toString())
                        actionStatus = "Copied PID ${result.pid} to clipboard."
                    },
                    onKill = { onKill(result) },
                )
                Spacer(Modifier.height(CarrierDimens.gapMd))
            }
        }
    }
}

private sealed interface SearchStatus {
    data object Idle : SearchStatus
    data object Invalid : SearchStatus
    data object Searching : SearchStatus
    data class Empty(val port: Int) : SearchStatus
    data class Found(val port: Int) : SearchStatus
}

@Composable
private fun StatusLine(status: SearchStatus) {
    when (status) {
        SearchStatus.Idle -> Hint("Enter a port number (1–65535), then press Enter or click Search.")
        SearchStatus.Invalid -> Hint("Port must be a number between 1 and 65535.")
        SearchStatus.Searching -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(
                Modifier.height(14.dp).width(14.dp),
                color = CarrierColors.Accent,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(CarrierDimens.gapSm))
            Hint("Searching…")
        }
        is SearchStatus.Empty -> Hint("No process is using port ${status.port}.")
        is SearchStatus.Found -> Hint("Port ${status.port}: matching process(es) below.")
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
}

@Composable
private fun ProcessCard(
    result: ProcessResult,
    onCopyPid: () -> Unit,
    onKill: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CarrierDimens.radius))
            .background(CarrierColors.Surface)
            .border(CarrierDimens.borderWidth, CarrierColors.Border, RoundedCornerShape(CarrierDimens.radius))
            .padding(CarrierDimens.gapMd),
    ) {
        // Header: image name + PID, action buttons.
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                result.imageName,
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.body,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(CarrierDimens.gapSm))
            Text("PID ${result.pid}", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
            Spacer(Modifier.weight(1f))
            ToolButton(label = "Copy PID", onClick = onCopyPid)
            Spacer(Modifier.width(CarrierDimens.gapXs))
            ToolButton(label = "Kill", onClick = onKill, tint = CarrierColors.Danger)
        }

        Spacer(Modifier.height(CarrierDimens.gapSm))

        SelectionContainer {
            Column {
                DetailRow("User", result.userName)
                DetailRow("Parent", result.parentPid?.let { "$it  ${result.parentName ?: ""}".trim() })
                DetailRow("Memory", formatMemory(result.memoryKb))
                DetailRow("Started", formatInstant(result.startInstant))
                DetailRow("CPU time", formatDuration(result.cpuTime))
                DetailRow("Status", result.status)
                DetailRow("Window", result.windowTitle)
                DetailRow("Command", result.command)
                DetailRow("Parameters", result.arguments)
            }
        }

        Spacer(Modifier.height(CarrierDimens.gapSm))
        Text("Connections", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.secondary)
        Spacer(Modifier.height(CarrierDimens.gapXs))
        result.connections.forEach { conn ->
            val tag = when {
                conn.matchedLocal && conn.matchedForeign -> "local+remote"
                conn.matchedForeign -> "remote"
                else -> "local"
            }
            Text(
                buildString {
                    append(conn.protocol).append("  ")
                    append(conn.localAddress).append("  →  ").append(conn.foreignAddress)
                    if (conn.state.isNotBlank()) append("  ").append(conn.state)
                    append("   [").append(tag).append(']')
                },
                color = CarrierColors.TextPrimary,
                fontSize = CarrierFontSizes.secondary,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.Start) {
        Text(
            label,
            color = CarrierColors.TextMuted,
            fontSize = CarrierFontSizes.secondary,
            modifier = Modifier.width(96.dp),
        )
        Text(
            value?.takeIf { it.isNotBlank() } ?: "—",
            color = CarrierColors.TextPrimary,
            fontSize = CarrierFontSizes.secondary,
            modifier = Modifier.weight(1f),
        )
    }
}

private val dateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault())

private fun formatInstant(instant: Instant?): String? = instant?.let(dateFormatter::format)

private fun formatDuration(duration: Duration?): String? {
    if (duration == null) return null
    val total = duration.seconds
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%d:%02d:%02d".format(h, m, s)
}

private fun formatMemory(kb: Long?): String? {
    if (kb == null) return null
    val mb = kb / 1024.0
    return "%,.1f MB  (%,d KB)".format(mb, kb)
}
