package com.example.applet_carrier.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.ConfigStore
import com.example.applet_carrier.api.Dialogs

/**
 * Top-level runtime state of the shell: which applets are started and which one is
 * active. Exposes Compose-observable state so the UI recomposes on changes.
 *
 * Started/stopped model (AGENTS.md §4): a started applet has a live [AppletContext] and
 * stays in the composition (viewport keeps it alive while hidden). Stopping tears it
 * down and flushes its state to disk; selecting it again restarts it from disk.
 */
class AppletHost(
    val registry: AppletRegistry,
    val hostConfig: ConfigStore,
    private val storeFactory: StoreFactory,
    private val dialogs: Dialogs,
    private val lifecycle: LifecycleManager = LifecycleManager(),
) {
    val applets: List<Applet> get() = registry.applets

    var activeId by mutableStateOf<String?>(null)
        private set

    // Presence of a key == "started". Observable so the viewport/sidebar recompose.
    private val contexts = mutableStateMapOf<String, AppletContext>()

    val startedApplets: List<Applet>
        get() = applets.filter { contexts.containsKey(it.metadata.id) }

    fun isStarted(id: String): Boolean = contexts.containsKey(id)
    fun contextFor(id: String): AppletContext? = contexts[id]

    /** Start every registered applet (restoring state) and select the first. */
    fun startAll() {
        applets.forEach { start(it.metadata.id) }
        if (activeId == null) activeId = applets.firstOrNull()?.metadata?.id
    }

    fun start(id: String) {
        if (contexts.containsKey(id)) return
        val applet = registry.byId(id) ?: return
        val context = AppletContext(
            state = storeFactory.appletState(id),
            config = storeFactory.appletConfig(id),
            dialogs = dialogs,
        )
        contexts[id] = context
        lifecycle.init(applet, context)
    }

    /** Make [id] the active applet, suspending the previous one. Starts it if needed. */
    fun select(id: String) {
        val prev = activeId
        if (prev == id) return
        if (!contexts.containsKey(id)) start(id)
        prev?.let { registry.byId(it)?.let(lifecycle::suspend) }
        activeId = id
        registry.byId(id)?.let(lifecycle::resume)
    }

    /** Stop a single applet: flush state to disk and tear it down. (Future: stop button.) */
    fun stop(id: String) {
        val applet = registry.byId(id) ?: return
        if (!contexts.containsKey(id)) return
        lifecycle.shutdown(applet)
        contexts.remove(id)
        if (activeId == id) activeId = startedApplets.firstOrNull()?.metadata?.id
    }

    /** Persist every started applet. Call on app close. */
    fun shutdownAll() {
        startedApplets.forEach(lifecycle::shutdown)
    }
}
