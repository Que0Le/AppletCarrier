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

    /**
     * Save dialog (prefilled with [suggestedName]); on confirm, writes via [write] and
     * returns a status message ("Saved <name>" / "Save failed: …"), or null if cancelled.
     * [ensureExtension], when set, is appended if the chosen file lacks it.
     */
    fun saveViaDialog(
        title: String,
        suggestedName: String,
        ensureExtension: String? = null,
        write: (File) -> Unit,
    ): String? {
        val chosen = save(title, suggestedName) ?: return null
        val file = if (ensureExtension != null && !chosen.extension.equals(ensureExtension, ignoreCase = true)) {
            File(chosen.parentFile, "${chosen.name}.$ensureExtension")
        } else {
            chosen
        }
        return runCatching { write(file) }.fold(
            onSuccess = { "Saved ${file.name}" },
            onFailure = { "Save failed: ${it.message}" },
        )
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
