package com.example.applet_carrier

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "applet_carrier",
    ) {
        App()
    }
}