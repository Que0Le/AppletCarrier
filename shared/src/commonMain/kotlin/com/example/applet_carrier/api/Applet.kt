package com.example.applet_carrier.api

import androidx.compose.runtime.Composable

/**
 * The contract every applet implements. Applets are plain compiled-in classes for now
 * (no runtime plugin loading). An applet owns the right-hand viewport — and only that —
 * plus its own preferences page and its own persistence files.
 *
 * Lifecycle (see AGENTS.md §4):
 *   onInit  — applet started; restore persisted state here.
 *   onResume/onSuspend — user navigated to / away from this applet (it stays alive).
 *   onShutdown — applet stopped or app closing; persist state here.
 *
 * "Browser-tab" behavior is provided by the host: a started applet's [Ui] stays in the
 * composition while hidden, so remembered UI state (scroll, fields) survives switching.
 */
abstract class Applet {

    abstract val metadata: AppletMetadata

    /** Whether this applet contributes a node under "Applet Settings" in preferences. */
    open val providesPrefs: Boolean get() = false

    open fun onInit(context: AppletContext) {}
    open fun onSuspend() {}
    open fun onResume() {}
    open fun onShutdown() {}

    /** The applet's entire UI, rendered into the right viewport. */
    @Composable
    abstract fun Ui()

    /** Optional settings form. Only consulted when [providesPrefs] is true. */
    @Composable
    open fun PrefsUi() {}
}
