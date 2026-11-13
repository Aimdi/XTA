package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Meta's post and user objects, as XTA's threads_parse_test and threads_direct_test pin them. */
class ThreadsParserTest {
    private val posts = ThreadsParser.graphqlFeed(J.parse(fixture("graphql_profile_threads.json")))

    private fun byCode(code: String) = posts.single { it.url?.endsWith("/post/$code") == true }

    @Test
    fun aGraphqlProfileTabGivesEachThreadsRootAndSkipsAPostWithoutAnAuthor() {
        assertEquals(
            listOf(
                "3400000000000000001", "3400000000000000002", "3400000000000000003", "3400000000000000004",
                "3400000000000000005", "3400000000000000006", "3400000000000000007",
            ),
            posts.map { it.id },
        )
    }

    @Test
    fun aRootCarriesItsAuthorCountsAndPermalink() {
        val root = byCode("DAthread01")
        assertEquals("zuck", root.handle)
        assertEquals("Mark Zuckerberg", root.authorName)
        assertEquals("https://scontent.cdninstagram.com/v/zuck.jpg?oe=6700", root.avatarUrl)
        assertTrue(root.isVerified)
        assertEquals(listOf(1348L, 12L, 4L, 2L), listOf(root.likeCount, root.replyCount, root.repostCount, root.quoteCount))
        assertEquals("https://www.threads.com/@zuck/post/DAthread01", root.url)
        assertEquals("DAthread01", root.shortcode)
        assertEquals(Instant.ofEpochSecond(1761900000), root.publishedAt)
        assertFalse(root.isReply)
        assertTrue(root.media.isEmpty())
    }

    @Test
    fun theAuthorsContinuationIsCountedAndAReplyFromAnyoneElseEndsIt() {
        assertEquals(2, byCode("DAthread01").selfThreadCount)
    }

    @Test
    fun metasFragmentsResolveMentionsUnwrappedLinksAndTags() {
        val root = byCode("DAthread01")
        assertEquals(listOf(Kind.TEXT, Kind.MENTION, Kind.TEXT, Kind.LINK, Kind.TEXT, Kind.TAG), root.fragments.map { it.kind })
        assertEquals(listOf(null, "mosseri", null, "https://example.com/article", null, "Books"), root.fragments.map { it.target })
        assertEquals(root.text, root.fragments.joinToString("") { it.text })
        assertEquals("Books", root.topicTag, "the topic, without its #")
    }

    @Test
    fun aCarouselKeepsEachPicturesLargestFileItsShapeAndDescriptionAndItsVideos() {
        val carousel = byCode("DAcarousel")
        assertEquals(
            listOf("https://scontent.cdninstagram.com/a-1080.jpg", "https://scontent.cdninstagram.com/b.jpg", "https://scontent.cdninstagram.com/c-poster.jpg"),
            carousel.media.map { it.url },
        )
        assertEquals(listOf(1080.0 / 1350, 1440.0 / 1080, 720.0 / 1280), carousel.media.map { it.aspectRatio })
        assertEquals(listOf("A kiln, open", null, null), carousel.media.map { it.alt })
        assertEquals(listOf(false, false, true), carousel.media.map { it.isVideo })
        assertEquals(listOf("https://scontent.cdninstagram.com/c-720.mp4" to 720, "https://scontent.cdninstagram.com/c-360.mp4" to 360),
            carousel.media[2].renditions.map { it.url to it.lines })
        assertEquals("https://scontent.cdninstagram.com/c-720.mp4", carousel.media[2].videoUrl)
        assertTrue(carousel.hasVideo)
        assertEquals(3L, carousel.replyCount)
        assertNull(carousel.likeCount, "a count Meta did not send is not a zero")
    }

