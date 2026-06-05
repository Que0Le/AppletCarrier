package com.example.applet_carrier.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HashingTest {

    private fun bytes(p: DecodeResult): ByteArray {
        assertTrue(p is DecodeResult.Ok, "expected Ok, was $p")
        return p.bytes
    }

    private fun hex(rows: List<HashRow>, alg: String) = rows.first { it.algorithm == alg }.hex

    // ---- decodeInput ----

    @Test
    fun utf8_bytes() = assertEquals(listOf<Byte>(97, 98, 99), bytes(decodeInput("abc", InputFormat.UTF8)).toList())

    @Test
    fun utf16le_bytes() =
        assertEquals(listOf<Byte>(0x41, 0x00), bytes(decodeInput("A", InputFormat.UTF16LE)).toList())

    @Test
    fun utf16be_bytes() =
        assertEquals(listOf<Byte>(0x00, 0x41), bytes(decodeInput("A", InputFormat.UTF16BE)).toList())

    @Test
    fun base64_decodes() =
        assertEquals("abc", String(bytes(decodeInput("YWJj", InputFormat.BASE64))))

    @Test
    fun base64_ignoresWhitespace() =
        assertEquals("abc", String(bytes(decodeInput("YW Jj\n", InputFormat.BASE64))))

    @Test
    fun base64_invalid_isRejected() =
        assertTrue(decodeInput("@@@@", InputFormat.BASE64) is DecodeResult.Invalid)

    // ---- hashAll: known vectors for "abc" ----

    @Test
    fun knownVectors_abc() {
        val rows = hashAll("abc".toByteArray(Charsets.UTF_8))
        assertEquals("900150983cd24fb0d6963f7d28e17f72", hex(rows, "MD5"))
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", hex(rows, "SHA-1"))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(rows, "SHA-256"))
        assertEquals("352441c2", hex(rows, "CRC32"))
    }

    @Test
    fun knownVectors_empty() {
        val rows = hashAll(ByteArray(0))
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", hex(rows, "MD5"))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(rows, "SHA-256"))
        assertEquals("00000000", hex(rows, "CRC32"))
    }

    @Test
    fun allAlgorithmsPresent() {
        val algs = hashAll("x".toByteArray()).map { it.algorithm }
        assertTrue(algs.containsAll(listOf("MD5", "SHA-1", "SHA-256", "SHA-512", "SHA3-256", "SHA3-512", "CRC32")))
    }

    @Test
    fun base64InputMatchesDirectBytes() {
        // SHA-256 of base64("abc") must equal SHA-256 of raw "abc"
        val viaB64 = hashAll(bytes(decodeInput("YWJj", InputFormat.BASE64)))
        val direct = hashAll("abc".toByteArray(Charsets.UTF_8))
        assertEquals(hex(direct, "SHA-256"), hex(viaB64, "SHA-256"))
    }
}
