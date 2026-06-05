package com.example.applet_carrier.applets.list

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.applet_carrier.api.Applet
import com.example.applet_carrier.api.AppletContext
import com.example.applet_carrier.api.AppletMetadata
import com.example.applet_carrier.ui.theme.CarrierColors
import com.example.applet_carrier.ui.theme.CarrierDimens
import com.example.applet_carrier.ui.theme.CarrierFontSizes

/**
 * Example applet: 5 clickable items in a scroll area. Doubles as the scroll-preservation
 * test (AGENTS.md §8) — scroll position and selection survive both tab switching (via the
 * viewport keeping this composed) and app restart (via persisted state).
 */
class ListApplet : Applet() {

    override val metadata = AppletMetadata(id = "list", displayName = "List")

    private var context: AppletContext? = null
    private var initialScroll = 0
    private var initialSelected = -1
    private var lastScroll = 0
    private var lastSelected = -1

    private val items = listOf(
        "Build pipeline" to "Compiles and packages the project",
        "Test runner" to "Executes the unit and integration suites",
        "Static analysis" to "Lints and inspects for code issues",
        "Dependency audit" to "Checks libraries for known advisories",
        "Release packager" to "Produces the distributable installer",
    )

    override fun onInit(context: AppletContext) {
        this.context = context
        initialScroll = context.state.getInt("scroll", 0)
        initialSelected = context.state.getInt("selected", -1)
        lastScroll = initialScroll
        lastSelected = initialSelected
    }

    override fun onShutdown() {
        context?.state?.apply {
            putInt("scroll", lastScroll)
            putInt("selected", lastSelected)
            flush()
        }
    }

    @Composable
    override fun Ui() {
        val scroll = rememberScrollState(initial = initialScroll)
        var selected by remember { mutableStateOf(initialSelected) }

        LaunchedEffect(scroll) { snapshotFlow { scroll.value }.collect { lastScroll = it } }
        LaunchedEffect(selected) { lastSelected = selected }

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(CarrierDimens.gapMd),
        ) {
            items.forEachIndexed { index, (title, subtitle) ->
                ListRow(
                    title = title,
                    subtitle = subtitle,
                    selected = index == selected,
                    onClick = { selected = index },
                )
                Spacer(Modifier.height(CarrierDimens.gapSm))
            }
        }
    }
}

@Composable
private fun ListRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg = when {
        selected -> CarrierColors.SelectionBg
        hovered -> CarrierColors.HoverOverlay
        else -> CarrierColors.Surface
    }
    val borderColor = if (selected) CarrierColors.Accent else CarrierColors.Border

    Column(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(CarrierDimens.radius))
            .background(bg)
            .border(CarrierDimens.borderWidth, borderColor, RoundedCornerShape(CarrierDimens.radius))
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(horizontal = CarrierDimens.gapMd, vertical = CarrierDimens.gapSm),
    ) {
        Text(
            title,
            color = CarrierColors.TextPrimary,
            fontSize = CarrierFontSizes.body,
        )
        Spacer(Modifier.height(CarrierDimens.gapXs))
        Text(
            subtitle,
            color = CarrierColors.TextMuted,
            fontSize = CarrierFontSizes.secondary,
        )
    }
}
