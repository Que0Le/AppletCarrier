package com.example.applet_carrier.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Layered dark palette (slate/graphite, never pure black). See AGENTS.md §7.
 * Surfaces are separated by luminance, not shadow. Accent is used sparingly.
 */
object CarrierColors {
    val Background = Color(0xFF1E1F22)
    val Surface = Color(0xFF2B2D30)
    val ElevatedSurface = Color(0xFF35373B)

    val Border = Color(0x0FFFFFFF)        // white @ ~6%
    val BorderStrong = Color(0x66000000)  // ambient dark seam

    val TextPrimary = Color(0xFFDFE1E5)
    val TextMuted = Color(0xFF9DA1A8)

    val Accent = Color(0xFF4B9FFF)

    // Restrained secondary accents — use sparingly.
    val Cyan = Color(0xFF3FB6C4)
    val Violet = Color(0xFF9A86FF)
    val Magenta = Color(0xFFD16FCB)

    // Interaction overlays.
    val HoverOverlay = Color(0x0AFFFFFF)   // white @ ~4%
    val SelectionBg = Color(0x1F4B9FFF)    // accent, low alpha

    // Demo color for the Hello applet's toggled state.
    val Green = Color(0xFF4FB06A)

    // Desaturated red for destructive actions (e.g. kill). Restrained, not bright.
    val Danger = Color(0xFFD16F6F)
}
