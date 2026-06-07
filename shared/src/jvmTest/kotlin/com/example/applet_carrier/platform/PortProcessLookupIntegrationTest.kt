package com.example.applet_carrier.platform

import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Exercises the real OS pipeline end-to-end without launching the GUI or killing anything.
 * The test JVM binds a socket on a free port, so `find()` must report THIS process as the
 * owner — on Windows (netstat/tasklist) and on macOS/Linux (lsof/ps). No-ops on other OSes.
 */
class PortProcessLookupIntegrationTest {

    @Test
    fun findsOwnProcessForBoundPort() {
        if (!Os.isWindows && !Os.isUnix) return

        val myPid = ProcessHandle.current().pid()
        ServerSocket(0).use { server ->
            val port = server.localPort

            val results = runBlocking { PortProcessLookup.find(port) }
            val mine = results.firstOrNull { it.pid == myPid }

            assertTrue(
                mine != null,
                "Expected own PID $myPid to own port $port; got PIDs ${results.map { it.pid }}",
            )
            assertTrue(
                mine.connections.any { it.protocol == "TCP" && it.matchedLocal },
                "Expected a TCP connection matched on the local port",
            )
            assertTrue(
                // Windows reports "LISTENING", lsof reports "LISTEN".
                mine.connections.any { it.state.startsWith("LISTEN", ignoreCase = true) },
                "The bound ServerSocket should appear as listening",
            )
            assertTrue(
                !mine.command.isNullOrBlank(),
                "Command (executable path) should resolve for own process; was '${mine.command}'",
            )
        }
    }
}
