package com.aimdi.xtanative.ui.links

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.services.links.LinkLaunch
import java.awt.Desktop
import java.net.URI

@Composable
internal actual fun rememberLinkPlatform(): LinkPlatform = DesktopLinks

/** Nothing is shared to the desktop app. */
@Composable
internal actual fun SharedTextEffect(onText: (String) -> Unit) = Unit

/** Hands [onLink] each link `xta-native <url>` opened here ([DesktopIncomingLinks]). */
@Composable
internal actual fun IncomingLinkEffect(onLink: (String) -> Unit) {
    val latest by rememberUpdatedState(onLink)
    LaunchedEffect(Unit) { DesktopIncomingLinks.links.collect { latest(it) } }
}

/**
 * The desktop's one browser, its default: java.awt.Desktop, or xdg-open where
 * that cannot (desktops without GNOME's libraries). Only web links leave: a
 * post's link never starts a program of its own, and nothing here throws.
 */
internal object DesktopLinks : LinkPlatform {
    override val desktop: Boolean = true

    override fun launch(launch: LinkLaunch) {
        val url = launch.url.trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) return
        if (!browse(url)) runCatching { ProcessBuilder("xdg-open", url).start() }
    }

    private fun browse(url: String): Boolean = runCatching {
        val desktop = Desktop.getDesktop()
        desktop.isSupported(Desktop.Action.BROWSE).also { supported -> if (supported) desktop.browse(URI(url)) }
    }.getOrDefault(false)
}
