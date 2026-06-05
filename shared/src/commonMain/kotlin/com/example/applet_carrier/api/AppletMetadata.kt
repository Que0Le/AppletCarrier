package com.example.applet_carrier.api

import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Static descriptor for an applet, shown in the sidebar.
 *
 * @param id stable identifier; also the stem of this applet's persistence files
 *           (normalized to ASCII). Must be unique and stable across versions.
 * @param displayName human-readable name shown in the sidebar / prefs tree.
 * @param icon optional vector icon; when null the sidebar renders an initial badge.
 */
data class AppletMetadata(
    val id: String,
    val displayName: String,
    val icon: ImageVector? = null,
)
