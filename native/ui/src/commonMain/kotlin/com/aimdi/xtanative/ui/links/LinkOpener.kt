package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.services.links.InstalledBrowser
import com.aimdi.xtanative.services.links.LinkLaunch
import com.aimdi.xtanative.services.links.LinkOpening
import com.aimdi.xtanative.services.links.LinkPostContext
import com.aimdi.xtanative.services.links.LinkRouter
import com.aimdi.xtanative.services.links.LinkSettings
import com.aimdi.xtanative.services.links.NativeRoute
import com.aimdi.xtanative.services.links.NetworkLink
import com.aimdi.xtanative.services.links.NetworkLinks
import com.aimdi.xtanative.services.links.XLink
import com.aimdi.xtanative.services.links.XLinks
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.XtaPrefs
import java.net.URI
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * The screens a link can land on, bound by the app's navigation. A network,
 * the live player or a community opens only where the app has a screen for
 * it: those answer true when they opened the link, and a link none of them
 * opens goes to the web.
 */
class LinkScreens(
    val post: (id: String) -> Unit,
    val profile: (screenName: String) -> Unit,
    val search: (query: String) -> Unit,
    /** XTA's browser screen at [url]; [from] finds the post it was opened from, see [LinkOpener.opening]. */
    val browser: (url: String, from: String?) -> Unit,
    val network: (NetworkLink) -> Boolean = { false },
    val live: (XLink.Live) -> Boolean = { false },
    val community: (id: String, about: Boolean) -> Boolean = { _, _ -> false },
    /** Pixiv's search for a work's id; while it is null, a shared number is searched on X. */
    val pixivWork: ((id: String) -> Unit)? = null,
)

/** What only the platform can do with a link that leaves the app for the web. */
interface LinkPlatform {
    /** No web view, no custom tabs, no app a link could bounce back into: the system browser takes every link. */
    val desktop: Boolean

    fun launch(launch: LinkLaunch)

    /** Whether a plain VIEW of [url] would land in this app again: an x.com page it has no screen for. */
    fun reopensApp(url: String): Boolean = false

    /** The platform's share sheet for [text]; false where there is none (then the caller copies it instead). */
    fun share(text: String): Boolean = false

    /** The browsers the reader can pick from; empty where the platform cannot say. */
    fun browsers(): List<InstalledBrowser> = emptyList()
}

/** What the app says about a link it was handed, where the screen it came from cannot. */
enum class LinkNotice { UNABLE_TO_OPEN }

/**
 * The post a link was opened from, for the bar under XTA's browser screen.
 * [openPost] opens the post; null when the post is the screen under the
 * browser, so going back to it is closing the browser (tweet_link_context.dart).
 */
class LinkFrom(val post: LinkPostContext, val openPost: (() -> Unit)?) {
    companion object {
        /** An X post's context, shared under the reader's share host from [settings]. */
        fun x(post: XPost, settings: Settings?, openPost: (() -> Unit)?): LinkFrom =
            LinkFrom(LinkPostContext.of(post, settings?.get(GeneralPrefs.shareBaseUrl)), openPost)
    }
}

/** What a browser screen was opened with: the post it came from and the title the link was opened with. */
class BrowserOpening(val from: LinkFrom?, val title: String?)

/** Where a tapped link goes before anything is opened. */
sealed interface LinkStep {
    data class Post(val id: String) : LinkStep

    data class Profile(val screenName: String) : LinkStep

    /** A network's screen, the live player or a community: opened when the app has one, else [orElse]. */
    data class Screen(val route: NativeRoute, val orElse: LinkLaunch) : LinkStep

    data class Web(val launch: LinkLaunch) : LinkStep
}

/**
 * The rules a link follows (plugin_links.dart, link_opening.dart, urls.dart):
 * an X post or profile opens its screen, a network that reads it and is on,
 * the live player or a community open theirs when the app has them, and
 * anything else leaves for the web the way the reader's link settings say.
 */
