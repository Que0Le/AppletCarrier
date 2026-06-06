package com.example.applet_carrier.platform

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

/**
 * Copy plain text to the system clipboard (desktop). Uses AWT directly rather than
 * Compose's clipboard local — synchronous, no coroutine needed, and not tied to the
 * deprecated `LocalClipboardManager` / evolving `LocalClipboard` API.
 */
fun copyToClipboard(text: String) {
    if (text.isEmpty()) return
    val selection = StringSelection(text)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(selection, selection)
}
