package com.baxailab.cadebot.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val VivaColorScheme = lightColorScheme(
    primary = VivaEspresso,
    onPrimary = VivaOnDark,
    primaryContainer = VivaCoffee,
    onPrimaryContainer = VivaCream,
    secondary = VivaCaramel,
    onSecondary = VivaEspresso,
    secondaryContainer = VivaLatte,
    onSecondaryContainer = VivaEspresso,
    tertiary = VivaCoffee,
    background = VivaFoam,
    onBackground = VivaEspresso,
    surface = VivaSurface,
    onSurface = VivaEspresso,
    surfaceVariant = VivaCream,
    onSurfaceVariant = VivaCoffee,
    error = VivaError,
    outline = VivaLatte
)

@Composable
fun CadebotTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = VivaColorScheme,
        typography = VivaTypography,
        content = content
    )
}
