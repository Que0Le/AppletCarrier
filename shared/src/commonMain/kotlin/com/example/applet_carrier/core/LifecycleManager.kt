package com.example.applet_carrier.core

import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext

/**
 * Thin seam around the applet lifecycle hooks. Centralizing the calls here gives one
 * place to later add cross-cutting concerns (logging, error isolation, timing) without
 * touching [AppletHost].
 */
class LifecycleManager {
    fun init(applet: Applet, context: AppletContext) = applet.onInit(context)
    fun suspend(applet: Applet) = applet.onSuspend()
    fun resume(applet: Applet) = applet.onResume()
    fun shutdown(applet: Applet) = applet.onShutdown()
}
