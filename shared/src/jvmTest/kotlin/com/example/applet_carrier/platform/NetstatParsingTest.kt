package com.example.applet_carrier.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NetstatParsingTest {

    // ---- portOf ----

    @Test
    fun portOf_ipv4() = assertEquals(8443, portOf("127.0.0.1:8443"))

    @Test
    fun portOf_ipv6_takesLastColon() = assertEquals(8443, portOf("[::]:8443"))

    @Test
    fun portOf_wildcard_isNull() = assertNull(portOf("*:*"))

    @Test
    fun portOf_noColon_isNull() = assertNull(portOf("notanaddress"))

    // ---- parseNetstatLine ----

    @Test
    fun tcp_listening_matchesLocalPort() {
        val line = "  TCP    0.0.0.0:8443           0.0.0.0:0              LISTENING       1234"
        val entry = parseNetstatLine(line, 8443)
        assertEquals(1234L, entry?.pid)
        assertEquals("TCP", entry?.match?.protocol)
        assertEquals("LISTENING", entry?.match?.state)
        assertEquals(true, entry?.match?.matchedLocal)
        assertEquals(false, entry?.match?.matchedForeign)
    }

    @Test
    fun tcp_ipv6_listening_matches() {
        val line = "  TCP    [::]:8443              [::]:0                 LISTENING       1234"
        val entry = parseNetstatLine(line, 8443)
        assertEquals(1234L, entry?.pid)
        assertEquals(true, entry?.match?.matchedLocal)
    }

    @Test
    fun tcp_established_matchesForeignPort() {
        val line = "  TCP    127.0.0.1:52345        127.0.0.1:8443         ESTABLISHED     5678"
        val entry = parseNetstatLine(line, 8443)
        assertEquals(5678L, entry?.pid)
        assertEquals(false, entry?.match?.matchedLocal)
        assertEquals(true, entry?.match?.matchedForeign)
        assertEquals("ESTABLISHED", entry?.match?.state)
    }

    @Test
    fun udp_hasNoState_pidIsLastToken() {
        val line = "  UDP    0.0.0.0:8443           *:*                                    9012"
        val entry = parseNetstatLine(line, 8443)
        assertEquals(9012L, entry?.pid)
        assertEquals("UDP", entry?.match?.protocol)
        assertEquals("", entry?.match?.state)
        assertEquals(true, entry?.match?.matchedLocal)
    }

    @Test
    fun nonMatchingPort_isNull() {
        val line = "  TCP    0.0.0.0:80             0.0.0.0:0              LISTENING       4"
        assertNull(parseNetstatLine(line, 8443))
    }

    @Test
    fun substringPort_doesNotFalseMatch() {
        // ":84430" must NOT match a search for 8443 (the bug findstr :8443 would have).
        val line = "  TCP    0.0.0.0:84430          0.0.0.0:0              LISTENING       4"
        assertNull(parseNetstatLine(line, 8443))
    }

    @Test
    fun headerAndBlankLines_areIgnored() {
        assertNull(parseNetstatLine("Active Connections", 8443))
        assertNull(parseNetstatLine("  Proto  Local Address          Foreign Address        State           PID", 8443))
        assertNull(parseNetstatLine("", 8443))
        assertNull(parseNetstatLine("   ", 8443))
    }

    // ---- parseCsvLine (tasklist /fo csv) ----

    @Test
    fun csv_basicRow() {
        val cols = parseCsvLine("\"chrome.exe\",\"1234\",\"Console\",\"1\",\"123,456 K\"")
        assertEquals(listOf("chrome.exe", "1234", "Console", "1", "123,456 K"), cols)
    }

    @Test
    fun csv_commaInsideQuotedField_isOneColumn() {
        // ...,"WIN\me","0:00:01","Title, with comma"
        val cols = parseCsvLine(
            "\"app.exe\",\"10\",\"Console\",\"1\",\"2,048 K\",\"Running\",\"WIN\\me\",\"0:00:01\",\"Title, with comma\"",
        )
        assertEquals(9, cols.size)
        assertEquals("Title, with comma", cols[8])
        assertEquals("WIN\\me", cols[6]) // single backslash domain\user
    }

    @Test
    fun csv_escapedDoubleQuote() {
        // Input field: "say ""hi""" -> say "hi"
        val cols = parseCsvLine("\"a\",\"say \"\"hi\"\"\"")
        assertEquals(2, cols.size)
        assertEquals("say \"hi\"", cols[1])
    }

    // ---- parseMemoryKb ----

    @Test
    fun mem_stripsThousandsAndSuffix() = assertEquals(123456L, parseMemoryKb("123,456 K"))

    @Test
    fun mem_plainNumber() = assertEquals(2048L, parseMemoryKb("2048 K"))

    @Test
    fun mem_nonNumeric_isNull() = assertNull(parseMemoryKb("N/A"))

    // ---- baseName ----

    @Test
    fun baseName_windowsPath() =
        assertEquals("chrome.exe", baseName("""C:\Program Files\Google\Chrome\chrome.exe"""))

    @Test
    fun baseName_bareName() = assertEquals("java.exe", baseName("java.exe"))

    // ---- splitCommandLine ----

    @Test
    fun split_quotedExeWithSpaces_andArgs() {
        val (exe, args) = splitCommandLine("\"C:\\Program Files\\app\\app.exe\" --flag x")
        assertEquals("C:\\Program Files\\app\\app.exe", exe)
        assertEquals("--flag x", args)
    }

    @Test
    fun split_unquotedExe_andArgs() {
        val (exe, args) = splitCommandLine("C:\\tools\\srv.exe -p 8443 -v")
        assertEquals("C:\\tools\\srv.exe", exe)
        assertEquals("-p 8443 -v", args)
    }

    @Test
    fun split_exeOnly_noArgs() {
        val (exe, args) = splitCommandLine("java.exe")
        assertEquals("java.exe", exe)
        assertEquals("", args)
    }

    @Test
    fun split_quotedExeOnly_noArgs() {
        val (exe, args) = splitCommandLine("\"C:\\a b\\app.exe\"")
        assertEquals("C:\\a b\\app.exe", exe)
        assertEquals("", args)
    }

    @Test
    fun split_blank_isEmptyPair() {
        val (exe, args) = splitCommandLine("   ")
        assertEquals("", exe)
        assertEquals("", args)
    }

    // ---- a small end-to-end-ish parse over a multi-line netstat block ----

    @Test
    fun multiLineBlock_groupsExpectedPids() {
        val block = """
            Active Connections

              Proto  Local Address          Foreign Address        State           PID
              TCP    0.0.0.0:8443           0.0.0.0:0              LISTENING       1234
              TCP    [::]:8443              [::]:0                 LISTENING       1234
              TCP    127.0.0.1:52345        127.0.0.1:8443         ESTABLISHED     5678
              TCP    0.0.0.0:80             0.0.0.0:0              LISTENING       4
              UDP    0.0.0.0:8443           *:*                                    9012
        """.trimIndent()

        val entries = block.lineSequence().mapNotNull { parseNetstatLine(it, 8443) }.toList()
        val pids = entries.map { it.pid }.toSet()

        assertEquals(setOf(1234L, 5678L, 9012L), pids)
        assertTrue(entries.none { it.pid == 4L }) // port 80 row excluded
        assertEquals(4, entries.count()) // two 1234 rows + 5678 + 9012
    }
}
