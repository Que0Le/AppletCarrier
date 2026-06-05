package com.example.applet_carrier.api

/**
 * Shared dialog utilities offered to applets via [AppletContext]. The host owns the
 * actual rendering (see ui/dialogs/DialogHost); applets only see this interface.
 *
 * This is an example set, not exhaustive — extend as common needs appear (AGENTS.md §1).
 */
interface Dialogs {
    /** Yes/No confirmation. [onResult] receives true if confirmed. */
    fun confirm(title: String, message: String, onResult: (Boolean) -> Unit)

    /** Single-line text input. [onResult] receives the text, or null if cancelled. */
    fun textInput(title: String, label: String, initial: String = "", onResult: (String?) -> Unit)
}
