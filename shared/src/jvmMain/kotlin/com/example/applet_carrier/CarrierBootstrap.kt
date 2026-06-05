package com.example.applet_carrier

import com.example.applet_carrier.applets.hello.HelloApplet
import com.example.applet_carrier.applets.list.ListApplet
import com.example.applet_carrier.core.AppletHost
import com.example.applet_carrier.core.AppletRegistry
import com.example.applet_carrier.platform.DesktopStoreFactory
import com.example.applet_carrier.ui.dialogs.DialogHost

/** The wired-up runtime: the host plus the concrete dialog host the shell renders. */
class CarrierRuntime(
    val host: AppletHost,
    val dialogHost: DialogHost,
)

/**
 * Composition root. Registers the hard-coded applets, wires persistence + dialogs, and
 * starts every applet (restoring state). Add new applets to the list here.
 */
object CarrierBootstrap {

    fun create(): CarrierRuntime {
        val registry = AppletRegistry(
            listOf(
                HelloApplet(),
                ListApplet(),
            ),
        )
        val storeFactory = DesktopStoreFactory()
        val dialogHost = DialogHost()

        val host = AppletHost(
            registry = registry,
            hostConfig = storeFactory.hostConfig(),
            storeFactory = storeFactory,
            dialogs = dialogHost,
        )
        host.startAll()

        return CarrierRuntime(host, dialogHost)
    }
}
