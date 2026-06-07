package com.example.applet_carrier.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

/** One connection (netstat row / lsof file) that matched the searched port. */
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
 * Cross-platform port → process lookup.
 *
 *  - Windows: `netstat -ano` (parsed in code) + JDK [ProcessHandle] + `tasklist /v` + WMI.
 *  - macOS / Linux: `lsof -nP -i:<port>` + [ProcessHandle] + `ps` (rss/user/comm).
 *
 * The public surface is suspend-based and runs off the UI thread; failures (e.g. the tool
 * missing) propagate so the caller can show a message.
 */
object PortProcessLookup {

    /** Find every process owning a connection whose local OR foreign port equals [port]. */
    suspend fun find(port: Int): List<ProcessResult> = withContext(Dispatchers.IO) {
        when {
            Os.isWindows -> findWindows(port)
            Os.isUnix -> findUnix(port)
            else -> throw UnsupportedOperationException("Port lookup isn't supported on ${Os.name}")
        }
    }

    /** Force-kill the process; success is confirmed by the process being gone. */
    suspend fun kill(pid: Long): KillOutcome = withContext(Dispatchers.IO) {
        if (Os.isWindows) killWindows(pid) else killUnix(pid)
    }

    /** Retry the kill elevated (UAC on Windows, admin prompt on macOS, pkexec on Linux). */
    suspend fun killElevated(pid: Long): KillOutcome = withContext(Dispatchers.IO) {
        if (Os.isWindows) killWindowsElevated(pid) else killUnixElevated(pid)
    }

    private fun isAlive(pid: Long): Boolean =
        ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

    // ================= Windows =================

    private fun findWindows(port: Int): List<ProcessResult> =
        runProcess(listOf("netstat", "-ano")).second
            .lineSequence()
            .mapNotNull { parseNetstatLine(it, port) }
            .toList()
            .groupBy { it.pid }
            .map { (pid, matches) -> buildWindowsProcessResult(pid, matches.map { it.match }) }
            .sortedByDescending { res -> res.connections.any { it.state.equals("LISTENING", true) } }

    private fun killWindows(pid: Long): KillOutcome {
        val (code, out) = runProcess(listOf("taskkill", "/F", "/PID", pid.toString()))
        return if (code == 0 || !isAlive(pid)) KillOutcome.Success
        else KillOutcome.Failed(out.trim().ifBlank { "taskkill exit code $code" })
    }

    private suspend fun killWindowsElevated(pid: Long): KillOutcome {
        val psCommand =
            "Start-Process taskkill -ArgumentList '/F','/PID','$pid' -Verb RunAs -WindowStyle Hidden"
        val (code, _) = runProcess(listOf("powershell", "-NoProfile", "-Command", psCommand))
        delay(500)
        return when {
            !isAlive(pid) -> KillOutcome.Success
            code != 0 -> KillOutcome.Failed("Elevation cancelled or denied")
            else -> KillOutcome.Failed("Process is still running")
        }
    }

    private fun buildWindowsProcessResult(pid: Long, connections: List<PortMatch>): ProcessResult {
        val handle = ProcessHandle.of(pid).orElse(null)
        val info = handle?.info()
        val handleUser = info?.user()?.orElse(null)
        val exe = info?.command()?.orElse(null)
        // ProcessHandle.arguments()/commandLine() are usually empty on Windows → fall back to WMI.
        val handleArgs = info?.arguments()?.orElse(null)?.takeIf { it.isNotEmpty() }?.joinToString(" ")
        val fullCommandLine = info?.commandLine()?.orElse(null) ?: wmiCommandLine(pid)
        val parsed = fullCommandLine?.let(::splitCommandLine)
        val command = exe ?: parsed?.first?.takeIf { it.isNotBlank() }
        val arguments = handleArgs ?: parsed?.second?.takeIf { it.isNotBlank() }

        val parent = handle?.parent()?.orElse(null)
        val task = taskListInfo(pid)

        return ProcessResult(
            pid = pid,
            imageName = task?.image ?: exe?.let(::baseName) ?: "unknown",
            userName = task?.user?.takeIf { it.isNotBlank() && it != "N/A" } ?: handleUser,
            command = command,
            arguments = arguments,
            parentPid = parent?.pid(),
            parentName = parent?.info()?.command()?.orElse(null)?.let(::baseName),
            memoryKb = task?.memKb,
            startInstant = info?.startInstant()?.orElse(null),
            cpuTime = info?.totalCpuDuration()?.orElse(null),
            status = task?.status?.takeIf { it.isNotBlank() && it != "N/A" },
            windowTitle = task?.windowTitle?.takeIf { it.isNotBlank() && it != "N/A" },
            connections = connections,
        )
    }

