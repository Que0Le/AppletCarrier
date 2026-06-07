package com.example.applet_carrier.platform

/** Current operating system, used to pick platform-specific command implementations. */
object Os {
    val name: String = System.getProperty("os.name").orEmpty()
    val isWindows: Boolean = name.startsWith("Windows", ignoreCase = true)
    val isMac: Boolean = name.startsWith("Mac", ignoreCase = true) || name.contains("OS X", ignoreCase = true)
    val isLinux: Boolean = name.startsWith("Linux", ignoreCase = true)
    val isUnix: Boolean get() = isMac || isLinux
}
