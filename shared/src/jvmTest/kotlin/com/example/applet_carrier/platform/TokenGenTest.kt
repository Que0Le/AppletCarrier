package com.example.applet_carrier.platform

import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TokenGenTest {

    // ---- charset (pools-only) ----

    @Test
    fun charset_lowercaseAlwaysIncluded() {
        val cs = buildCharset(PasswordOptions(10, includeUppercase = false, includeDigits = false, includeSpecial = false, splitEvery = 0, delimiter = ""))
        assertEquals(PW_LOWER, cs)
    }

    @Test
    fun charset_addsSelectedClasses() {
        val cs = buildCharset(PasswordOptions(10, includeUppercase = true, includeDigits = true, includeSpecial = true, splitEvery = 0, delimiter = ""))
        assertTrue(cs.contains(PW_UPPER))
        assertTrue(cs.contains(PW_DIGITS))
        assertTrue(cs.contains(PW_SPECIAL))
    }

    // ---- delimiter formatting ----

    @Test
    fun delimiter_insertedEveryN() {
        assertEquals("abcd-efgh-ijkl", formatWithDelimiter("abcdefghijkl", 4, "-"))
    }

    @Test
    fun delimiter_zeroOrEmpty_isNoOp() {
        assertEquals("abcdef", formatWithDelimiter("abcdef", 0, "-"))
        assertEquals("abcdef", formatWithDelimiter("abcdef", 3, ""))
    }

    @Test
    fun delimiter_partialLastChunk() {
        assertEquals("abc-de", formatWithDelimiter("abcde", 3, "-"))
    }

    // ---- password generation ----

    @Test
    fun password_lengthCountsRealChars_notDelimiters() {
        val pw = generatePassword(
            PasswordOptions(20, includeUppercase = true, includeDigits = true, includeSpecial = false, splitEvery = 4, delimiter = "-"),
            Random(42),
        )
        // 20 chars + 4 delimiters (after each group of 4, joined → 4 separators)
        assertEquals(20, pw.count { it != '-' })
        assertEquals("----".length /*4*/, pw.count { it == '-' })
    }

    @Test
    fun password_onlyUsesPoolCharacters() {
        val pw = generatePassword(
            PasswordOptions(200, includeUppercase = false, includeDigits = true, includeSpecial = false, splitEvery = 0, delimiter = ""),
            Random(7),
        )
        val allowed = (PW_LOWER + PW_DIGITS).toSet()
        assertTrue(pw.all { it in allowed })
        assertFalse(pw.any { it in PW_UPPER })
    }

    @Test
    fun password_isDeterministicForSeededRandom() {
        val opts = PasswordOptions(32, includeUppercase = true, includeDigits = true, includeSpecial = true, splitEvery = 0, delimiter = "")
        assertEquals(generatePassword(opts, Random(123)), generatePassword(opts, Random(123)))
    }

    // ---- ssh-keygen arg building ----

    @Test
    fun sshKeygenArgs_ed25519_withPassphraseAndComment() {
        val args = sshKeygenArgs("ssh-keygen", "C:\\tmp\\id_ed25519", "me@host", "secret")
        assertEquals(listOf("ssh-keygen", "-t", "ed25519", "-f", "C:\\tmp\\id_ed25519", "-N", "secret", "-q", "-C", "me@host"), args)
    }

    @Test
    fun sshKeygenArgs_blankComment_omitsCFlag() {
        val args = sshKeygenArgs("ssh-keygen", "out", "", "")
        assertFalse(args.contains("-C"))
        // empty passphrase is still passed as its own (empty) argument after -N
        assertEquals("-N", args[args.indexOf("-N")])
        assertEquals("", args[args.indexOf("-N") + 1])
    }
}