object LinkSteps {
    /**
     * Where [url] goes. [inPost] is a link from a post's card, which XTA's own
     * browser screen may show; any other link leaves the app ([LinkOpening.outside]).
     */
    fun step(
        url: String,
        route: NativeRoute?,
        settings: LinkSettings,
        desktop: Boolean,
        reopensApp: Boolean,
        inPost: Boolean,
    ): LinkStep {
        val web = if (inPost) {
            LinkOpening.postLink(url, settings, desktop, reopensApp)
        } else {
            LinkOpening.outside(url, settings, desktop, reopensApp)
        }
        return when (route) {
            null -> LinkStep.Web(web)
            is NativeRoute.X -> when (val link = route.link) {
                is XLink.Post -> LinkStep.Post(link.id)
                is XLink.Profile -> LinkStep.Profile(link.screenName)
                else -> LinkStep.Screen(route, web)
            }
            is NativeRoute.Live, is NativeRoute.Network -> LinkStep.Screen(route, web)
        }
    }

    /** The reader's three link settings as stored; null where a switch was never set, as [LinkSettings] reads them. */
    fun settingsOf(settings: Settings?): LinkSettings {
        settings ?: return LinkSettings()
        fun <T> stored(pref: Pref<T>): T? = if (settings.isSet(pref)) settings[pref] else null
        return LinkSettings(
            inApp = stored(GeneralPrefs.openLinksInApp),
            browser = stored(GeneralPrefs.externalBrowser),
            clean = stored(GeneralPrefs.cleanLinks),
        )
    }

    /** [settings] holding [links], as the browser picker stores a choice. */
    fun withLinkSettings(settings: Settings, links: LinkSettings): Settings {
        var next = settings
        links.inApp?.let { next = next.with(GeneralPrefs.openLinksInApp, it) }
        links.browser?.let { next = next.with(GeneralPrefs.externalBrowser, it) }
        links.clean?.let { next = next.with(GeneralPrefs.cleanLinks, it) }
        return next
    }

    /** Whether the network [id] is switched on in [settings]. */
    fun networkEnabled(settings: Settings?, id: String): Boolean {
        val prefs = XtaPrefs.networks.firstOrNull { it.id == id } ?: return false
        return settings?.get(prefs.enabled) == true
    }

    /** The host of [url] in lower case, or null when it has none. */
    fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull()?.ifEmpty { null }

    /** Whether the app claims [url]'s host, so that a plain VIEW of it could land back in the app. */
    fun claimedByApp(url: String): Boolean = hostOf(url) in XLinks.HOSTS
}

/**
 * The app's one way to open a link. [open] is a link in a post's text, a
 * profile or anywhere else: an XTA screen when one reads it, else out to the
 * web. [openPostLink] is a link from a post's card, which may open XTA's own
 * browser screen with the post in reach. [openOutside] leaves the app.
 *
 * The screens are bound once the navigation exists; a link opened before
 * that (shared from another app while the session loads) waits for them.
 */
