package com.example.applet_carrier.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LsofParsingTest {

    // ---- lsofPort ----

    @Test fun port_ipv4() = assertEquals(8443, lsofPort("127.0.0.1:8443"))
    @Test fun port_wildcard() = assertEquals(8443, lsofPort("*:8443"))
    @Test fun port_ipv6() = assertEquals(8443, lsofPort("[::1]:8443"))
    @Test fun port_none() = assertNull(lsofPort("*:*"))

    // ---- portMatchFromLsofName ----

    @Test
    fun name_listening_matchesLocal() {
        val m = portMatchFromLsofName("*:8443", 8443)
        assertTrue(m.matchedLocal)
        assertTrue(!m.matchedForeign)
    }

    @Test
    fun name_established_matchesForeign() {
        val m = portMatchFromLsofName("127.0.0.1:52345->1.2.3.4:443", 443)
        assertTrue(!m.matchedLocal)
        assertTrue(m.matchedForeign)
        assertEquals("127.0.0.1:52345", m.local)
        assertEquals("1.2.3.4:443", m.foreign)
    }

    @Test
    fun name_established_matchesLocalSide() {
        val m = portMatchFromLsofName("127.0.0.1:52345->1.2.3.4:443", 52345)
        assertTrue(m.matchedLocal)
        assertTrue(!m.matchedForeign)
    }

    // ---- parseLsof over a -F block ----

    private val block = listOf(
        "p12345", "cjava",
        "f10", "PTCP", "TST=LISTEN", "n*:8443",
        "f11", "PTCP", "TST=ESTABLISHED", "n127.0.0.1:52345->1.2.3.4:443",
    ).joinToString("\n")

    @Test
    fun parse_listeningPort() {
        val entries = parseLsof(block, 8443)
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals(12345L, e.pid)
        assertEquals("java", e.command)
        assertEquals("TCP", e.match.protocol)
        assertEquals("LISTEN", e.match.state)
        assertTrue(e.match.matchedLocal)
    }

    @Test
    fun parse_establishedForeignPort() {
        val entries = parseLsof(block, 443)
        assertEquals(1, entries.size)
        val e = entries.first()
        assertEquals(12345L, e.pid)
        assertEquals("ESTABLISHED", e.match.state)
        assertTrue(e.match.matchedForeign)
    }

    @Test
    fun parse_noMatch() = assertTrue(parseLsof(block, 9999).isEmpty())

    // ---- parseRssKb ----

    @Test fun rss_parsesWithWhitespace() = assertEquals(12345L, parseRssKb("  12345 "))
    @Test fun rss_nonNumeric() = assertNull(parseRssKb("RSS"))
}
