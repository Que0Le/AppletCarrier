package com.example.applet_carrier.platform

import java.security.MessageDigest
import java.util.Base64
import java.util.zip.CRC32

/**
 * Pure, testable hashing logic. The input text is first decoded to bytes according to the
 * chosen [InputFormat], then every common digest is computed over those bytes.
 */

/** How the input text should be interpreted as bytes before hashing. */
enum class InputFormat(val label: String) {
    UTF8("Text (UTF-8)"),
    UTF16LE("Text (UTF-16LE)"),
    UTF16BE("Text (UTF-16BE)"),
    BASE64("Base64"),
}

sealed interface DecodeResult {
    data class Ok(val bytes: ByteArray) : DecodeResult
    data class Invalid(val message: String) : DecodeResult
}

/** Convert [text] to raw bytes per [format]. Base64 ignores surrounding whitespace. */
internal fun decodeInput(text: String, format: InputFormat): DecodeResult = when (format) {
    InputFormat.UTF8 -> DecodeResult.Ok(text.toByteArray(Charsets.UTF_8))
    InputFormat.UTF16LE -> DecodeResult.Ok(text.toByteArray(Charsets.UTF_16LE))
    InputFormat.UTF16BE -> DecodeResult.Ok(text.toByteArray(Charsets.UTF_16BE))
    InputFormat.BASE64 -> try {
        DecodeResult.Ok(Base64.getDecoder().decode(text.filterNot(Char::isWhitespace)))
    } catch (e: IllegalArgumentException) {
        DecodeResult.Invalid("Input is not valid Base64.")
    }
}

data class HashRow(val algorithm: String, val hex: String)

private val DIGEST_ALGORITHMS = listOf(
    "MD5", "SHA-1", "SHA-224", "SHA-256", "SHA-384", "SHA-512", "SHA3-256", "SHA3-512",
)

/** Compute every supported digest over [bytes], plus CRC32, as lowercase hex. */
internal fun hashAll(bytes: ByteArray): List<HashRow> {
    val rows = mutableListOf<HashRow>()
    for (algorithm in DIGEST_ALGORITHMS) {
        runCatching {
            val digest = MessageDigest.getInstance(algorithm).digest(bytes)
            rows += HashRow(algorithm, digest.toHex())
        }
    }
    val crc = CRC32().apply { update(bytes) }.value
    rows += HashRow("CRC32", crc.toString(16).padStart(8, '0'))
    return rows
}

private const val HEX = "0123456789abcdef"

internal fun ByteArray.toHex(): String {
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(HEX[v ushr 4]).append(HEX[v and 0x0F])
    }
    return sb.toString()
}
