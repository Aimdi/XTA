package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsVideoRendition
import com.aimdi.xtanative.networks.threads.ThreadsXyException
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.ui.plugins.MediaItem
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ThreadsRulesTest {
    @Test
    fun linksToThreadsOpenThePluginsOwnScreens() {
        val post = ThreadsRoutes.parse(ThreadsRoutes.forUrl("https://www.threads.net/@Zuck/post/ABC123?xmt=1")!!)
        assertEquals(ThreadsRoute.Post("https://www.threads.net/@Zuck/post/ABC123?xmt=1", "zuck"), post)
        assertEquals(ThreadsRoute.Profile("zuck"), ThreadsRoutes.parse(ThreadsRoutes.forUrl("threads.com/@zuck")!!))
        assertNull(ThreadsRoutes.forUrl("https://example.com/@zuck"))
    }

    @Test
    fun aRouteCarriesWhatItsScreenNeeds() {
        val card = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "hi", url = "https://www.threads.com/@zuck/post/A&B")
        assertEquals(ThreadsRoute.Post(card.url!!, "zuck", "7"), ThreadsRoutes.parse(ThreadsRoutes.post(card, "7")!!))
        assertNull(ThreadsRoutes.post(card.copy(url = null), null), "a post without a link has no page to read")
        val terms = ThreadsSearchTerms("café & co", tag = true, profile = null)
        assertEquals(ThreadsRoute.Search(terms), ThreadsRoutes.parse(ThreadsRoutes.search(terms)))
        assertEquals(ThreadsRoute.Media("3", 2), ThreadsRoutes.parse(ThreadsRoutes.media("3", 2)))
        assertEquals(ThreadsRoute.Saved, ThreadsRoutes.parse(ThreadsRoutes.SAVED))
        assertNull(ThreadsRoutes.parse("elsewhere?x=1"))
    }

    @Test
    fun theSearchHistoryPutsTheNewestFirstOnceAndKeepsTwenty() {
        val history = (1..20).map { "q$it" }
        assertEquals(listOf("q5") + history.filter { it != "q5" }, ThreadsSearchHistory.remembered(history, " q5 "))
        assertEquals(20, ThreadsSearchHistory.remembered(history, "new").size)
        assertEquals(history, ThreadsSearchHistory.remembered(history, "  "))
    }

    @Test
    fun aProfileShowsTheCountsMetaToldAndNoLinkToItself() {
        val guest = ThreadsProfile(pk = "", id = "", username = "zuck", fullName = "Mark", followerCount = 5_700_000, mediaCount = 380)
        assertEquals(listOf(5_700_000L to ThreadsCount.FOLLOWERS, 380L to ThreadsCount.POSTS), ThreadsProfiles.counts(guest))
        assertEquals(emptyList(), ThreadsProfiles.counts(guest.copy(followerCount = null, mediaCount = null)))
        assertNull(ThreadsProfiles.outsideLink(guest.copy(externalUrl = "https://www.threads.net/@Zuck")))
        assertEquals("https://about.meta.com", ThreadsProfiles.outsideLink(guest.copy(externalUrl = " https://about.meta.com ")))
    }

    @Test
    fun aVideoPlaysWhatTheQualitySettingPicksAndOffersEveryRenditionByItsLines() {
        val video = ThreadsMedia(
            "https://cdn/poster.jpg",
            aspectRatio = 0.5625,
            renditions = listOf(
                ThreadsVideoRendition("https://cdn/1080.mp4", 1080, 1920),
                ThreadsVideoRendition("https://cdn/720.mp4", 720, 1280),
                ThreadsVideoRendition("https://cdn/480.mp4", 480, 854),
            ),
        )
        val post = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "", media = listOf(ThreadsMedia("https://cdn/a.jpg"), video), url = "https://www.threads.com/@zuck/post/A")

        val large = ThreadsMediaItems.items(post, MediaQuality.LARGE)
        assertEquals(MediaItem.Kind.PHOTO, large[0].kind)
        assertEquals("https://cdn/1080.mp4", large[1].videoUrl)
        assertEquals("https://cdn/720.mp4", ThreadsMediaItems.items(post, MediaQuality.MEDIUM)[1].videoUrl)
        assertEquals(listOf(post.url, post.url), large.map { it.sharedUrl }, "the viewer's Share gives the post")
        assertEquals("https://cdn/480.mp4", ThreadsMediaItems.items(post, MediaQuality.THUMB)[1].videoUrl)
        assertEquals(post.url, large[1].webUrl, "the desktop opens the post on the web")
        assertEquals(listOf("1080p", "720p", "480p"), ThreadsMediaItems.qualities(post)[1].map { it.label })
        assertEquals(emptyList(), ThreadsMediaItems.qualities(post)[0])

        val old = video.copy(renditions = listOf(ThreadsVideoRendition("https://cdn/v.mp4")))
        assertEquals("https://cdn/v.mp4", ThreadsMediaItems.items(post.copy(media = listOf(old)), MediaQuality.MEDIUM).single().videoUrl, "a post kept before renditions plays its one file")
    }

    @Test
    fun severalPicturesShareOneRowTheNextOnesEdgeInView() {
        val one = ThreadsStrip.layout(400f, listOf(0.3))
        assertEquals(400f / 0.45f, one.height, 0.01f)
        val row = ThreadsStrip.layout(400f, listOf(0.5, 1.0, 3.0, null))
        assertEquals(400f * 0.62f, row.height, 0.01f)
        assertEquals(listOf(row.height * 0.8f, row.height, 400f * 0.86f, 400f * 0.86f), row.widths)
        assertTrue(row.widths.all { it < 400f })
        assertEquals(0f, ThreadsStrip.layout(400f, emptyList()).height)
    }

    @Test
    fun failuresSayWhatXtaSays() {
        assertEquals(Strings.not_configured, ThreadsFailures.feed(ThreadsException(ThreadsFailure.NOT_CONFIGURED, "")))
        assertEquals(Strings.error_no_feed, ThreadsFailures.feed(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "")))
        assertEquals(Strings.error_no_route, ThreadsFailures.settings(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "")))
        assertEquals(Strings.error_throttled, ThreadsFailures.feed(ThreadsException(ThreadsFailure.THROTTLED, "", 429)))
        assertEquals(Strings.error_session_suspended, ThreadsFailures.feed(ThreadsException(ThreadsFailure.SESSION_SUSPENDED, "")))
        assertEquals(Strings.error_unreachable, ThreadsFailures.feed(IOException("offline")))
        assertEquals("Profile is gone", ThreadsFailures.page(ThreadsXyException(ThreadsXyException.Kind.NOT_FOUND, serverMessage = " Profile is gone "), "en"))
        assertEquals(Strings.api_unauthorized.get("en"), ThreadsFailures.page(ThreadsXyException(ThreadsXyException.Kind.UNAUTHORIZED), "en"))
    }

    @Test
    fun everyGlyphDraws() {
        ThreadsIcons.all.forEach { assertTrue(it.root.iterator().hasNext(), it.name) }
    }
}
