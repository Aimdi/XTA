package com.aimdi.xtanative.desktop

import androidx.compose.ui.platform.UriHandler
import java.awt.Desktop
import java.net.URI

/**
 * Opens a link where the desktop opens links: through java.awt.Desktop, or
 * xdg-open where that cannot (desktops without GNOME's libraries). Only web
 * links: a post's link never starts a program of its own. Never throws, so a
 * link in a post's text cannot end the app.
 */
internal object SystemBrowser : UriHandler {
    override fun openUri(uri: String) {
        if (!uri.startsWith("https://") && !uri.startsWith("http://")) return
        if (!browse(uri)) runCatching { ProcessBuilder("xdg-open", uri).start() }
    }

    private fun browse(uri: String): Boolean = runCatching {
        val desktop = Desktop.getDesktop()
        desktop.isSupported(Desktop.Action.BROWSE).also { supported -> if (supported) desktop.browse(URI(uri)) }
    }.getOrDefault(false)
}
