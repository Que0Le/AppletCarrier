package com.example.applet_carrier.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/** One netstat connection row that matched the searched port. */
data class PortMatch(
    val protocol: String,        // TCP / UDP
    val localAddress: String,
    val foreignAddress: String,
    val state: String,           // empty for UDP
    val matchedLocal: Boolean,
    val matchedForeign: Boolean,
)

/** A process that owns at least one matching connection, with full details. */
data class ProcessResult(
    val pid: Long,
    val imageName: String,
    val userName: String?,
    val command: String?,        // executable path
    val arguments: String?,      // parameters (everything after the executable)
    val parentPid: Long?,
    val parentName: String?,
    val memoryKb: Long?,
    val startInstant: Instant?,
    val cpuTime: Duration?,
    val status: String?,
    val windowTitle: String?,
    val connections: List<PortMatch>,
)

sealed interface KillOutcome {
    data object Success : KillOutcome
    data class Failed(val message: String) : KillOutcome
}

/**
 * Windows port → process lookup. Uses `netstat -ano` for the port→PID mapping (parsed in
 * code, not via `findstr`, to avoid false positives), the JDK [ProcessHandle] for rich
 * details, and `tasklist /v` for image name, RAM and window title (AGENTS.md: platform
 * applets live in jvmMain). The public surface is suspend-based and runs off the UI
 * thread; a future `lsof`-based impl could replace the Windows internals.
 */
object PortProcessLookup {

    /** Find every process owning a connection whose local OR foreign port equals [port]. */
    suspend fun find(port: Int): List<ProcessResult> = withContext(Dispatchers.IO) {
        val rows = runCapture(listOf("netstat", "-ano")).second
            .lineSequence()
            .mapNotNull { parseNetstatLine(it, port) }
            .toList()

        rows.groupBy { it.pid }
            .map { (pid, matches) -> buildProcessResult(pid, matches.map { it.match }) }
            // Listeners first — usually what you care about.
            .sortedByDescending { res -> res.connections.any { it.state.equals("LISTENING", true) } }
    }

    /** Try a normal force-kill; success is confirmed by the process being gone. */
    suspend fun kill(pid: Long): KillOutcome = withContext(Dispatchers.IO) {
        val (code, out) = runCapture(listOf("taskkill", "/F", "/PID", pid.toString()))
        if (code == 0 || !isAlive(pid)) KillOutcome.Success
        else KillOutcome.Failed(out.trim().ifBlank { "taskkill exit code $code" })
    }

    /** Retry the kill elevated (triggers a UAC prompt). Verifies the PID is gone after. */
    suspend fun killElevated(pid: Long): KillOutcome = withContext(Dispatchers.IO) {
        val psCommand =
            "Start-Process taskkill -ArgumentList '/F','/PID','$pid' -Verb RunAs -WindowStyle Hidden"
        val (code, _) = runCapture(listOf("powershell", "-NoProfile", "-Command", psCommand))
        delay(500) // give the elevated taskkill a moment to act
        when {
            !isAlive(pid) -> KillOutcome.Success
            code != 0 -> KillOutcome.Failed("Elevation cancelled or denied")
            else -> KillOutcome.Failed("Process is still running")
        }
    }

    // ---- internals ----

    private fun buildProcessResult(pid: Long, connections: List<PortMatch>): ProcessResult {
        val handle = ProcessHandle.of(pid).orElse(null)
        val info = handle?.info()
        val handleUser = info?.user()?.orElse(null)
        val start = info?.startInstant()?.orElse(null)
        val cpu = info?.totalCpuDuration()?.orElse(null)

        // Executable path: ProcessHandle.command() is reliable on Windows.
        val exe = info?.command()?.orElse(null)
        // Arguments: ProcessHandle.arguments()/commandLine() are usually empty on Windows,
        // so fall back to WMI for the full command line and split off the parameters.
        val handleArgs = info?.arguments()?.orElse(null)?.takeIf { it.isNotEmpty() }?.joinToString(" ")
        val fullCommandLine = info?.commandLine()?.orElse(null) ?: wmiCommandLine(pid)
        val parsed = fullCommandLine?.let(::splitCommandLine)
        val command = exe ?: parsed?.first?.takeIf { it.isNotBlank() }
        val arguments = handleArgs
            ?: parsed?.second?.takeIf { it.isNotBlank() }

        val parent = handle?.parent()?.orElse(null)
        val parentPid = parent?.pid()
        val parentName = parent?.info()?.command()?.orElse(null)?.let(::baseName)

        val task = taskListInfo(pid)

        return ProcessResult(
            pid = pid,
            imageName = task?.image
                ?: info?.command()?.orElse(null)?.let(::baseName)
                ?: "unknown",
            userName = task?.user?.takeIf { it.isNotBlank() && it != "N/A" } ?: handleUser,
            command = command,
            arguments = arguments,
            parentPid = parentPid,
            parentName = parentName,
            memoryKb = task?.memKb,
            startInstant = start,
            cpuTime = cpu,
            status = task?.status?.takeIf { it.isNotBlank() && it != "N/A" },
            windowTitle = task?.windowTitle?.takeIf { it.isNotBlank() && it != "N/A" },
            connections = connections,
        )
    }

    /**
     * Full command line via WMI. Windows doesn't expose process arguments through the
     * JDK, so this PowerShell/CIM query is the reliable source. Returns null if empty
     * (e.g. a protected process without sufficient rights).
     */
    private fun wmiCommandLine(pid: Long): String? = try {
        val (code, out) = runCapture(
            listOf(
                "powershell", "-NoProfile", "-Command",
                "(Get-CimInstance Win32_Process -Filter \"ProcessId=$pid\").CommandLine",
            ),
        )
        out.trim().takeIf { it.isNotBlank() && code == 0 }
    } catch (_: Exception) {
        null
    }

    private data class TaskInfo(
        val image: String?,
        val memKb: Long?,
        val status: String?,
        val user: String?,
        val windowTitle: String?,
    )

    private fun taskListInfo(pid: Long): TaskInfo? = try {
        val line = runCapture(
            listOf("tasklist", "/v", "/fo", "csv", "/nh", "/fi", "PID eq $pid"),
        ).second.lineSequence().firstOrNull { it.startsWith("\"") }

        if (line == null) {
            null
        } else {
            // Columns: Image, PID, Session, Session#, MemUsage, Status, UserName, CPUTime, WindowTitle
            val cols = parseCsvLine(line)
            TaskInfo(
                image = cols.getOrNull(0),
                memKb = cols.getOrNull(4)?.let(::parseMemoryKb),
                status = cols.getOrNull(5),
                user = cols.getOrNull(6),
                windowTitle = cols.getOrNull(8),
            )
        }
    } catch (_: Exception) {
        null
    }

    private fun isAlive(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    /** Run a command, returning (exitCode, combined stdout+stderr). */
    private fun runCapture(command: List<String>): Pair<Int, String> {
        val process = ProcessBuilder(command).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader().readText()
        val code = process.waitFor()
        return code to output
    }
}
