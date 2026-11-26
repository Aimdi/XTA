package com.aimdi.xtanative.desktop

import androidx.compose.ui.platform.UriHandler
import com.aimdi.xtanative.ui.links.BrowserHandoff

/**
 * Opens a link where the desktop opens links ([BrowserHandoff], off the
 * window's thread). Only web links: a post's link never starts a program of
 * its own. Never throws, so a link in a post's text cannot end the app.
 */
internal object SystemBrowser : UriHandler {
    override fun openUri(uri: String) {
        BrowserHandoff.desktop.send(uri)
    }
}
