package com.example.applet_carrier.applets.proxmox

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.platform.ProxmoxClient
import com.example.applet_carrier.platform.ProxmoxResource
import com.example.applet_carrier.platform.ProxmoxResult
import com.example.applet_carrier.platform.ResourceType
import com.example.applet_carrier.ui.components.ToolButton
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes
import kotlinx.coroutines.launch

/**
 * Proxmox VE control applet: connect with an API token, list VMs/containers across nodes,
 * and start/stop/reboot/reset them. Network applet (jvmMain) using OkHttp with TLS
 * verification disabled for the self-signed cert (scoped to its client). Server + token are
 * persisted to config (token is stored in plaintext — scope/revoke it in Proxmox).
 */
class ProxmoxApplet : Applet() {

    override val metadata = AppletMetadata(id = "proxmox", displayName = "Proxmox")

    private var context: AppletContext? = null
    private var initialServer = ""
    private var initialTokenId = ""
    private var initialSecret = ""

    override fun onInit(context: AppletContext) {
        this.context = context
        initialServer = context.config.getString("server", "")
        initialTokenId = context.config.getString("tokenId", "")
        initialSecret = context.config.getString("secret", "")
    }

    @Composable
    override fun Ui() {
        val scope = rememberCoroutineScope()
        val dialogs = context?.dialogs

        var server by remember { mutableStateOf(initialServer) }
        var tokenId by remember { mutableStateOf(initialTokenId) }
        var secret by remember { mutableStateOf(initialSecret) }
        var client by remember { mutableStateOf<ProxmoxClient?>(null) }
        var resources by remember { mutableStateOf<List<ProxmoxResource>>(emptyList()) }
        var query by remember { mutableStateOf("") }
        var error by remember { mutableStateOf<String?>(null) }
        var busy by remember { mutableStateOf(false) }

        suspend fun refresh() {
            val c = client ?: return
            busy = true
            when (val r = c.listResources()) {
                is ProxmoxResult.Ok -> { resources = r.value; error = null }
                is ProxmoxResult.Err -> error = r.message
            }
            busy = false
        }

        fun connect() {
            scope.launch {
                busy = true; error = null
                val c = ProxmoxClient(server, tokenId, secret)
                when (val r = c.testConnection()) {
                    is ProxmoxResult.Ok -> {
                        client = c
                        context?.config?.apply {
                            putString("server", server)
                            putString("tokenId", tokenId)
                            putString("secret", secret)
                            flush()
                        }
                        refresh()
                    }
                    is ProxmoxResult.Err -> { error = r.message; busy = false }
                }
            }
        }

        fun runAction(resource: ProxmoxResource, action: String) {
            scope.launch {
                busy = true; error = null
                // Run the action, then refresh — but report the action's own error LAST so
                // the refresh's success path (error = null) doesn't wipe it.
                val actionError = (client?.action(resource, action) as? ProxmoxResult.Err)?.message
                refresh()
                if (actionError != null) error = actionError
            }
        }

        fun onAction(resource: ProxmoxResource, action: String) {
            if (action == "stop" || action == "reset") {
                dialogs?.confirm(
                    title = "Confirm ${action.replaceFirstChar { it.uppercase() }}",
                    message = "${action.uppercase()} ${resource.name} (VMID ${resource.vmid})?",
                ) { confirmed -> if (confirmed) runAction(resource, action) }
            } else {
                runAction(resource, action)
            }
        }

        if (client == null) {
            ConnectionScreen(
                server = server, onServer = { server = it },
                tokenId = tokenId, onTokenId = { tokenId = it },
                secret = secret, onSecret = { secret = it },
                busy = busy, error = error, onConnect = ::connect,
            )
        } else {
            val filtered = resources.filter {
                query.isBlank() || it.name.contains(query, ignoreCase = true) || it.vmid.toString().contains(query)
            }
            MainScreen(
                server = server,
                rows = filtered,
                query = query, onQuery = { query = it },
                busy = busy, error = error,
                onRefresh = { scope.launch { refresh() } },
                onLogout = { client?.close(); client = null; resources = emptyList(); error = null; query = "" },
                onAction = ::onAction,
            )
        }
    }
}

// ---------- Connection screen ----------

@Composable
private fun ConnectionScreen(
    server: String, onServer: (String) -> Unit,
    tokenId: String, onTokenId: (String) -> Unit,
    secret: String, onSecret: (String) -> Unit,
    busy: Boolean, error: String?, onConnect: () -> Unit,
) {
    var showSecret by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.width(380.dp), horizontalAlignment = Alignment.Start) {
            Text("Connect to Proxmox", color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.title, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(CarrierDimens.gapMd))

            OutlinedTextField(
                value = server, onValueChange = onServer, singleLine = true,
                label = { Text("Server address") },
                placeholder = { Text("https://192.168.1.10:8006") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CarrierDimens.gapSm))
            OutlinedTextField(
                value = tokenId, onValueChange = onTokenId, singleLine = true,
                label = { Text("Token ID") },
                placeholder = { Text("root@pam!mytoken") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(CarrierDimens.gapSm))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = secret, onValueChange = onSecret, singleLine = true,
                    label = { Text("Token secret") },
                    placeholder = { Text("xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx") },
                    visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(CarrierDimens.gapSm))
                ToolButton(if (showSecret) "Hide" else "Show", onClick = { showSecret = !showSecret })
            }
            Spacer(Modifier.height(CarrierDimens.gapMd))
            ToolButton(if (busy) "Connecting…" else "Connect", enabled = !busy, tint = CarrierColors.Accent, onClick = onConnect)
            error?.let {
                Spacer(Modifier.height(CarrierDimens.gapSm))
                Text("⚠  $it", color = CarrierColors.Danger, fontSize = CarrierFontSizes.secondary)
            }
        }
    }
}