    private fun wmiCommandLine(pid: Long): String? = try {
        val (code, out) = runProcess(
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
        val line = runProcess(
            listOf("tasklist", "/v", "/fo", "csv", "/nh", "/fi", "PID eq $pid"),
        ).second.lineSequence().firstOrNull { it.startsWith("\"") }
        if (line == null) {
            null
        } else {
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

    // ================= macOS / Linux =================

    private fun findUnix(port: Int): List<ProcessResult> =
        // lsof exits 1 with no output when nothing matches — that's empty, not an error.
        runProcess(listOf("lsof", "-nP", "-i:$port", "-FpcfPTn")).second
            .let { parseLsof(it, port) }
            .groupBy { it.pid }
            .map { (pid, entries) -> buildUnixProcessResult(pid, entries.map { it.match }) }
            .sortedByDescending { res -> res.connections.any { it.state.equals("LISTEN", true) } }

    private fun buildUnixProcessResult(pid: Long, connections: List<PortMatch>): ProcessResult {
        val handle = ProcessHandle.of(pid).orElse(null)
        val info = handle?.info()
        val command = info?.command()?.orElse(null)
        // ProcessHandle args/commandLine DO work on Unix.
        val arguments = info?.arguments()?.orElse(null)?.takeIf { it.isNotEmpty() }?.joinToString(" ")
            ?: info?.commandLine()?.orElse(null)?.let { splitCommandLine(it).second.takeIf(String::isNotBlank) }
        val parent = handle?.parent()?.orElse(null)

        return ProcessResult(
            pid = pid,
            imageName = command?.let(::baseName) ?: psField(pid, "comm=") ?: "unknown",
            userName = info?.user()?.orElse(null) ?: psField(pid, "user="),
            command = command,
            arguments = arguments,
            parentPid = parent?.pid(),
            parentName = parent?.info()?.command()?.orElse(null)?.let(::baseName),
            memoryKb = psField(pid, "rss=")?.let(::parseRssKb),
            startInstant = info?.startInstant()?.orElse(null),
            cpuTime = info?.totalCpuDuration()?.orElse(null),
            status = null,
            windowTitle = null,
            connections = connections,
        )
    }

    /** Read a single `ps -o <field>= -p <pid>` value (header suppressed by the trailing `=`). */
    private fun psField(pid: Long, field: String): String? = try {
        runProcess(listOf("ps", "-o", field, "-p", pid.toString())).second.trim().ifBlank { null }
    } catch (_: Exception) {
        null
    }

    private fun killUnix(pid: Long): KillOutcome {
        val (code, out) = runProcess(listOf("kill", "-9", pid.toString()))
        return if (code == 0 || !isAlive(pid)) KillOutcome.Success
        else KillOutcome.Failed(out.trim().ifBlank { "kill exit code $code" })
    }

    private suspend fun killUnixElevated(pid: Long): KillOutcome {
        val command = if (Os.isMac) {
            // Triggers the macOS admin password prompt.
            listOf("osascript", "-e", "do shell script \"kill -9 $pid\" with administrator privileges")
        } else {
            // Linux: polkit GUI prompt (if pkexec is available).
            listOf("pkexec", "kill", "-9", pid.toString())
        }
        val (code, _) = runProcess(command)
        delay(500)
        return when {
            !isAlive(pid) -> KillOutcome.Success
            code != 0 -> KillOutcome.Failed("Elevation cancelled or denied")
            else -> KillOutcome.Failed("Process is still running")
        }
    }
}
