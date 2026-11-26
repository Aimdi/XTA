package com.aimdi.xtanative.ui.links

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.aimdi.xtanative.services.links.LinkLaunch

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

/** The desktop's one browser, its default ([BrowserHandoff]): only web links leave, and nothing here throws. */
internal object DesktopLinks : LinkPlatform {
    override val desktop: Boolean = true

    override fun launch(launch: LinkLaunch) {
        val url = launch.url.trim()
        if (!BrowserHandoff.isWeb(url)) return
        SentOutside.sent(url)
        BrowserHandoff.desktop.send(url)
    }
}