// ---------- Main screen ----------

@Composable
private fun MainScreen(
    server: String,
    rows: List<ProxmoxResource>,
    query: String, onQuery: (String) -> Unit,
    busy: Boolean, error: String?,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onAction: (ProxmoxResource, String) -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(CarrierDimens.gapMd)) {
        // Top bar
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(server, color = CarrierColors.TextPrimary, fontSize = CarrierFontSizes.body, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(CarrierDimens.gapMd))
            ToolButton(if (busy) "Refreshing…" else "Refresh", enabled = !busy, onClick = onRefresh)
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = query, onValueChange = onQuery, singleLine = true,
                placeholder = { Text("Filter by name or VMID") },
                modifier = Modifier.width(220.dp),
            )
            Spacer(Modifier.width(CarrierDimens.gapSm))
            ToolButton("Logout", onClick = onLogout)
        }
        error?.let {
            Spacer(Modifier.height(CarrierDimens.gapXs))
            Text("⚠  $it", color = CarrierColors.Danger, fontSize = CarrierFontSizes.secondary)
        }
        Spacer(Modifier.height(CarrierDimens.gapSm))

        // Header + rows share one container that scrolls both ways, so wide action
        // columns never clip and rows size to their content height.
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState()),
        ) {
            HeaderRow()
            Spacer(Modifier.height(CarrierDimens.gapXs))
            if (rows.isEmpty()) {
                Text("No VMs or containers.", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.body, modifier = Modifier.padding(vertical = CarrierDimens.gapSm))
            }
            rows.forEach { ResourceRow(it, onAction) }
        }
    }
}

private val W_NAME = 200.dp
private val W_VMID = 72.dp
private val W_TYPE = 52.dp
private val W_NODE = 104.dp
private val W_STATUS = 120.dp
private val W_CPU = 64.dp
private val W_MEM = 150.dp

@Composable
private fun HeaderRow() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        HeaderCell("Name", W_NAME)
        HeaderCell("VMID", W_VMID)
        HeaderCell("Type", W_TYPE)
        HeaderCell("Node", W_NODE)
        HeaderCell("Status", W_STATUS)
        HeaderCell("CPU", W_CPU)
        HeaderCell("Memory", W_MEM)
        Text("Actions", color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.body, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun HeaderCell(text: String, width: androidx.compose.ui.unit.Dp) {
    Text(text, color = CarrierColors.TextMuted, fontSize = CarrierFontSizes.body, fontWeight = FontWeight.Medium, modifier = Modifier.width(width))
}

@Composable
private fun ResourceRow(res: ProxmoxResource, onAction: (ProxmoxResource, String) -> Unit) {
    Row(Modifier.heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
        Cell(res.name, W_NAME)
        Cell(res.vmid.toString(), W_VMID, mono = true)
        Cell(res.type.label, W_TYPE)
        Cell(res.node, W_NODE)
        Box(Modifier.width(W_STATUS)) {
            StatusBadge(res.status)
        }
        Cell("%.0f%%".format(res.cpuPercent), W_CPU, mono = true)
        Cell("${res.memUsedMb} / ${res.memTotalMb} MB", W_MEM, mono = true)
        Row(horizontalArrangement = Arrangement.Start) {
            actionsFor(res).forEach { (label, action) ->
                ToolButton(
                    label, onClick = { onAction(res, action) },
                    tint = if (action == "stop" || action == "reset") CarrierColors.Danger else null,
                )
            }
        }
    }
}

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, mono: Boolean = false) {
    Text(
        text,
        color = CarrierColors.TextPrimary,
        fontSize = CarrierFontSizes.body,
        fontFamily = if (mono) FontFamily.Monospace else FontFamily.Default,
        modifier = Modifier.width(width),
    )
}

/** Status shown as a filled colored badge (legible in poor lighting, unlike a small dot). */
@Composable
private fun StatusBadge(status: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(CarrierDimens.radiusSmall))
            .background(statusColor(status))
            .padding(horizontal = CarrierDimens.gapSm, vertical = 2.dp),
    ) {
        Text(
            status,
            color = CarrierColors.Background, // dark text on the colored fill = high contrast
            fontSize = CarrierFontSizes.body,
            fontWeight = FontWeight.Medium,
        )
    }
}

private fun statusColor(status: String): Color = when (status) {
    "running" -> CarrierColors.Green
    "paused" -> Color(0xFFE0B341)
    else -> CarrierColors.TextMuted
}

/** Per-spec action set: running → shutdown/stop/reboot(+reset for VMs); stopped → start; else none. */
private fun actionsFor(res: ProxmoxResource): List<Pair<String, String>> = when (res.status) {
    "running" -> buildList {
        add("Shutdown" to "shutdown")
        add("Stop" to "stop")
        add("Reboot" to "reboot")
        if (res.type == ResourceType.VM) add("Reset" to "reset")
    }
    "stopped" -> listOf("Start" to "start")
    else -> emptyList()
}
