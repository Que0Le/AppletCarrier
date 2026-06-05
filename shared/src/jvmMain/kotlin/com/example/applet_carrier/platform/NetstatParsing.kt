package com.example.applet_carrier.platform

/**
 * Pure, OS-independent parsing helpers for the port lookup. Kept separate from
 * [PortProcessLookup] (which spawns processes) so this logic is unit-testable without
 * touching the OS. `internal` so jvmTest can reach it.
 */

/** A netstat row that matched the searched port, with its owning PID. */
internal data class NetstatEntry(val pid: Long, val match: PortMatch)

/**
 * Parse a single `netstat -ano` line, returning an entry only if the local OR foreign
 * port equals [port]. Returns null for headers, blanks, and non-matching rows.
 *
 * TCP rows have 5 columns (proto, local, foreign, state, pid); UDP rows have 4 (no state).
 */
internal fun parseNetstatLine(line: String, port: Int): NetstatEntry? {
    val tokens = line.trim().split(Regex("\\s+"))
    if (tokens.size < 4) return null

    val proto = tokens[0].uppercase()
    if (proto != "TCP" && proto != "UDP") return null

    val local = tokens[1]
    val foreign = tokens[2]
    val state: String
    val pidStr: String
    if (proto == "TCP" && tokens.size >= 5) {
        state = tokens[3]
        pidStr = tokens[4]
    } else {
        state = ""
        pidStr = tokens.last()
    }
    val pid = pidStr.toLongOrNull() ?: return null

    val matchedLocal = portOf(local) == port
    val matchedForeign = portOf(foreign) == port
    if (!matchedLocal && !matchedForeign) return null

    return NetstatEntry(pid, PortMatch(proto, local, foreign, state, matchedLocal, matchedForeign))
}

/**
 * Extract the port from an address. Handles IPv4 (`127.0.0.1:8443`), IPv6 (`[::]:8443`),
 * and wildcards (`*:*` → null). The port is whatever follows the LAST colon.
 */
internal fun portOf(address: String): Int? {
    val idx = address.lastIndexOf(':')
    if (idx < 0) return null
    return address.substring(idx + 1).toIntOrNull()
}

/** Parse one CSV line, honoring double-quoted fields and `""` escapes (tasklist format). */
internal fun parseCsvLine(line: String): List<String> {
    val out = ArrayList<String>()
    val sb = StringBuilder()
    var inQuotes = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            c == '"' && inQuotes && i + 1 < line.length && line[i + 1] == '"' -> {
                sb.append('"'); i++
            }
            c == '"' -> inQuotes = !inQuotes
            c == ',' && !inQuotes -> { out.add(sb.toString()); sb.clear() }
            else -> sb.append(c)
        }
        i++
    }
    out.add(sb.toString())
    return out
}

/** Parse a tasklist "Mem Usage" value (e.g. `"12,345 K"`) into kilobytes. */
internal fun parseMemoryKb(raw: String): Long? = raw.filter(Char::isDigit).toLongOrNull()

/** The file name part of an executable path (Windows or Unix separators). */
internal fun baseName(path: String): String =
    path.substringAfterLast('\\').substringAfterLast('/')

/**
 * Split a Windows command line into (executable, parameters). Handles a quoted executable
 * path that contains spaces, e.g. `"C:\Program Files\app.exe" --flag x` →
 * (`C:\Program Files\app.exe`, `--flag x`). Returns an empty params string when there are
 * no arguments.
 */
internal fun splitCommandLine(commandLine: String): Pair<String, String> {
    val s = commandLine.trim()
    if (s.isEmpty()) return "" to ""
    return if (s.startsWith("\"")) {
        val end = s.indexOf('"', startIndex = 1)
        if (end < 0) {
            s.removePrefix("\"") to ""
        } else {
            s.substring(1, end) to s.substring(end + 1).trim()
        }
    } else {
        val idx = s.indexOfFirst { it == ' ' || it == '\t' }
        if (idx < 0) s to "" else s.substring(0, idx) to s.substring(idx + 1).trim()
    }
}
