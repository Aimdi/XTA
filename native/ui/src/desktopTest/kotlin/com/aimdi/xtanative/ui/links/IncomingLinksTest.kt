package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.services.links.XLink
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

/** Links other apps open here, routed as main.dart's handleInitialLink routes them. */
class IncomingLinksTest {
    private fun step(url: String, network: (String) -> Boolean = { false }, resolve: suspend (String) -> String? = { null }) =
        runBlocking { IncomingLinks.step(url, network) { resolve(it) } }

    private class Opened(hasScreens: Boolean = true, liveScreen: Boolean = true) {
        val seen = ArrayList<String>()
        val screens = LinkScreens(
            post = { seen += "post $it" },
            profile = { seen += "profile $it" },
            search = { seen += "search $it" },
            browser = { url, _ -> seen += "browser $url" },
            live = { link -> liveScreen.also { if (it) seen += "live ${link.url}" } },
            community = { id, about -> hasScreens.also { if (it) seen += "community $id $about" } },
            list = { id -> hasScreens.also { if (it) seen += "list $id" } },
        )

        fun open(step: IncomingStep) = IncomingLinks.open(step, screens, outside = { seen += "outside $it" }, postLink = { seen += "post link $it" }, unable = { seen += "unable $it" })
    }

    @Test
    fun aPluginOrANetworkHasTheFirstTry() {
        val asked = ArrayList<String>()
        val url = "https://x.com/i/broadcasts/1abc"
        assertEquals(IncomingStep.Opened, step(url, network = { asked += it; true }))
        assertEquals(listOf(url), asked)
    }

    @Test
    fun postsProfilesCommunitiesAndListsOpenTheirScreens() {
        val opened = Opened()
        listOf(
            "https://x.com/maya/status/12?s=20",
            "http://twitter.com/maya",
            "https://fxtwitter.com/jo/status/34",
            "https://x.com/i/communities/56/about",
            "https://x.com/i/lists/78",
        ).forEach { opened.open(step(it)) }
        assertEquals(listOf("post 12", "profile maya", "post 34", "community 56 true", "list 78"), opened.seen)
    }

    @Test
    fun broadcastsAndSpacesGoToTheLivePlayer() {
        val broadcast = step("https://twitter.com/i/broadcasts/1abc")
        assertEquals(IncomingStep.Screen(XLink.Live("https://x.com/i/broadcasts/1abc", isSpace = false), "https://twitter.com/i/broadcasts/1abc"), broadcast)
        val opened = Opened()
        opened.open(broadcast)
        opened.open(step("https://x.com/i/spaces/1room"))
        assertEquals(listOf("live https://x.com/i/broadcasts/1abc", "live https://x.com/i/spaces/1room"), opened.seen)
    }

    @Test
    fun withoutItsScreenALinkLeavesForTheWeb() {
        val opened = Opened(hasScreens = false, liveScreen = false)
        opened.open(step("https://x.com/i/lists/78"))
        opened.open(step("https://x.com/i/spaces/1room?s=1"))
        assertEquals(listOf("outside https://x.com/i/lists/78", "outside https://x.com/i/spaces/1room"), opened.seen)
    }

    @Test
    fun aShortLinkIsFollowedFirst() {
        val followed = ArrayList<String>()
        val opened = Opened()
        opened.open(step("https://t.co/abc", resolve = { followed += it; "https://x.com/maya/status/9" }))
        assertEquals(listOf("https://t.co/abc"), followed)
        assertEquals(listOf("post 9"), opened.seen)
    }

    @Test
    fun aShortLinkThatLeadsNowhereCannotBeOpened() {
        assertEquals(IncomingStep.Unable("https://t.co/abc"), step("https://t.co/abc") { null })
        assertEquals(IncomingStep.Unable("https://t.co/abc"), step("https://t.co/abc") { error("offline") })
    }

    @Test
    fun aGrokShareOpensAsAPostsLink() {
        val opened = Opened()
        opened.open(step("https://x.com/i/grok/share/abc"))
        assertEquals(listOf("post link https://x.com/i/grok/share/abc"), opened.seen)
    }

    @Test
    fun aLinkNothingReadsAsksTheReader() {
        val opened = Opened()
        opened.open(step("https://x.com/i/bookmarks/folder/1"))
        assertEquals(listOf("unable https://x.com/i/bookmarks/folder/1"), opened.seen)
    }

    @Test
    fun aPixivPageNoNetworkOpenedGoesToTheWeb() {
        assertEquals(IncomingStep.Outside("https://www.pixiv.net/en/artworks/1"), step("https://www.pixiv.net/en/artworks/1"))
    }
}
