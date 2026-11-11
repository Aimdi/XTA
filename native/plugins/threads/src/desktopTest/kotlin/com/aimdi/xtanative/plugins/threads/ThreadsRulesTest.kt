package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsImage
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsRetry
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsVideoRendition
import com.aimdi.xtanative.networks.threads.ThreadsXyException
import com.aimdi.xtanative.store.settings.MediaQuality
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.PluginSearchHistory
import java.io.IOException
import java.time.Instant
import java.time.ZoneOffset
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
    fun aSearchOrTopicLinkOpensThePluginsSearchAndThreadsOwnPagesOpenNothing() {
        val topic = ThreadsRoutes.parse(ThreadsRoutes.forUrl("https://www.threads.com/search?q=Keramik&serp_type=tags&tag_id=1234")!!)
        assertEquals(ThreadsRoute.Search(ThreadsSearchTerms("Keramik", tag = true)), topic)
        val words = ThreadsRoutes.parse(ThreadsRoutes.forUrl("https://www.threads.net/search?q=glaze+recipes&filter=recent")!!)
        assertEquals(ThreadsRoute.Search(ThreadsSearchTerms("glaze recipes", tag = false)), words)
        assertNull(ThreadsRoutes.forUrl("https://www.threads.com/search"), "a search page with nothing asked")
        assertNull(ThreadsRoutes.forUrl("https://www.threads.com/login?next=%2F%40zuck"))
        assertNull(ThreadsRoutes.forUrl("https://www.threads.com/settings"))
        assertEquals(ThreadsRoute.Profile("login"), ThreadsRoutes.parse(ThreadsRoutes.forUrl("https://www.threads.com/@login")!!), "an account by that name, with its @")
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
    fun theSearchHistoryPutsTheNewestFirstOnceKeepsTwentyAndForgetsOneOnAsking() {
        val history = (1..20).map { "q$it" }
        assertEquals(listOf("q5") + history.filter { it != "q5" }, PluginSearchHistory.remembered(history, " q5 "))
        assertEquals(20, PluginSearchHistory.remembered(history, "new").size)
        assertEquals(history, PluginSearchHistory.remembered(history, "  "))
        assertEquals(history - "q7", PluginSearchHistory.forgotten(history, "q7"))
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
        assertEquals(post.url, large[1].webUrl, "a desktop without libmpv opens the post on the web")
        assertEquals(listOf("1080p", "720p", "480p"), ThreadsMediaItems.qualities(post)[1].map { it.label })
        assertEquals(emptyList(), ThreadsMediaItems.qualities(post)[0])

        val old = video.copy(renditions = listOf(ThreadsVideoRendition("https://cdn/v.mp4")))
        assertEquals("https://cdn/v.mp4", ThreadsMediaItems.items(post.copy(media = listOf(old)), MediaQuality.MEDIUM).single().videoUrl, "a post kept before renditions plays its one file")
    }

    @Test
    fun aPictureOpenedInAConversationIsViewedAmongAllItsPictures() {
        fun shot(n: Int) = ThreadsMedia("https://cdn/$n.jpg")
        fun post(id: String, vararg media: ThreadsMedia) = ThreadsPost(id = id, handle = "zuck", authorName = "Mark", text = id, media = media.toList())
        val shown = listOf(post("a", shot(1), shot(2)), post("b"), post("c", shot(3)), post("d", shot(4), shot(5)))

        assertEquals(0, ThreadsMediaItems.galleryStart(shown, shown[0], 0))
        assertEquals(1, ThreadsMediaItems.galleryStart(shown, shown[0], 1))
        assertEquals(2, ThreadsMediaItems.galleryStart(shown, shown[2], 0))
        assertEquals(4, ThreadsMediaItems.galleryStart(shown, shown[3], 1))
        assertNull(ThreadsMediaItems.galleryStart(shown, post("quote", shot(9)), 0), "a quote is not one of the conversation's posts")
        assertNull(ThreadsMediaItems.galleryStart(listOf(shown[0], shown[1]), shown[0], 1), "nothing more to swipe to: the post's own viewer")
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
    fun aGifLoopsAsTheAppsGifsDoAndIsKeptAsOne() {
        val gif = ThreadsMedia(
            "https://i.giphy.example/full.gif", 4.0 / 3, renditions = listOf(ThreadsVideoRendition("https://i.giphy.example/full.mp4", 480, 360)),
            images = listOf(ThreadsImage("https://i.giphy.example/full.gif", 480, 360)), isGif = true,
        )
        val post = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "", media = listOf(gif), url = "https://www.threads.com/@zuck/post/G1")
        val item = ThreadsMediaItems.items(post, MediaQuality.LARGE).single()
        assertEquals(MediaItem.Kind.GIF, item.kind)
        assertEquals("https://i.giphy.example/full.mp4", item.videoUrl)
        assertTrue(ThreadsMediaItems.qualities(post).single().isEmpty(), "no quality to choose")
        assertEquals(post, ThreadsPostJson.post(J.parse(ThreadsPostJson.json(post).toString())))
    }

    @Test
    fun failuresSayWhatXtaSays() {
        assertEquals(Strings.not_configured, ThreadsFailures.feed(ThreadsException(ThreadsFailure.NOT_CONFIGURED, "")))
        assertEquals(Strings.error_no_feed, ThreadsFailures.feed(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "")))
        assertEquals(Strings.error_no_route, ThreadsFailures.settings(ThreadsException(ThreadsFailure.NO_SUCH_FEED, "")))
        assertEquals(Strings.error_throttled, ThreadsFailures.feed(ThreadsException(ThreadsFailure.THROTTLED, "", 429)))
        assertEquals(Strings.error_session_suspended, ThreadsFailures.feed(ThreadsException(ThreadsFailure.SESSION_SUSPENDED, "")))
        assertEquals(Strings.error_unreachable, ThreadsFailures.feed(IOException("offline")))
        assertEquals("Profile is gone", ThreadsFailures.profile(ThreadsXyException(ThreadsXyException.Kind.NOT_FOUND, serverMessage = " Profile is gone "), "en"))
        assertEquals(Strings.api_unauthorized.get("en"), ThreadsFailures.profile(ThreadsXyException(ThreadsXyException.Kind.UNAUTHORIZED), "en"))
    }

    @Test
    fun aPageThatFailedSaysWhatIsMissingWhenMetasWaitEndsAndThatItWantsASignIn() {
        val missing = ThreadsException(ThreadsFailure.NO_SUCH_FEED, "404", 404)
        assertEquals("Could not read that Threads account", ThreadsFailures.profile(missing, "en"), "not XTA's words for an RSSHub instance")
        assertEquals("Could not read that Threads post", ThreadsFailures.post(missing, "en"))
        assertEquals("Dieser Threads-Beitrag konnte nicht gelesen werden", ThreadsFailures.post(missing, "de"))
        assertEquals(Strings.error_unreachable.get("en"), ThreadsFailures.search(missing, "en"))

        val until = Instant.parse("2026-11-11T14:05:00Z")
        val throttled = ThreadsException(ThreadsFailure.THROTTLED, "429", 429, retryAt = until)
        val time = ThreadsFailures.time(until, "en", ZoneOffset.UTC)
        assertTrue(time.startsWith("2:05"), time)
        assertEquals("Threads is rate limiting — try again after $time", ThreadsFailures.post(throttled, "en", ZoneOffset.UTC))
        assertEquals("14:05", ThreadsFailures.time(until, "de", ZoneOffset.UTC))
        assertEquals(Strings.error_throttled.get("en"), ThreadsFailures.post(ThreadsException(ThreadsFailure.THROTTLED, "429", 429), "en"))
        assertEquals("Threads is rate limiting — try again after $time", ThreadsFailures.feedText(throttled, "en", ZoneOffset.UTC), "the tab says when too")
        assertEquals(Strings.error_no_feed.get("en"), ThreadsFailures.feedText(missing, "en"))

        val wall = ThreadsException(ThreadsFailure.SIGN_IN_REQUIRED, "login")
        assertEquals("Threads shows this page only to signed-in readers right now", ThreadsFailures.profile(wall, "en"))
        assertEquals(Strings.error_sign_in, ThreadsFailures.feed(wall))
        assertEquals(ThreadsRetry.MANUAL, wall.retry, "it waits for the reader")
    }

    @Test
    fun everyGlyphDraws() {
        ThreadsIcons.all.forEach { assertTrue(it.root.iterator().hasNext(), it.name) }
    }

    @Test
    fun theViewerShowsTheLargestPictureAndATileTheAppDrawsOneAPhoneWide() {
        val sizes = listOf(ThreadsImage("https://cdn/1440.jpg", 1440, 1800), ThreadsImage("https://cdn/1080.jpg", 1080, 1350), ThreadsImage("https://cdn/320.jpg", 320, 400))
        val post = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "", media = listOf(ThreadsMedia("https://cdn/1440.jpg", 0.8, images = sizes)))
        val item = ThreadsMediaItems.items(post, MediaQuality.LARGE).single()
        assertEquals("https://cdn/1440.jpg", item.imageUrl, "the viewer's picture, and what a download saves")
        assertNull(item.downloadUrl)
        assertEquals("https://cdn/1080.jpg", item.tileUrl)

        val plain = ThreadsMediaItems.items(post.copy(media = listOf(ThreadsMedia("https://cdn/only.jpg"))), MediaQuality.LARGE).single()
        assertEquals("https://cdn/only.jpg", plain.tileUrl, "a picture with one address shows it")
    }

    @Test
    fun aFeedTileLoadsThePictureThatFillsItAndPlaysTheQualityTheSettingPicks() {
        val picture = ThreadsMedia(
            "https://cdn/1440.jpg",
            images = listOf(ThreadsImage("https://cdn/1440.jpg", 1440, 1800), ThreadsImage("https://cdn/640.jpg", 640, 800), ThreadsImage("https://cdn/320.jpg", 320, 400)),
            alt = " A harbour ",
        )
        val video = ThreadsMedia(
            "https://cdn/poster.jpg",
            renditions = listOf(
                ThreadsVideoRendition("https://cdn/1080.mp4", 1080, 1920),
                ThreadsVideoRendition("https://cdn/720.mp4", 720, 1280),
                ThreadsVideoRendition("https://cdn/480.mp4", 480, 854),
            ),
        )
        val post = ThreadsPost(id = "1", handle = "zuck", authorName = "Mark", text = "", media = listOf(picture, video), url = "https://www.threads.com/@zuck/post/A")

        val still = ThreadsMediaItems.tile(post, picture, MediaQuality.MEDIUM, 600, 700)
        assertEquals("https://cdn/640.jpg", still.tileUrl, "the smallest size that fills the tile")
        assertEquals("https://cdn/1440.jpg", still.imageUrl, "the viewer and a download keep the largest")
        assertEquals("A harbour", still.description)

        val moving = ThreadsMediaItems.tile(post, video, MediaQuality.MEDIUM, 600, 700)
        assertEquals(MediaItem.Kind.VIDEO, moving.kind)
        assertEquals("https://cdn/720.mp4", moving.inlineVideoUrl, "in place, the file the setting picks, not the smallest")
        assertEquals("https://cdn/480.mp4", ThreadsMediaItems.tile(post, video, MediaQuality.THUMB, 600, 700).inlineVideoUrl)
    }

    @Test
    fun theDotsMarkTheTileInViewAndTheLastAtTheRowsEnd() {
        assertEquals(0, ThreadsStrip.shown(first = 0, offset = 0, firstWidth = 300f, atEnd = false, count = 4))
        assertEquals(0, ThreadsStrip.shown(first = 0, offset = 140, firstWidth = 300f, atEnd = false, count = 4))
        assertEquals(1, ThreadsStrip.shown(first = 0, offset = 160, firstWidth = 300f, atEnd = false, count = 4), "more than half of the first has left")
        assertEquals(3, ThreadsStrip.shown(first = 2, offset = 40, firstWidth = 300f, atEnd = true, count = 4), "the row cannot scroll further")
        assertEquals(0, ThreadsStrip.shown(first = 0, offset = 0, firstWidth = 300f, atEnd = true, count = 2), "a row that fits whole starts on its first")
        assertEquals(0, ThreadsStrip.shown(first = 0, offset = 0, firstWidth = 0f, atEnd = false, count = 0))
    }

    @Test
    fun aProfilesLinkReadsWithoutItsSchemeAndStillOpensWhole() {
        assertEquals("example.com/about", ThreadsProfiles.linkLabel("https://www.example.com/about/"))
        assertEquals("example.com", ThreadsProfiles.linkLabel(" HTTP://example.com "))
        assertEquals("linktr.ee/zuck", ThreadsProfiles.linkLabel("linktr.ee/zuck"))
        assertEquals("https://", ThreadsProfiles.linkLabel("https://"), "nothing left to show: the address as it is")
    }
}
