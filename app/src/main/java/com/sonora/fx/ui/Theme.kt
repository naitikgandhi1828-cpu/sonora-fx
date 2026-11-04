package com.sonora.fx.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Sonora FX palette, measured from the original app. Always dark. */
object Sx {
    val Bg = Color(0xFF0D0C11)
    val Card = Color(0xFF18171D)
    val Tile = Color(0xFF212129)
    val Track = Color(0xFF37373F)
    val Thumb = Color(0xFF52525A)
    val Divider = Color(0xFF26252C)
    val Ring = Color(0xFF3A3A44)
    val Accent = Color(0xFFFF874B)
    val Text = Color(0xFFF5F5F7)
    val Sub = Color(0xFF9696A2)
}

@Composable
fun SonoraFxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Sx.Accent,
            onPrimary = Color.Black,
            background = Sx.Bg,
            onBackground = Sx.Text,
            surface = Sx.Bg,
            onSurface = Sx.Text,
            surfaceVariant = Sx.Card,
            onSurfaceVariant = Sx.Sub,
        ),
        content = content,
    )
}