class LinkOpener(
    private val platform: LinkPlatform,
    private val settings: () -> Settings?,
    private val router: (Settings?) -> LinkRouter = { current -> defaultRouter(current, platform.desktop) },
) {
    @Volatile
    private var screens: LinkScreens? = null
    private var waiting: (() -> Unit)? = null
    private val openings = object : LinkedHashMap<String, BrowserOpening>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, BrowserOpening>?) = size > KEPT_OPENINGS
    }
    private var tokens = 0
    private val said = MutableSharedFlow<LinkNotice>(extraBufferCapacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    val desktop: Boolean get() = platform.desktop

    /** What the app tells the reader about links, as it happens. */
    val notices: SharedFlow<LinkNotice> = said.asSharedFlow()

    fun notify(notice: LinkNotice) {
        said.tryEmit(notice)
    }

    fun browsers(): List<InstalledBrowser> = platform.browsers()

    /** Whether the screens are bound, so a link opened now opens at once. */
    val bound: Boolean get() = screens != null

    /** Makes [screens] the ones links open on, and opens the link that was waiting for them. */
    fun bind(screens: LinkScreens) {
        val pending = synchronized(this) {
            this.screens = screens
            waiting.also { waiting = null }
        }
        pending?.invoke()
    }

    fun unbind(screens: LinkScreens) {
        synchronized(this) { if (this.screens === screens) this.screens = null }
    }

    fun open(url: String) = go(url, inPost = false, from = null, title = null)

    fun openPostLink(url: String, from: LinkFrom? = null, title: String? = null) = go(url, inPost = true, from = from, title = title)

    /** Leaves the app for [url], whatever XTA screen could read it (`openUri`). */
    fun openOutside(url: String) {
        val current = settings()
        platform.launch(LinkOpening.outside(url, LinkSteps.settingsOf(current), platform.desktop, reopensApp(url)))
    }

    /** "Open in browser" from XTA's browser screen: the picked browser, or the system's choice. */
    fun leaveBrowserScreen(url: String) {
        platform.launch(LinkOpening.leaveBrowserScreen(url, LinkSteps.settingsOf(settings())))
    }

    /** [url] as it leaves the app: without tracking unless the reader turned that off. */
    fun prepare(url: String): String = LinkOpening.prepare(url, LinkSteps.settingsOf(settings()))

    /** The platform's share sheet for [text]; false where there is none. */
    fun share(text: String): Boolean = platform.share(text)

    /**
     * Opens [url] on an XTA screen when one reads it, answering whether one
     * did: a web page in XTA's browser asks this before it navigates.
     */
    fun openOnScreen(url: String): Boolean {
        val screens = screens ?: return false
        val current = settings()
        return when (val step = LinkSteps.step(url, router(current).route(url), LinkSteps.settingsOf(current), platform.desktop, false, inPost = false)) {
            is LinkStep.Post -> true.also { screens.post(step.id) }
            is LinkStep.Profile -> true.also { screens.profile(step.screenName) }
            is LinkStep.Screen -> openScreen(screens, step.route)
            is LinkStep.Web -> false
        }
    }

    /** Opens [url] on a network's screen when one is on and reads it, answering whether one did. */
    fun openOnNetwork(url: String): Boolean {
        val screens = screens ?: return false
        val route = router(settings()).route(url) as? NativeRoute.Network ?: return false
        return screens.network(route.link)
    }

    /** What the browser screen [token] names was opened with; null once it is forgotten. */
    @Synchronized
    fun opening(token: String?): BrowserOpening? = token?.let { openings[it] }

    private fun go(url: String, inPost: Boolean, from: LinkFrom?, title: String?) {
        val screens = synchronized(this) {
            screens ?: run {
                waiting = { go(url, inPost, from, title) }
                return
            }
        }
        val current = settings()
        val step = LinkSteps.step(url, router(current).route(url), LinkSteps.settingsOf(current), platform.desktop, reopensApp(url), inPost)
        when (step) {
            is LinkStep.Post -> screens.post(step.id)
            is LinkStep.Profile -> screens.profile(step.screenName)
            is LinkStep.Screen -> if (!openScreen(screens, step.route)) web(screens, step.orElse, from, title)
            is LinkStep.Web -> web(screens, step.launch, from, title)
        }
    }

    private fun openScreen(screens: LinkScreens, route: NativeRoute): Boolean = when (route) {
        is NativeRoute.Network -> screens.network(route.link)
        is NativeRoute.Live -> screens.live(route.live)
        is NativeRoute.X -> when (val link = route.link) {
            is XLink.Post -> true.also { screens.post(link.id) }
            is XLink.Profile -> true.also { screens.profile(link.screenName) }
            is XLink.Community -> screens.community(link.id, link.about)
            else -> false
        }
    }

    private fun web(screens: LinkScreens, launch: LinkLaunch, from: LinkFrom?, title: String?) {
        if (launch is LinkLaunch.InAppBrowser) {
            screens.browser(launch.url, remember(BrowserOpening(from, title)))
        } else {
            platform.launch(launch)
        }
    }

    @Synchronized
    private fun remember(opening: BrowserOpening): String? {
        if (opening.from == null && opening.title == null) return null
        val token = "l" + ++tokens
        openings[token] = opening
        return token
    }

    private fun reopensApp(url: String): Boolean = !platform.desktop && platform.reopensApp(url)

    companion object {
        /** Browser screens whose post is still in reach: the ones on the back stack, in practice a few. */
        private const val KEPT_OPENINGS = 32

        /**
         * XTA's link order over the networks switched on in [settings]. A
         * network's screen decides for itself whether it reads a link it is
         * handed (Pixiv, a booru, Mastodon's hosts): none is asked here.
         */
        fun defaultRouter(settings: Settings?, desktop: Boolean): LinkRouter =
            LinkRouter(NetworkLinks(), enabled = { id -> LinkSteps.networkEnabled(settings, id) }, desktop = desktop)
    }
}
