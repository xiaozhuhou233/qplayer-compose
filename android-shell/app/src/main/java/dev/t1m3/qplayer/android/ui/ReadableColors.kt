package dev.t1m3.qplayer.android.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/** Choose ink from the painted surface, not the system's dark-mode flag. */
internal fun readableInk(background: Color): Color =
    if (background.luminance() > 0.17912878f) Color.Black else Color.White

/** Apply AFTER colour animation so crossfading themes never produces grey-on-grey text. */
internal fun ColorScheme.withReadableContent(): ColorScheme = copy(
    onBackground = readableInk(background),
    onSurface = readableInk(surface),
    onSurfaceVariant = readableInk(surfaceVariant),
    onPrimary = readableInk(primary),
    onSecondary = readableInk(secondary),
    onTertiary = readableInk(tertiary),
    onPrimaryContainer = readableInk(primaryContainer),
    onSecondaryContainer = readableInk(secondaryContainer),
    onTertiaryContainer = readableInk(tertiaryContainer),
    onError = readableInk(error),
    onErrorContainer = readableInk(errorContainer),
    inverseOnSurface = readableInk(inverseSurface),
    onPrimaryFixed = readableInk(primaryFixed),
    onPrimaryFixedVariant = readableInk(primaryFixedDim),
    onSecondaryFixed = readableInk(secondaryFixed),
    onSecondaryFixedVariant = readableInk(secondaryFixedDim),
    onTertiaryFixed = readableInk(tertiaryFixed),
    onTertiaryFixedVariant = readableInk(tertiaryFixedDim),
)
