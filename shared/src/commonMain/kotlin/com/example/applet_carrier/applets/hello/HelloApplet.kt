package com.example.applet_carrier.applets.hello

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.ui.prefs.ToggleRow
import com.example.applet_carrier.ui.theme.CarrierColors

/**
 * Example applet: a single button that toggles blue → green on click. Demonstrates
 * persisted state (the toggled color survives restart) and a per-applet prefs page
 * (a default start color stored in config).
 */
class HelloApplet : Applet() {

    override val metadata = AppletMetadata(id = "hello", displayName = "Hello")

    override val providesPrefs = true

    private var context: AppletContext? = null
    private var initialGreen = false
    private var lastGreen = false

    override fun onInit(context: AppletContext) {
        this.context = context
        val startGreen = context.config.getBoolean("startGreen", false)
        initialGreen = context.state.getBoolean("green", startGreen)
        lastGreen = initialGreen
    }

    override fun onShutdown() {
        context?.state?.apply {
            putBoolean("green", lastGreen)
            flush()
        }
    }

    @Composable
    override fun Ui() {
        var green by remember { mutableStateOf(initialGreen) }
        LaunchedEffect(green) { lastGreen = green }

        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Button(
                onClick = { green = !green },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (green) CarrierColors.Green else CarrierColors.Accent,
                    contentColor = Color.White,
                ),
            ) {
                Text(if (green) "Green — click to reset" else "Hello — click me")
            }
        }
    }

    @Composable
    override fun PrefsUi() {
        val ctx = context ?: return
        var startGreen by remember { mutableStateOf(ctx.config.getBoolean("startGreen", false)) }
        ToggleRow(
            label = "Start as green",
            checked = startGreen,
            onCheckedChange = {
                startGreen = it
                ctx.config.putBoolean("startGreen", it)
                ctx.config.flush()
            },
        )
        Spacer(Modifier.height(0.dp))
    }
}