    @Test
    fun aVideoKeepsEveryFileLargestFirstOneFileAndOneQualityEach() {
        val video = byCode("DAvideo001")
        assertEquals("", video.text, "a video alone is still a post")
        val poster = video.media.single()
        assertEquals("https://scontent.cdninstagram.com/v-poster.jpg", poster.url)
        assertEquals(1080.0 / 1920, poster.aspectRatio)
        assertEquals(
            listOf(
                "https://scontent.cdninstagram.com/v-1920.mp4" to 1080,
                "https://scontent.cdninstagram.com/v-1280.mp4" to 720,
                "https://scontent.cdninstagram.com/v-640.mp4" to 360,
            ),
            poster.renditions.map { it.url to it.lines },
        )
        assertEquals("https://scontent.cdninstagram.com/v-1920.mp4", poster.videoUrl)
    }

    @Test
    fun aGifFromGiphyIsItsLargestRenditionLoopingFromItsMp4InPlaceOfTheStill() {
        val post = j(mapOf(
            "image_versions2" to mapOf("candidates" to listOf(mapOf("url" to "https://scontent.cdninstagram.com/gif-still.jpg", "width" to 200, "height" to 200))),
            "giphy_media_info" to mapOf("first_party_cdn_proxied_images" to mapOf(
                "fixed_height" to mapOf("url" to "https://i.giphy.example/200.gif", "mp4" to "https://i.giphy.example/200.mp4", "width" to "267", "height" to "200"),
                "original" to mapOf("url" to "https://i.giphy.example/full.gif", "mp4" to "https://i.giphy.example/full.mp4", "webp" to "https://i.giphy.example/full.webp", "width" to "480", "height" to "360"),
            )),
        ))
        val gif = ThreadsParser.media(post).single()
        assertTrue(gif.isGif)
        assertEquals("https://i.giphy.example/full.gif", gif.url)
        assertEquals("https://i.giphy.example/full.mp4", gif.videoUrl)
        assertEquals(480.0 / 360, gif.aspectRatio)

        val withoutClip = ThreadsParser.gif(j(mapOf("giphy_media_info" to mapOf("first_party_cdn_proxied_images" to mapOf(
            "fixed_width" to mapOf("webp" to "https://i.giphy.example/w.webp", "width" to 200, "height" to 100),
        )))))!!
        assertEquals("https://i.giphy.example/w.webp", withoutClip.url)
        assertFalse(withoutClip.isVideo, "a GIF without an MP4 is its picture")
        assertNull(ThreadsParser.gif(j(mapOf("giphy_media_info" to null))))
        assertFalse(ThreadsParser.media(j(mapOf("image_versions2" to mapOf("candidates" to listOf(mapOf("url" to "https://cdn/p.jpg")))))).single().isGif)
    }

    @Test
    fun renditionsWithoutSizesFollowTheSizedOnesAndAQualityIsNamedByItsShorterSide() {
        val renditions = ThreadsParser.videoRenditions(j(mapOf("video_versions" to listOf(
            mapOf("url" to "https://cdn/640.mp4", "width" to 360, "height" to 640, "type" to 101),
            mapOf("url" to "https://cdn/1280.mp4", "width" to 720, "height" to 1280, "type" to 102),
            mapOf("url" to "https://cdn/1280.mp4", "width" to 720, "height" to 1280, "type" to 103),
            mapOf("url" to "https://cdn/1280-lighter.mp4", "width" to 720, "height" to 1280),
            mapOf("url" to "https://cdn/unsized.mp4"),
            mapOf("url" to ""),
        ))))
        assertEquals(listOf("https://cdn/1280.mp4" to 720, "https://cdn/640.mp4" to 360, "https://cdn/unsized.mp4" to null),
            renditions.map { it.url to it.lines })
        assertEquals(1080, ThreadsVideoRendition("https://cdn/l.mp4", width = 1920, height = 1080).lines)
        assertEquals(480, ThreadsVideoRendition("https://cdn/h.mp4", height = 480).lines)
        assertNull(ThreadsVideoRendition("https://cdn/w.mp4", width = 480).lines)
    }

