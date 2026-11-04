package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.links.InstalledBrowser
import com.aimdi.xtanative.services.links.LinkDestination
import com.aimdi.xtanative.services.links.LinkLaunch
import com.aimdi.xtanative.services.links.NetworkLink
import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkOpenerTest {
    private class Platform(override val desktop: Boolean = false, private val reopens: (String) -> Boolean = { false }) : LinkPlatform {
        val launched = ArrayList<LinkLaunch>()

        override fun launch(launch: LinkLaunch) {
            launched += launch
        }

        override fun reopensApp(url: String) = reopens(url)
    }

    private class Screens {
        val opened = ArrayList<String>()
        var readsNetworks = false
        var hasXScreens = false
        var pluginHosts = emptySet<String>()
        val screens = LinkScreens(
            post = { opened += "post $it" },
            profile = { opened += "profile $it" },
            search = { opened += "search $it" },
            browser = { url, from -> opened += "browser $url ${from ?: "-"}" },
            network = { link -> readsNetworks.also { if (it) opened += "network ${link.network}" } },
            community = { id, about -> hasXScreens.also { if (it) opened += listOfNotNull("community", id, "about".takeIf { about }).joinToString(" ") } },
            list = { id -> hasXScreens.also { if (it) opened += "list $id" } },
            plugin = { url -> (LinkSteps.hostOf(url) in pluginHosts).also { if (it) opened += "plugin $url" } },
        )
    }

    private fun settings(vararg values: Pair<String, Any>): Settings = values.fold(Settings()) { s, (key, value) ->
        when (key) {
            "inApp" -> s.with(GeneralPrefs.openLinksInApp, value as Boolean)
            "browser" -> s.with(GeneralPrefs.externalBrowser, value as String)
            "clean" -> s.with(GeneralPrefs.cleanLinks, value as Boolean)
            "bluesky" -> s.with(BlueskyPrefs.enabled, value as Boolean)
            else -> error(key)
        }
    }

    private fun opener(platform: Platform, settings: Settings? = Settings()) = LinkOpener(platform, { settings })

    @Test
    fun xPostsAndProfilesOpenTheirScreens() {
        val screens = Screens()
        val opener = opener(Platform()).also { it.bind(screens.screens) }
        opener.open("https://x.com/maya/status/123?s=20")
        opener.open("https://twitter.com/maya")
        opener.openPostLink("https://mobile.x.com/nasa/status/456/photo/1")
        assertEquals(listOf("post 123", "profile maya", "post 456"), screens.opened)
    }

    @Test
    fun aLinkInTextLeavesForTheChosenBrowserWithoutTracking() {
        val platform = Platform()
        val opener = opener(platform, settings("inApp" to false, "browser" to "org.mozilla.firefox"))
        opener.bind(Screens().screens)
        opener.open("https://example.com/story?utm_source=x&id=7")
        assertEquals(listOf<LinkLaunch>(LinkLaunch.External("https://example.com/story?id=7", "org.mozilla.firefox")), platform.launched)
    }

    @Test
    fun aLinkInTextOpensACustomTabWhenLinksStayInTheApp() {
        val platform = Platform()
        val opener = opener(platform, settings("inApp" to true))
        opener.bind(Screens().screens)
        opener.open("https://example.com/a")
        assertEquals(listOf<LinkLaunch>(LinkLaunch.CustomTab("https://example.com/a")), platform.launched)
    }

    @Test
    fun aCardLinkOpensTheBrowserScreenWithItsPost() {
        val screens = Screens()
        val opener = opener(Platform(), settings("inApp" to true))
        opener.bind(screens.screens)
        val author = XUser("1", "maya", "Maya", likes = null)
        val post = XPost("9", author, null, "text", replyCount = 3, repostCount = 2, quoteCount = 1, likeCount = 40)
        var opened = 0
        opener.openPostLink("https://example.com/read", LinkFrom.x(post, Settings(), openPost = { opened++ }), title = "Read")
        val line = screens.opened.single()
        assertTrue(line.startsWith("browser https://example.com/read "))
        val opening = opener.opening(line.substringAfterLast(' '))!!
        assertEquals("Maya", opening.from?.post?.author)
        assertEquals(3L, opening.from?.post?.reposts)
        assertEquals("https://x.com/maya/status/9", opening.from?.post?.postUrl)
        assertEquals("Read", opening.title)
        opening.from?.openPost?.invoke()
        assertEquals(1, opened)
    }

    @Test
    fun aCardLinkWithoutContextOpensTheBrowserScreenAlone() {
        val screens = Screens()
        val opener = opener(Platform(), Settings())
        opener.bind(screens.screens)
        opener.openPostLink("https://example.com/plain")
        assertEquals(listOf("browser https://example.com/plain -"), screens.opened)
    }

    @Test
    fun theDesktopHandsEveryWebLinkToTheSystemBrowser() {
        val platform = Platform(desktop = true)
        val screens = Screens()
        val opener = opener(platform, settings("inApp" to true))
        opener.bind(screens.screens)
        opener.openPostLink("https://example.com/b")
        opener.open("https://x.com/i/spaces/1ABC")
        assertEquals(listOf<LinkLaunch>(LinkLaunch.Desktop("https://example.com/b"), LinkLaunch.Desktop("https://x.com/i/spaces/1ABC")), platform.launched)
        assertTrue(screens.opened.isEmpty())
    }

    @Test
    fun anXPageWithoutAScreenGoesToANamedBrowser() {
        val platform = Platform(reopens = LinkSteps::claimedByApp)
        val opener = opener(platform, settings("inApp" to false, "browser" to "com.brave.browser"))
        opener.bind(Screens().screens)
        opener.open("https://x.com/i/lists/42")
        opener.open("https://x.com/i/broadcasts/1xyz")
        assertEquals(
            listOf<LinkLaunch>(
                LinkLaunch.RealBrowser("https://x.com/i/lists/42", "com.brave.browser", customTabFirst = false),
                LinkLaunch.RealBrowser("https://x.com/i/broadcasts/1xyz", "com.brave.browser", customTabFirst = false),
            ),
            platform.launched,
        )
    }

    @Test
    fun aCommunityOrAListOpensItsScreenOnceTheAppHasOne() {
        val platform = Platform(reopens = LinkSteps::claimedByApp)
        val screens = Screens().apply { hasXScreens = true }
        val opener = opener(platform, settings("inApp" to false))
        opener.bind(screens.screens)
        opener.open("https://x.com/i/communities/9/about")
        opener.open("https://twitter.com/i/lists/42?s=20")
        assertTrue(opener.openOnScreen("https://mobile.x.com/i/lists/43"))
        opener.open("https://example.com/i/lists/44")
        assertEquals(listOf("community 9 about", "list 42", "list 43"), screens.opened)
        assertEquals(listOf<LinkLaunch>(LinkLaunch.External("https://example.com/i/lists/44", "")), platform.launched)
    }

    @Test
    fun aNetworkThatIsOnAndHasAScreenOpensItsLinks() {
        val platform = Platform()
        val screens = Screens()
        val opener = opener(platform, settings("bluesky" to true, "inApp" to false))
        opener.bind(screens.screens)
        opener.open("https://bsky.app/profile/someone.bsky.social")
        assertEquals(listOf<LinkLaunch>(LinkLaunch.External("https://bsky.app/profile/someone.bsky.social", "")), platform.launched)
        screens.readsNetworks = true
        opener.open("https://bsky.app/profile/someone.bsky.social")
        assertEquals(listOf("network ${NetworkLink.BLUESKY}"), screens.opened)
    }

    @Test
    fun aNetworkThatIsOffIsNotAsked() {
        val screens = Screens().apply { readsNetworks = true }
        val platform = Platform()
        val opener = opener(platform, settings("bluesky" to false, "inApp" to false))
        opener.bind(screens.screens)
        opener.open("https://bsky.app/profile/someone.bsky.social")
        assertTrue(screens.opened.isEmpty())
        assertEquals(1, platform.launched.size)
    }

    @Test
    fun anInstalledPluginOpensItsLinksBeforeAnyRule() {
        val screens = Screens().apply {
            readsNetworks = true
            pluginHosts = setOf("bsky.app")
        }
        val platform = Platform()
        val opener = opener(platform, settings("bluesky" to true, "inApp" to false))
        opener.bind(screens.screens)
        val links = listOf(
            "https://bsky.app/profile/someone.bsky.social",
            "https://bsky.app/profile/someone.bsky.social/post/3l6",
            "https://bsky.app/profile/other.bsky.social",
            "https://bsky.app/profile/third.bsky.social",
        )
        opener.open(links[0])
        opener.openPostLink(links[1])
        assertTrue(opener.openOnScreen(links[2]))
        assertTrue(opener.openOnNetwork(links[3]))
        assertEquals(links.map { "plugin $it" }, screens.opened)
        assertTrue(platform.launched.isEmpty())
        opener.open("https://x.com/maya")
        assertEquals("profile maya", screens.opened.last())
    }

    @Test
    fun aLinkWaitsForTheScreensToBeBound() {
        val screens = Screens()
        val opener = opener(Platform())
        opener.open("https://x.com/maya/status/1")
        assertTrue(screens.opened.isEmpty())
        opener.bind(screens.screens)
        assertEquals(listOf("post 1"), screens.opened)
    }

    @Test
    fun theBrowserScreenAsksBeforeItNavigates() {
        val screens = Screens()
        val opener = opener(Platform())
        assertFalse(opener.openOnScreen("https://x.com/maya/status/2"))
        opener.bind(screens.screens)
        assertTrue(opener.openOnScreen("https://x.com/maya/status/2"))
        assertFalse(opener.openOnScreen("https://example.com/next"))
        assertEquals(listOf("post 2"), screens.opened)
    }

    @Test
    fun leavingTheBrowserScreenUsesThePickedBrowser() {
        val platform = Platform()
        val opener = opener(platform, settings("inApp" to true, "browser" to "org.mozilla.firefox", "clean" to false))
        opener.leaveBrowserScreen("https://example.com/?utm_source=a")
        opener.openOutside("https://example.com/x")
        assertEquals(
            listOf<LinkLaunch>(LinkLaunch.External("https://example.com/?utm_source=a", "org.mozilla.firefox"), LinkLaunch.CustomTab("https://example.com/x")),
            platform.launched,
        )
    }

    @Test
    fun leavingTheBrowserScreenForAPageTheAppClaimsNamesABrowser() {
        val platform = Platform(reopens = LinkSteps::claimedByApp)
        val opener = opener(platform, settings("browser" to "com.brave.browser"))
        opener.leaveBrowserScreen("https://x.com/i/grok/share/abc?s=20")
        opener.leaveBrowserScreen("https://example.com/a")
        assertEquals(
            listOf<LinkLaunch>(
                LinkLaunch.RealBrowser("https://x.com/i/grok/share/abc", "com.brave.browser", customTabFirst = false),
                LinkLaunch.External("https://example.com/a", "com.brave.browser"),
            ),
            platform.launched,
        )
    }

    @Test
    fun aPageTheAppClaimsGoesToARealBrowserNeverToTheApp() {
        val self = "com.aimdi.xtanative"
        val installed = listOf(InstalledBrowser(self, "XTA Native"), InstalledBrowser("org.mozilla.firefox", "Firefox"))
        assertEquals("com.brave.browser", LinkSteps.realBrowser("com.brave.browser", installed, self))
        assertEquals("org.mozilla.firefox", LinkSteps.realBrowser("android", installed, self), "Android's chooser is no browser")
        assertEquals("org.mozilla.firefox", LinkSteps.realBrowser(null, installed, self))
        assertNull(LinkSteps.realBrowser(self, installed.take(1), self))
    }

    @Test
    fun thePickerShowsAndStoresWhereLinksOpen() {
        val browsers = listOf(InstalledBrowser("org.mozilla.firefox", "Firefox"))
        assertEquals(LinkDestination.InApp, LinkSteps.pickedBrowser(Settings(), browsers), "links open in the app until a browser is picked")
        val picked = LinkSteps.withPicked(Settings(), LinkDestination.Browser("org.mozilla.firefox"))
        assertFalse(picked[GeneralPrefs.openLinksInApp])
        assertEquals(LinkDestination.Browser("org.mozilla.firefox"), LinkSteps.pickedBrowser(picked, browsers))
        assertEquals(LinkDestination.SystemDefault, LinkSteps.pickedBrowser(picked, emptyList()), "a browser since uninstalled is the system's choice")
        val inApp = LinkSteps.withPicked(picked, LinkDestination.InApp)
        assertEquals("org.mozilla.firefox", inApp[GeneralPrefs.externalBrowser])
        assertEquals(LinkDestination.InApp, LinkSteps.pickedBrowser(inApp, browsers))
        assertEquals(LinkDestination.SystemDefault, LinkSteps.pickedBrowser(LinkSteps.withPicked(inApp, LinkDestination.SystemDefault), browsers))
    }

    @Test
    fun aCardOnItsPostsOwnScreenHasNoWayBackToThePost() {
        val post = XPost("5", XUser("1", "maya", "Maya"), null, "t")
        var opened = 0
        assertNull(LinkFrom.card(post, null, onPostScreen = true) { opened++ }.openPost)
        LinkFrom.card(post, null, onPostScreen = false) { opened++ }.openPost?.invoke()
        assertEquals(1, opened)
    }

    @Test
    fun linkSettingsReadUnsetSwitchesAsUnset() {
        assertNull(LinkSteps.settingsOf(Settings()).inApp)
        assertNull(LinkSteps.settingsOf(null).browser)
        val stored = LinkSteps.settingsOf(settings("inApp" to false, "browser" to "a.b", "clean" to false))
        assertEquals(false, stored.inApp)
        assertEquals("a.b", stored.browser)
        assertEquals(false, stored.clean)
        val written = LinkSteps.withLinkSettings(Settings(), stored)
        assertEquals(false, written[GeneralPrefs.openLinksInApp])
        assertEquals("a.b", written[GeneralPrefs.externalBrowser])
    }

    @Test
    fun theAppClaimsOnlyXHosts() {
        assertTrue(LinkSteps.claimedByApp("https://www.x.com/home"))
        assertTrue(LinkSteps.claimedByApp("https://mobile.twitter.com/a"))
        assertFalse(LinkSteps.claimedByApp("https://example.com"))
        assertFalse(LinkSteps.claimedByApp("not a link"))
        assertNull(LinkSteps.hostOf("mailto:someone@example.com"))
    }

    @Test
    fun theBarShowsCountsUnlessZenOrCalmHidesThem() {
        val author = XUser("1", "maya", "Maya")
        val context = LinkFrom.x(XPost("9", author, null, "t", replyCount = 1, likeCount = 2500), null, null).post
        val shown = LinkPostBar.counts(context, hidden = false) { it.toString() }
        assertEquals(listOf(BarCount.Kind.LIKES, BarCount.Kind.REPLIES, BarCount.Kind.REPOSTS), shown.map { it.kind })
        assertEquals(listOf("2500", "1", "0"), shown.map { it.shown })
        assertTrue(LinkPostBar.counts(context, hidden = true) { it.toString() }.all { it.shown.isEmpty() })
        val unreported = context.copy(likes = null)
        assertEquals("", LinkPostBar.counts(unreported, hidden = false) { it.toString() }.first().shown)
        assertTrue(LinkPostBar.countsHidden(Settings().with(PostsPrefs.calmMode, true)))
        assertFalse(LinkPostBar.countsHidden(Settings()))
        assertEquals("M", LinkPostBar.monogram(context))
        assertEquals("X", LinkPostBar.monogram(context.copy(author = " ")))
    }

    @Test
    fun theShareHostComesFromSettings() {
        val post = XPost("5", XUser("1", "maya", "Maya"), null, "t")
        val settings = Settings().with(GeneralPrefs.shareBaseUrl, "https://fxtwitter.com/")
        assertEquals("https://fxtwitter.com/maya/status/5", LinkFrom.x(post, settings, null).post.postUrl)
        assertIs<LinkFrom>(LinkFrom.x(post, null, null))
    }
}
