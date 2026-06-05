package com.example.applet_carrier.platform

import kotlinx.coroutines.runBlocking
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Exercises the real OS pipeline end-to-end (netstat + ProcessHandle + tasklist + WMI)
 * without launching the GUI or killing anything. The test JVM itself binds a socket on a
 * free port, so `find()` must report THIS process as the owner.
 *
 * Windows-only: on other OSes it no-ops (the lookup shells out to Windows tools).
 */
class PortProcessLookupIntegrationTest {

    private val isWindows: Boolean =
        System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    @Test
    fun findsOwnProcessForBoundPort() {
        if (!isWindows) return

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
                mine.connections.any { it.state.equals("LISTENING", ignoreCase = true) },
                "The bound ServerSocket should appear as LISTENING",
            )
            // Detail enrichment: the executable must resolve for our own process.
            assertTrue(
                !mine.command.isNullOrBlank(),
                "Command (executable path) should resolve for own process; was '${mine.command}'",
            )
        }
    }
}
