package com.example.applet_carrier.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.background
import androidx.compose.ui.unit.sp

private val CarrierColorScheme = darkColorScheme(
    primary = CarrierColors.Accent,
    onPrimary = CarrierColors.TextPrimary,
    background = CarrierColors.Background,
    onBackground = CarrierColors.TextPrimary,
    surface = CarrierColors.Surface,
    onSurface = CarrierColors.TextPrimary,
    surfaceVariant = CarrierColors.ElevatedSurface,
    onSurfaceVariant = CarrierColors.TextMuted,
    outline = CarrierColors.Border,
)

// Segoe UI is the safe Windows default; swap for Inter/JetBrains Mono once bundled.
private val CarrierFont = FontFamily.SansSerif

private val CarrierTypography = Typography().run {
    copy(
        bodyMedium = bodyMedium.copy(fontFamily = CarrierFont, fontSize = 13.sp, fontWeight = FontWeight.Normal),
        bodySmall = bodySmall.copy(fontFamily = CarrierFont, fontSize = 11.sp),
        labelLarge = labelLarge.copy(fontFamily = CarrierFont, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        titleSmall = titleSmall.copy(fontFamily = CarrierFont, fontSize = 13.sp, fontWeight = FontWeight.Medium),
    )
}

/** Applies the dark IDE theme and a calm default background. */
@Composable
fun CarrierTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CarrierColorScheme,
        typography = CarrierTypography,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(CarrierColors.Background),
        ) {
            content()
        }
    }
}

/** Convenience default text style for muted secondary labels. */
val MutedLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.bodySmall.copy(color = CarrierColors.TextMuted)
