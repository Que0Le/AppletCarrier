package com.example.applet_carrier.api

/**
 * Services injected into an applet when it is started ([Applet.onInit]). Everything
 * here is already scoped to the owning applet's id — applets never see other applets'
 * stores.
 */
class AppletContext(
    val state: StateStore,
    val config: ConfigStore,
    val dialogs: Dialogs,
)
