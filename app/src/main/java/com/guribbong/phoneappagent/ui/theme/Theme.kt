package com.guribbong.phoneappagent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors =
    lightColorScheme(
        primary = SignalBlue,
        secondary = MossGreen,
        background = WarmWhite,
        surface = Slate05,
        surfaceVariant = Slate15,
        onSurface = Slate80,
        onSurfaceVariant = Slate40,
    )

private val DarkColors =
    darkColorScheme(
        primary = SignalBlue,
        secondary = MossGreen,
    )

@Composable
fun PhoneAppAgentTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content,
    )
}

