package com.example.applet_carrier.platform

/**
 * Pure, testable parsing of `lsof -nP -i:<port> -FpcfPTn` field output (macOS / Linux),
 * the Unix counterpart to NetstatParsing on Windows. lsof's `-F` machine format emits one
 * tagged value per line: `p`=pid, `c`=command, `f`=fd (starts a file record), `P`=protocol,
 * `T`=TCP info (`TST=<state>`), `n`=name (address, possibly `local->foreign`).
 */

internal data class LsofEntry(val pid: Long, val command: String, val match: PortMatch)

internal fun parseLsof(output: String, port: Int): List<LsofEntry> {
    val entries = mutableListOf<LsofEntry>()

    var pid: Long? = null
    var command = ""
    var inFile = false
    var proto = ""
    var state = ""
    var name = ""

    fun flushFile() {
        val p = pid
        if (inFile && p != null && name.isNotEmpty()) {
            val m = portMatchFromLsofName(name, port)
            if (m.matchedLocal || m.matchedForeign) {
                entries += LsofEntry(
                    pid = p,
                    command = command,
                    match = PortMatch(
                        protocol = proto.ifBlank { "TCP" },
                        localAddress = m.local,
                        foreignAddress = m.foreign,
                        state = state,
                        matchedLocal = m.matchedLocal,
                        matchedForeign = m.matchedForeign,
                    ),
                )
            }
        }
        inFile = false; proto = ""; state = ""; name = ""
    }

    for (line in output.lineSequence()) {
        if (line.isEmpty()) continue
        val tag = line[0]
        val value = line.substring(1)
        when (tag) {
            'p' -> { flushFile(); pid = value.toLongOrNull(); command = "" }
            'c' -> command = value
            'f' -> { flushFile(); inFile = true }
            'P' -> proto = value
            'T' -> if (value.startsWith("ST=")) state = value.removePrefix("ST=")
            'n' -> name = value
        }
    }
    flushFile()
    return entries
}

internal data class LsofNameMatch(
    val local: String,
    val foreign: String,
    val matchedLocal: Boolean,
    val matchedForeign: Boolean,
)

/** Split an lsof name (`*:8443`, `127.0.0.1:52345->1.2.3.4:443`, `[::1]:8443`) and match [port]. */
internal fun portMatchFromLsofName(name: String, port: Int): LsofNameMatch {
    val arrow = name.indexOf("->")
    val local: String
    val foreign: String
    if (arrow >= 0) {
        local = name.substring(0, arrow)
        foreign = name.substring(arrow + 2)
    } else {
        local = name
        foreign = ""
    }
    return LsofNameMatch(
        local = local,
        foreign = foreign,
        matchedLocal = lsofPort(local) == port,
        matchedForeign = foreign.isNotEmpty() && lsofPort(foreign) == port,
    )
}

/** Port after the last colon (handles IPv6 `[..]:port` and wildcard `*:port`). */
internal fun lsofPort(address: String): Int? {
    val idx = address.lastIndexOf(':')
    if (idx < 0) return null
    return address.substring(idx + 1).toIntOrNull()
}

/** Parse `ps -o rss=` output (resident set size in KB) into kilobytes. */
internal fun parseRssKb(raw: String): Long? = raw.trim().toLongOrNull()
