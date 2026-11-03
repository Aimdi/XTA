package com.aimdi.xtanative.services.links

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LinkOpeningTest {
    private val article = "https://www.washingtonpost.com/politics/2026/10/08/budget-vote/"

    private fun router(on: Set<String>, substack: (String) -> Boolean = { false }, desktop: Boolean = false) =
        LinkRouter(NetworkLinks(NetworkLinks.mastodonHosts(NetworkLinks.MASTODON_DEFAULT_INSTANCES)), { it in on }, substack, desktop)

    @Test
    fun aNetworksLinkOpensOnlyWhileThatNetworkIsOn() {
        val bluesky = "https://bsky.app/profile/a/post/b"
        assertEquals(NativeRoute.Network(NetworkLink.BlueskyPost("a", "b")), router(setOf("bluesky")).route(bluesky))
        assertNull(router(emptySet()).route(bluesky))
    }

    @Test
    fun xProfilesPostsAndCommunitiesOpenOnTheXScreens() {
        assertEquals(NativeRoute.X(XLink.Profile("someone")), router(emptySet()).route("https://x.com/someone"))
        assertEquals(NativeRoute.X(XLink.Post("someone", "1")), router(emptySet()).route("https://mobile.twitter.com/someone/status/1"))
        assertEquals(NativeRoute.X(XLink.Community("9")), router(emptySet()).route("https://x.com/i/communities/9"))
    }

    @Test
    fun mobileXComLinksOpenOnTheXScreensTooWhichXtaSentToTheBrowser() {
        assertEquals(NativeRoute.X(XLink.Profile("someone")), router(emptySet()).route("https://mobile.x.com/someone"))
    }

    @Test
    fun liveLinksOpenTheLivePlayerFromAnyHostTheyComeOn() {
        assertEquals(NativeRoute.Live(XLink.Live("https://x.com/i/spaces/1room", isSpace = true)), router(emptySet()).route("https://x.com/i/spaces/1room"))
        assertEquals(NativeRoute.Live(XLink.Live("https://x.com/i/broadcasts/1v", isSpace = false)), router(emptySet()).route("https://pscp.tv/w/1v"))
    }

    @Test
    fun listsGrokSharesAndOrdinaryPagesAreLeftForTheWeb() {
        listOf("https://x.com/i/lists/1", "https://x.com/i/grok/share/abc", article, "https://t.co/short").forEach {
            assertNull(router(setOf("bluesky", "reddit")).route(it), it)
        }
    }

    @Test
    fun aKnownSubstackPostOpensBeforeAnyOtherNetworkButNotOnTheDesktop() {
        val post = "https://news.example/p/hello"
        val reads: (String) -> Boolean = { it == post }
        assertEquals(NativeRoute.Network(NetworkLink.Claimed("substack", post)), router(setOf("substack"), reads).route(post))
        assertNull(router(emptySet(), reads).route(post))
        assertNull(router(setOf("substack"), reads, desktop = true).route(post))
    }

    @Test
    fun theDesktopLeavesTikTokVideosToTheBrowser() {
        val video = "https://www.tiktok.com/@nasa/video/1"
        assertEquals(NativeRoute.Network(NetworkLink.TikTokVideo("1", "nasa")), router(setOf("tiktok")).route(video))
        assertNull(router(setOf("tiktok"), desktop = true).route(video))
        assertEquals(NativeRoute.Network(NetworkLink.TikTokProfile("nasa")), router(setOf("tiktok"), desktop = true).route("https://www.tiktok.com/@nasa"))
    }

    @Test
    fun aPostsLinkOpensInTheAppWhenTheReaderKeepsLinksThere() {
        assertEquals(LinkLaunch.InAppBrowser(article), LinkOpening.postLink(article, LinkSettings(inApp = true)))
        // Unset is in the app: XTA turns the switch on at first launch.
        assertEquals(LinkLaunch.InAppBrowser(article), LinkOpening.postLink(article, LinkSettings()))
        assertEquals(LinkLaunch.External(article, ""), LinkOpening.postLink(article, LinkSettings(inApp = false)))
        assertEquals(LinkLaunch.Desktop(article), LinkOpening.postLink(article, LinkSettings(inApp = true), desktop = true))
        // Not a web page: it leaves as any other link does, a Custom Tab with the switch on.
        assertEquals(LinkLaunch.CustomTab("mailto:a@b.c"), LinkOpening.postLink("mailto:a@b.c", LinkSettings(inApp = true)))
    }

    @Test
    fun trackingIsStrippedInEveryWayALinkLeaves() {
        val dirty = "https://example.com/a?utm_source=x&keep=1"
        val clean = "https://example.com/a?keep=1"
        assertEquals(clean, LinkOpening.postLink(dirty, LinkSettings(inApp = true)).url)
        assertEquals(clean, LinkOpening.outside(dirty, LinkSettings(inApp = false)).url)
        assertEquals(clean, LinkOpening.outside(dirty, LinkSettings(inApp = true)).url)
        assertEquals(dirty, LinkOpening.outside(dirty, LinkSettings(inApp = true, clean = false)).url)
        assertEquals(clean, LinkOpening.leaveBrowserScreen(dirty, LinkSettings(browser = "com.android.chrome")).url)
    }

    @Test
    fun linksXtaClaimsGoToABrowserByNameSoTheyDoNotBounceBack() {
        assertEquals(
            LinkLaunch.RealBrowser("https://x.com/i/spaces/1room", "", customTabFirst = true),
            LinkOpening.outside("https://x.com/i/spaces/1room", LinkSettings(inApp = true)),
        )
        assertEquals(
            LinkLaunch.RealBrowser("https://x.com/i/broadcasts/1abc", "org.mozilla.firefox", customTabFirst = false),
            LinkOpening.outside("https://x.com/i/broadcasts/1abc", LinkSettings(inApp = false, browser = "org.mozilla.firefox")),
        )
        assertEquals(
            LinkLaunch.RealBrowser("https://www.pixiv.net/artworks/1", "", customTabFirst = false),
            LinkOpening.outside("https://www.pixiv.net/artworks/1", LinkSettings(), reopensApp = true),
        )
        assertTrue(LinkOpening.onClaimedHost("https://www.pixiv.net/artworks/1"))
        assertFalse(LinkOpening.onClaimedHost("https://i.pximg.net/a.png"))
    }

    @Test
    fun outsideTheAppACustomTabNeedsTheSwitchOnNotJustUnset() {
        assertEquals(LinkLaunch.External(article, ""), LinkOpening.outside(article, LinkSettings()))
        assertEquals(LinkLaunch.CustomTab(article), LinkOpening.outside(article, LinkSettings(inApp = true)))
    }

    @Test
    fun theBrowserScreenFollowsThePageAndIgnoresItAfterClosing() {
        val session = LinkBrowserSession("https://example.com/a")
        assertTrue(session.state.value.loading)
        session.progressed(150)
        assertEquals(1.0, session.state.value.progress)
        session.started("")
        assertEquals("https://example.com/a", session.state.value.url)
        session.finished("http://example.org/b")
        assertEquals(LinkBrowserState("http://example.org/b", loading = false, progress = 1.0), session.state.value)
        assertFalse(session.state.value.secure)
        assertEquals("example.org", session.state.value.domain)
        session.close()
        session.started("https://late.example")
        assertEquals("http://example.org/b", session.state.value.url)
    }

    @Test
    fun thePageMayFollowWebLinksAndItsOwnFramesButNoOtherScheme() {
        assertEquals(LinkNavigation.ASK_APP, linkNavigation("https://bsky.app/profile/a"))
        listOf("about:blank", "data:text/html,x", "blob:https://a/b").forEach { assertEquals(LinkNavigation.ALLOW, linkNavigation(it), it) }
        listOf("intent://x#Intent;end", "market://details?id=a", "javascript:alert(1)").forEach { assertEquals(LinkNavigation.BLOCK, linkNavigation(it), it) }
    }

    private val browsers = listOf(InstalledBrowser("com.android.chrome", "Chrome"), InstalledBrowser("org.mozilla.firefox", "Firefox"))

    @Test
    fun pickingANamedBrowserTurnsTheInAppViewOff() {
        val picked = BrowserPicker.choose(LinkSettings(inApp = true, browser = "", clean = true), LinkDestination.Browser("org.mozilla.firefox"))
        assertEquals(LinkSettings(inApp = false, browser = "org.mozilla.firefox", clean = true), picked)
        assertEquals(LinkDestination.Browser("org.mozilla.firefox"), BrowserPicker.selected(picked, browsers))
        assertEquals("Firefox", BrowserPicker.label("org.mozilla.firefox", browsers))
    }

    @Test
    fun pickingInAppAgainLeavesTheLastBrowserStored() {
        val picked = BrowserPicker.choose(LinkSettings(inApp = false, browser = "org.mozilla.firefox"), LinkDestination.InApp)
        assertEquals(LinkSettings(inApp = true, browser = "org.mozilla.firefox"), picked)
        assertEquals(LinkDestination.InApp, BrowserPicker.selected(picked, browsers))
    }

    @Test
    fun aBrowserNoLongerInstalledShowsAsTheSystemDefault() {
        assertEquals(LinkDestination.SystemDefault, BrowserPicker.selected(LinkSettings(inApp = false, browser = "gone.browser"), browsers))
        assertEquals(LinkDestination.SystemDefault, BrowserPicker.selected(LinkSettings(), browsers))
        assertEquals("gone.browser", BrowserPicker.label("gone.browser", browsers))
        assertEquals(
            listOf(LinkDestination.InApp, LinkDestination.SystemDefault, LinkDestination.Browser("com.android.chrome"), LinkDestination.Browser("org.mozilla.firefox")),
            BrowserPicker.options(browsers),
        )
    }

    @Test
    fun anXPostsContextCountsRepostsWithQuotesAndSharesUnderTheReadersHost() {
        val post = XPost(
            id = "7", author = XUser("1", "maya", "Maya", avatarUrl = "https://pbs.twimg.com/a.jpg"), createdAt = null, text = "",
            replyCount = 3, repostCount = 4, quoteCount = 1, likeCount = 9,
        )
        assertEquals(
            LinkPostContext("x", "Maya", "https://pbs.twimg.com/a.jpg", replies = 3, reposts = 5, likes = 9, postUrl = "https://x.com/maya/status/7"),
            LinkPostContext.of(post),
        )
        assertEquals("https://fixupx.com/maya/status/7", LinkPostContext.of(post, "https://fixupx.com").postUrl)
        assertEquals("https://x.com/maya/status/7", LinkPostContext.of(post, "").postUrl)
    }
}