    @Test
    fun aRepostIsReadAsTheOriginalRepostedAtTheRepostsTime() {
        val repost = posts.single { it.isRepost }
        assertEquals("3400000000000000003", repost.id, "the repost's own id")
        assertEquals("meta", repost.handle)
        assertEquals("Meta", repost.authorName)
        assertEquals("Connect is on", repost.text)
        assertEquals("zuck", repost.repostedByHandle)
        assertEquals("Mark Zuckerberg", repost.reposterDisplayName)
        assertEquals("zuck", repost.sourceHandle)
        assertEquals(listOf(42L, 3L, 7L, 1L), listOf(repost.likeCount, repost.replyCount, repost.repostCount, repost.quoteCount))
        assertEquals("https://www.threads.com/@meta/post/DBmeta0001", repost.url)
        assertEquals(Instant.ofEpochSecond(1761880000), repost.publishedAt, "when the profile reposted, not when Meta posted")
    }

    @Test
    fun aRepostWithoutAReposterIsLeftOut() {
        val repost = postJson("1", text = "", info = mapOf("share_info" to mapOf("reposted_post" to postJson("2", user = "meta"))))
        assertNull(ThreadsParser.post(j(repost + ("user" to mapOf("username" to " ")))))
    }

    @Test
    fun aQuoteIsReadOneLevelDeepAndANumericIdIsKept() {
        val quote = byCode("DAquote001")
        assertEquals("3400000000000000004", quote.id, "the app API sends ids as numbers")
        assertEquals(5L, quote.quoteCount)
        assertEquals("mosseri", quote.quoted?.handle)
        assertEquals("Original thought", quote.quoted?.text)
        assertNull(quote.quoted?.quoted)
    }

    @Test
    fun aQuoteWithNoWordsOfItsOwnIsStillAPostUnderEitherName() {
        val bare = ThreadsParser.post(j(postJson("6", text = "", info = mapOf("share_info" to mapOf("quoted_post" to postJson("7", user = "meta"))))))
        assertEquals("meta", bare?.quoted?.handle)
        val attached = ThreadsParser.post(j(postJson("8", info = mapOf("share_info" to mapOf("quoted_attachment_post" to postJson("9", user = "ola"))))))
        assertEquals("ola", attached?.quoted?.handle)
    }

    @Test
    fun aLinkPreviewLeadsPastThreadsRedirect() {
        val card = assertNotNull(byCode("DAlink0001").linkCard)
        assertEquals("https://example.org/story", card.url)
        assertEquals("A story", card.title)
        assertEquals("About something", card.description)
        assertEquals("https://example.org/og.jpg", card.imageUrl)
        assertEquals("example.org", card.providerName)
        assertTrue(card.hasImage)
    }

    @Test
    fun aLinkPreviewWithOnlyItsDisplayAddressStillLeadsSomewhere() {
        val card = ThreadsParser.linkCard(j(mapOf("text_post_app_info" to mapOf("link_preview_attachment" to mapOf("title" to "T", "display_url" to "example.org/a")))))
        assertEquals("https://example.org/a", card?.url)
        assertNull(ThreadsParser.linkCard(j(mapOf("text_post_app_info" to mapOf("link_preview_attachment" to mapOf("description" to "only"))))))
    }

    @Test
    fun aReplyNamesWhoItAnswers() {
        val reply = byCode("DAreply001")
        assertTrue(reply.isReply)
        assertEquals("dana", reply.replyToHandle)
    }

