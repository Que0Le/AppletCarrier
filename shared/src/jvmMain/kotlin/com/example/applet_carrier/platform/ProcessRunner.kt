package com.example.applet_carrier.platform

/**
 * Run an external [command], capturing combined stdout+stderr and returning
 * (exitCode, output). Blocking — call from a background dispatcher, not the UI thread.
 *
 * Shared by the platform applets that shell out (port lookup, key generation, and future
 * macOS `lsof`/`kill` implementations).
 */
internal fun runProcess(command: List<String>): Pair<Int, String> {
    val process = ProcessBuilder(command).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    return process.waitFor() to output
}
