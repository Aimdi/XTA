package com.aimdi.xtanative.ui

import com.aimdi.xtanative.services.links.XLink
import com.aimdi.xtanative.ui.post.CashtagLinks
import com.aimdi.xtanative.ui.post.WindowOpens
import com.aimdi.xtanative.ui.profile.ProfileTab

/**
 * The screen a window of its own opens on for what was clicked in it with
 * Ctrl held or the middle button: a post, a profile, the search of a hashtag
 * or a cashtag, or the X screen a link leads to. A page on the web has none:
 * it stays the browser's.
 */
internal object WindowRoutes {
    /**
     * The route of a link of a post's text by its [tag] (an address, `@name`,
     * `#tag` or `$SYMBOL`); [xScreen] reads which X screen an address is. Null
     * for an address no X screen of the app reads, and a contract address.
     */
    fun of(tag: String, xScreen: (url: String) -> XLink?): String? {
        val text = tag.trim().takeIf { it.length > 1 } ?: return null
        val lower = text.lowercase()
        return when {
            lower.startsWith("https://") || lower.startsWith("http://") -> xScreen(text)?.let(::of)
            text.startsWith('@') -> Routes.profile(text.substring(1))
            text.startsWith('#') -> Routes.search(text)
            CashtagLinks.symbolOf(text) != null -> Routes.search(text)
            else -> null
        }
    }

    /** The route of an X screen [link] names; null for one the app opens elsewhere (the live player). */
    fun of(link: XLink): String? = when (link) {
        is XLink.Post -> Routes.post(link.id)
        is XLink.Profile -> Routes.profile(link.screenName, tab = ProfileTab.of(link.tab))
        is XLink.Community -> if (link.about) Routes.communityAbout(link.id) else Routes.community(link.id)
        is XLink.XList -> Routes.list(link.id)
        else -> null
    }

    /** What opens in windows of their own through [open] (a route), [xScreen] reading the X screen of a link's address. */
    fun opens(open: (route: String) -> Unit, xScreen: (url: String) -> XLink?): WindowOpens = WindowOpens(
        post = { id -> open(Routes.post(id)) },
        profile = { screenName, userId -> open(Routes.profile(screenName, userId)) },
        community = { id -> open(Routes.community(id)) },
        link = { tag -> of(tag, xScreen)?.let { route -> { open(route) } } },
    )
}
