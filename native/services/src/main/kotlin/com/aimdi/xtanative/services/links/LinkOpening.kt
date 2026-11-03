package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.services.util.WebLink

/**
 * The reader's three link settings as stored; null is a setting never made.
 * [inApp] keeps links in the app, [browser] is the package of the browser
 * picked in settings (empty for the system's choice), [clean] strips
 * tracking parameters before a link leaves.
 */
data class LinkSettings(val inApp: Boolean? = null, val browser: String? = null, val clean: Boolean? = null)

/** Where XTA opens a link before any browser sees it. */
sealed interface NativeRoute {
    /** A network's own screen. */
    data class Network(val link: NetworkLink) : NativeRoute

    /** The live player: a broadcast, a Periscope watch link or a Space. */
    data class Live(val live: XLink.Live) : NativeRoute

    /** An X screen: a post, a profile or a community. */
    data class X(val link: XLink) : NativeRoute
}

/** How a link that no XTA screen reads leaves for the web. */
sealed interface LinkLaunch {
    /** The address to open: tracking already stripped when the reader asked for that. */
    val url: String

    /** XTA's own browser screen, with the post the link came from in reach. */
    data class InAppBrowser(override val url: String) : LinkLaunch

    /** The platform's in-app browser view (Custom Tabs on Android). */
    data class CustomTab(override val url: String) : LinkLaunch

    /**
     * Another app; [browser] is the package picked in settings, empty for the
     * system's choice. When that browser cannot take the link (it can be
     * uninstalled at any time) the system's choice opens it: a link that does
     * nothing is worse than one in the wrong browser.
     */
    data class External(override val url: String, val browser: String) : LinkLaunch

    /**
     * A link XTA's own app claims (a broadcast, a Space, a Grok share, a
     * Pixiv page): a plain VIEW would come back to XTA, so it goes to a
     * browser by name — [browser] when one was picked; then a Custom Tab when
     * [customTabFirst], else the system's default browser by its package, and
     * a Custom Tab when that fails too.
     */
    data class RealBrowser(override val url: String, val browser: String, val customTabFirst: Boolean) : LinkLaunch

    /** The desktop's default browser: it has no other, and no app a link could bounce back into. */
    data class Desktop(override val url: String) : LinkLaunch
}

/**
 * The order XTA opens a tapped link in (plugin_links.dart, link_opening.dart,
 * urls.dart): a network that reads it and is switched on, then the live
 * player, then the X screens; only what none of them reads goes to the web,
 * through the reader's browser setting.
 *
 * [enabled] says whether a network is switched on. [substack] is Substack's
 * reader: a post of a known publication opens there first, before any other
 * network, as XTA does; asking an unknown domain whether it is a Substack
 * costs a request, which is that network's to make. Neither happens on the
 * desktop, and neither does a TikTok video, whose player needs a web view.
 */
class LinkRouter(
    private val networks: NetworkLinks,
    private val enabled: (network: String) -> Boolean,
    private val substack: (String) -> Boolean = { false },
    private val desktop: Boolean = false,
) {
    fun route(url: String): NativeRoute? {
        if (!desktop && enabled(NetworkLink.SUBSTACK) && substack(url)) {
            return NativeRoute.Network(NetworkLink.Claimed(NetworkLink.SUBSTACK, url))
        }
        networks.parse(url)
            ?.takeIf { enabled(it.network) && !(desktop && it is NetworkLink.TikTokVideo) }
            ?.let { return NativeRoute.Network(it) }
        if (XLinks.isLiveWatchUrl(url)) (XLinks.parse(url) as? XLink.Live)?.let { return NativeRoute.Live(it) }
        return xRoute(url)
    }

    /** An x.com or twitter.com post, profile or community; lists, Grok shares and the rest go to the web. */
    private fun xRoute(url: String): NativeRoute? {
        if (WebLink.parse(url.trim())?.host !in XLinks.HOSTS) return null
        return when (val link = XLinks.parse(url)) {
            is XLink.Post, is XLink.Profile, is XLink.Community -> NativeRoute.X(link)
            is XLink.Live -> NativeRoute.Live(link)
            else -> null
        }
    }
}

/** How a link leaves for the web once no XTA screen read it. */
object LinkOpening {
    /** A web address a browser screen can show at all: http(s) with a host. */
    fun isBrowsable(url: String): Boolean = WebLink.parse(url)?.isWeb == true

    /**
     * Whether links stay in the app. An unset switch keeps them in: XTA turns
     * it on at first launch, and settings stores false the moment a browser
     * is picked. The desktop has no web view, so never there.
     */
    fun inAppBrowserEnabled(settings: LinkSettings, desktop: Boolean): Boolean = !desktop && settings.inApp != false

    /** [url] as it leaves the app: without tracking unless the reader turned that off. */
    fun prepare(url: String, settings: LinkSettings): String = XLinks.prepare(url, settings.clean)

    /**
     * A link tapped inside a post (`openPostLink`): XTA's browser screen
     * when links stay in the app and it can show this one, else [outside].
     */
    fun postLink(url: String, settings: LinkSettings, desktop: Boolean = false, reopensApp: Boolean = false): LinkLaunch =
        if (isBrowsable(url) && inAppBrowserEnabled(settings, desktop)) {
            LinkLaunch.InAppBrowser(prepare(url, settings))
        } else {
            outside(url, settings, desktop, reopensApp)
        }

    /**
     * A link leaving the app (`openUri`). [reopensApp] is the platform's
     * answer to whether a plain VIEW of it would land in XTA again (a Pixiv
     * page XTA opens by default); X pages XTA has no screen for always would.
     * Unlike [postLink], a Custom Tab is used only when the switch is on, not
     * when it was never set.
     */
    fun outside(url: String, settings: LinkSettings, desktop: Boolean = false, reopensApp: Boolean = false): LinkLaunch {
        val prepared = prepare(url, settings)
        return when {
            desktop -> LinkLaunch.Desktop(prepared)
            XLinks.needsNamedBrowser(url) || reopensApp ->
                LinkLaunch.RealBrowser(prepared, settings.browser.orEmpty(), customTabFirst = settings.inApp == true)
            settings.inApp == true -> LinkLaunch.CustomTab(prepared)
            else -> LinkLaunch.External(prepared, settings.browser.orEmpty())
        }
    }

    /** "Open in browser" from XTA's browser screen: the picked browser, or the system's choice. */
    fun leaveBrowserScreen(url: String, settings: LinkSettings): LinkLaunch.External =
        LinkLaunch.External(prepare(url, settings), settings.browser.orEmpty())

    /**
     * Whether [url] is a page on a host the Android app claims (pixiv.net,
     * pixiv.me): only then can a plain VIEW come back to the app, which the
     * platform then asks its resolver about. A launch by name never names the
     * app's own package.
     */
    fun onClaimedHost(url: String): Boolean {
        val link = WebLink.parse(url.trim()) ?: return false
        return link.isWeb && link.host in PixivHosts.CLAIMED
    }
}