    @Test
    fun theLargestCandidateWinsAndTheOriginalSizeIsTheFallbackShape() {
        val post = ThreadsParser.post(j(postJson("333", extra = mapOf(
            "original_width" to 1080,
            "original_height" to 1350,
            "image_versions2" to mapOf("candidates" to listOf(
                mapOf("url" to "https://example.org/small.jpg", "width" to 320, "height" to 400),
                mapOf("url" to "https://example.org/large.jpg", "width" to 1080, "height" to 1350),
            )),
        ))))!!
        assertEquals(listOf("https://example.org/large.jpg"), post.media.map { it.url })
        val unsized = ThreadsParser.post(j(postJson("334", extra = mapOf(
            "original_width" to 1000,
            "original_height" to 500,
            "image_versions2" to mapOf("candidates" to listOf(mapOf("url" to "https://example.org/p.jpg"))),
        ))))!!
        assertEquals(2.0, unsized.media.single().aspectRatio)
    }

    @Test
    fun aSplitThatDoesNotSpellTheCaptionOrIsOnlyTextIsIgnored() {
        fun withFragments(caption: String, parts: List<Map<String, Any?>>) =
            ThreadsParser.post(j(postJson("9", text = caption, info = mapOf("text_fragments" to mapOf("fragments" to parts)))))!!
        val mismatched = withFragments("Hello world", listOf(mapOf(
            "fragment_type" to "mention",
            "plaintext" to "@someone",
            "mention_fragment" to mapOf("mentioned_user" to mapOf("username" to "someone")),
        )))
        assertTrue(mismatched.fragments.isEmpty())
        assertTrue(withFragments("Just words", listOf(mapOf("fragment_type" to "plaintext", "plaintext" to "Just words"))).fragments.isEmpty())
    }

    @Test
    fun aPostWithNothingToShowIsSkipped() {
        assertNull(ThreadsParser.post(j(postJson("1", text = "  "))))
        assertNull(ThreadsParser.post(j(postJson("2") - "pk" - "code")), "no id at all")
    }

    @Test
    fun theAppApiFeedShapeIsReadToo() {
        val timeline = ThreadsParser.feed(J.parse(fixture("app_timeline.json")))
        assertEquals(listOf("3420000000000000001", "3420000000000000003"), timeline.map { it.id })
        assertEquals("meta", timeline.first().handle, "handles are lower case")
        assertEquals(0, timeline.first().selfThreadCount, "someone else's reply is not the author's thread")
        assertEquals("maya", timeline.last().handle)
    }

    @Test
    fun aUserObjectReadsAsAProfile() {
        val profile = ThreadsParser.profile(J.parse(fixture("user_info.json"))["user"])!!
        assertEquals("63404918397", profile.pk)
        assertEquals("instagram", profile.username)
        assertEquals("https://scontent.cdninstagram.com/v/instagram-hd.jpg", profile.profilePicUrl)
        assertEquals(listOf(4100000L, 120L, 8000L), listOf(profile.followerCount, profile.followingCount, profile.mediaCount))
        assertEquals("https://about.instagram.com", profile.externalUrl)
        assertTrue(profile.isVerified)
        assertNull(ThreadsParser.profile(j(mapOf("full_name" to "No username"))))
    }

    @Test
    fun aBareWordIsNoJsonAsDartsJsonDecodeRefusesIt() {
        listOf("<html>Error</html>", "OK", "Error", """{"a": abc}""", "[1, nope]", "").forEach {
            assertNull(ThreadsJson.decode(it), "'$it'")
        }
        listOf("true", "null", "-12.5e3", """{"a":[1,"x",null,false,{"b":0.25}]}""").forEach {
            assertNotNull(ThreadsJson.decode(it), "'$it'")
        }
    }

    @Test
    fun aReshapedPayloadYieldsNothingRatherThanThrowing() {
        assertTrue(ThreadsParser.feed(J(null)).isEmpty())
        assertTrue(ThreadsParser.feed(j(mapOf("threads" to "not a list"))).isEmpty())
        assertTrue(ThreadsParser.graphqlFeed(j(mapOf("data" to mapOf("mediaData" to mapOf("threads" to emptyList<Any>()))))).isEmpty())
        assertNull(ThreadsParser.post(j("a string")))
        assertTrue(ThreadsParser.media(j(mapOf("carousel_media" to listOf("x", 1)))).isEmpty())
    }
}
