package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = WhatsAppEmerald,
    onPrimary = Color(0xFF042917),
    primaryContainer = WhatsAppTealDark,
    onPrimaryContainer = Color.White,
    secondary = WhatsAppTealMedium,
    onSecondary = Color.White,
    secondaryContainer = DarkOutgoingBubble,
    onSecondaryContainer = DarkTextPrimary,
    tertiary = PrivacyAmber,
    background = DarkBg,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkCard,
    onSurfaceVariant = DarkTextSecondary,
    error = AlertRed
)

private val LightColorScheme = lightColorScheme(
    primary = WhatsAppTealDark,
    onPrimary = Color.White,
    primaryContainer = WhatsAppTealMedium,
    onPrimaryContainer = Color.White,
    secondary = WhatsAppEmerald,
    onSecondary = Color(0xFF042917),
    secondaryContainer = LightOutgoingBubble,
    onSecondaryContainer = LightTextPrimary,
    tertiary = PrivacyAmber,
    background = LightBg,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightCard,
    onSurfaceVariant = LightTextSecondary,
    error = AlertRed
)

@Composable
fun WhatsuppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
