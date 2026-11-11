package com.aimdi.xtanative.ui.plugins

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import com.aimdi.xtanative.ui.feed.ProvenanceColors

/**
 * Whose screen is being drawn, as the app says it around every plugin's
 * bodies and screens: the plugin's [id], its [name] in the reader's language,
 * its brand [color] and [icon], and [openSettings] where it has settings. The
 * shared pieces read it so a plugin does not hand them over: a failure's
 * sheet names the plugin and offers its settings, the tab strip is underlined
 * in its colour.
 */
class PluginSurface(
    val id: String,
    val name: String,
    val color: Color,
    val icon: ImageVector,
    val openSettings: (() -> Unit)? = null,
)

/** The plugin the shared pieces are drawn for; null outside a plugin's screens (a preview, a test). */
val LocalPluginSurface = staticCompositionLocalOf<PluginSurface?> { null }

/**
 * The colour a plugin's chrome is marked in on the theme's background, as
 * XTA's plugin chrome picks it: [accent], else the plugin's own brand, else
 * the theme's primary, lightened on a dark theme when it is nearly black and
 * taken toward black or white until it reads there.
 */
@Composable
fun pluginAccent(accent: Color? = null): Color {
    val wanted = accent ?: LocalPluginSurface.current?.color ?: MaterialTheme.colorScheme.primary
    val ground = MaterialTheme.colorScheme.background
    return remember(wanted, ground) {
        val dark = ProvenanceColors.luminance(ground.toArgb()) < 0.5
        Color(ProvenanceColors.readable(ProvenanceColors.brandOn(wanted.toArgb(), dark), ground.toArgb()))
    }
}
