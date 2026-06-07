package com.example.applet_carrier.platform

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

/**
 * Pure, testable token-generation logic plus Ed25519 key generation via the OpenSSH
 * `ssh-keygen` client (a platform applet — AGENTS.md). The password helpers take an
 * injected [Random] so tests can be deterministic; production passes a `SecureRandom`.
 */

const val PW_LOWER = "abcdefghijklmnopqrstuvwxyz"
const val PW_UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
const val PW_DIGITS = "0123456789"
const val PW_SPECIAL = "!@#\$%^&*()-_=+[]{}"

data class PasswordOptions(
    val length: Int,
    val includeUppercase: Boolean,
    val includeDigits: Boolean,
    val includeSpecial: Boolean,
    val splitEvery: Int,      // 0 = no delimiter
    val delimiter: String,
)

/** Lowercase is the always-on base; ticked classes are added to the pool (pools-only). */
internal fun buildCharset(options: PasswordOptions): String = buildString {
    append(PW_LOWER)
    if (options.includeUppercase) append(PW_UPPER)
    if (options.includeDigits) append(PW_DIGITS)
    if (options.includeSpecial) append(PW_SPECIAL)
}

/** Insert [delimiter] every [every] characters (readability only; not counted in length). */
internal fun formatWithDelimiter(raw: String, every: Int, delimiter: String): String {
    if (every <= 0 || delimiter.isEmpty()) return raw
    return raw.chunked(every).joinToString(delimiter)
}

/** Generate a password of [PasswordOptions.length] real characters, then apply the delimiter. */
internal fun generatePassword(options: PasswordOptions, random: Random): String {
    val charset = buildCharset(options)
    if (options.length <= 0 || charset.isEmpty()) return ""
    val raw = buildString {
        repeat(options.length) { append(charset[random.nextInt(charset.length)]) }
    }
    return formatWithDelimiter(raw, options.splitEvery, options.delimiter)
}

// ---- Ed25519 keys via ssh-keygen ----

data class SshKeyPair(val publicKey: String, val privateKey: String)

sealed interface KeyGenResult {
    data class Ok(val pair: SshKeyPair) : KeyGenResult
    data class Failed(val message: String) : KeyGenResult
}

/** Build the ssh-keygen argument list. `-N ""` means no passphrase; `-C` omitted when blank. */
internal fun sshKeygenArgs(exe: String, outFile: String, comment: String, passphrase: String): List<String> {
    val args = mutableListOf(exe, "-t", "ed25519", "-f", outFile, "-N", passphrase, "-q")
    if (comment.isNotBlank()) {
        args += "-C"
        args += comment
    }
    return args
}

object KeyGen {

    /** Best-effort default path to ssh-keygen, per OS; falls back to the PATH name. */
    fun defaultSshKeygenPath(): String {
        if (Os.isWindows) {
            val systemRoot = System.getenv("SystemRoot") ?: "C:\\Windows"
            val bundled = File(systemRoot, "System32\\OpenSSH\\ssh-keygen.exe")
            return if (bundled.exists()) bundled.absolutePath else "ssh-keygen"
        }
        // macOS / Linux: ssh-keygen ships at /usr/bin/ssh-keygen and is on PATH.
        val unixDefault = File("/usr/bin/ssh-keygen")
        return if (unixDefault.exists()) unixDefault.absolutePath else "ssh-keygen"
    }

    /**
     * Generate an Ed25519 key pair. ssh-keygen must write to disk, so we use a fresh temp
     * dir (no overwrite prompt), read both files into memory, then shred-delete the temp
     * files — nothing sensitive is left on disk by this app.
     */
    suspend fun generateEd25519(exe: String, comment: String, passphrase: String): KeyGenResult =
        withContext(Dispatchers.IO) {
            val tempDir = Files.createTempDirectory("applet-keygen")
            try {
                val keyPath = tempDir.resolve("id_ed25519")
                val (code, output) = runProcess(sshKeygenArgs(exe, keyPath.toString(), comment, passphrase))
                if (code != 0) {
                    return@withContext KeyGenResult.Failed(output.trim().ifBlank { "ssh-keygen exited with code $code" })
                }
                val privateKey = Files.readString(keyPath)
                val publicKey = Files.readString(tempDir.resolve("id_ed25519.pub")).trim()
                KeyGenResult.Ok(SshKeyPair(publicKey = publicKey, privateKey = privateKey))
            } catch (e: Exception) {
                KeyGenResult.Failed(e.message ?: "Key generation failed")
            } finally {
                shredDelete(tempDir)
            }
        }


    /** Overwrite then delete every file under [dir]; best-effort. */
    private fun shredDelete(dir: Path) {
        runCatching {
            Files.walk(dir).sorted(Comparator.reverseOrder()).forEach { path ->
                if (Files.isRegularFile(path)) {
                    runCatching { Files.write(path, ByteArray(Files.size(path).toInt())) }
                }
                Files.deleteIfExists(path)
            }
        }
    }
}
