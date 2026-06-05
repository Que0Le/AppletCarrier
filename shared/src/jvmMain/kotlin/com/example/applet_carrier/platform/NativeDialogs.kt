package com.example.applet_carrier.platform

import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/** Native Windows file dialogs via AWT (works alongside Compose Desktop windows). */
object NativeDialogs {

    /** "Save as" dialog, prefilled with [suggestedName]. Returns null if cancelled. */
    fun save(title: String, suggestedName: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = suggestedName
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        return File(dir, name)
    }

    /** "Open" dialog. Returns the chosen file, or null if cancelled. */
    fun open(title: String): File? {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
        dialog.isVisible = true
        val name = dialog.file ?: return null
        val dir = dialog.directory ?: return null
        return File(dir, name)
    }
}
