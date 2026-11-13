package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.PhotoSize
import com.aimdi.xtanative.core.x.model.XAffiliation
import com.aimdi.xtanative.core.x.model.XArticle
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XCardImage
import com.aimdi.xtanative.core.x.model.XCodec
import com.aimdi.xtanative.core.x.model.XCommunityNote
import com.aimdi.xtanative.core.x.model.XConversation
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XGrokShare
import com.aimdi.xtanative.core.x.model.XLive
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVerifiedType
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.core.x.model.XWithheld
import com.aimdi.xtanative.core.x.model.photoUrl
import com.aimdi.xtanative.core.x.model.playableUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class XModelHelpersTest {
    @Test
    fun aPhotoIsAskedForAtTheSizeItIsShown() {
        assertEquals("https://pbs.twimg.com/media/GxYz?format=jpg&name=medium", photoUrl("https://pbs.twimg.com/media/GxYz.jpg", PhotoSize.MEDIUM))
        assertEquals("https://pbs.twimg.com/media/GxYz?format=png&name=small", photoUrl("https://pbs.twimg.com/media/GxYz.png?name=orig", PhotoSize.SMALL))
        assertEquals("https://example.com/a.jpg", photoUrl("https://example.com/a.jpg", PhotoSize.SMALL), "only X's image host takes the size")
    }

    @Test
    fun theVideoPlayedIsTheBestAPhoneStreamsWell() {
        val video = XMedia(
            XMedia.Kind.VIDEO, "https://pbs.twimg.com/poster.jpg", 1280, 720,
            variants = listOf(
                XVideoVariant("https://video.twimg.com/hls.m3u8", null, "application/x-mpegURL"),
                XVideoVariant("https://video.twimg.com/360.mp4", 832_000, "video/mp4"),
                XVideoVariant("https://video.twimg.com/1080.mp4", 10_368_000, "video/mp4"),
                XVideoVariant("https://video.twimg.com/720.mp4", 2_176_000, "video/mp4"),
            ),
        )
        assertEquals("https://video.twimg.com/720.mp4", video.playableUrl())
        assertEquals("https://video.twimg.com/360.mp4", video.playableUrl(maxBitrate = 100), "too light a cap still plays the lightest")
    }

    private fun post(id: String) = XPost(id, XUser("1", "a", "A"), null, "post $id")

    @Test
    fun anOpenedPostIsSplitIntoWhatItAnswersItselfAndItsReplies() {
        val page = XTimelinePage(
            listOf(XThread("1", listOf(post("1"))), XThread("2", listOf(post("2"))), XThread("m", listOf(post("3"), post("4")))),
            bottomCursor = "more",
            topCursor = null,
        )
        val conversation = XConversation.of(page, "2")
        assertEquals(listOf("1"), conversation.before.map { it.id })
        assertEquals("2", conversation.focal?.id)
        assertEquals(listOf(listOf("3", "4")), conversation.replies.map { thread -> thread.posts.map { it.id } })
        assertEquals("more", conversation.repliesCursor)

        val missing = XConversation.of(page, "9")
        assertNull(missing.focal)
        assertEquals(3, missing.replies.size)
    }

    /** What a row draws of its author: the disk keeps no profile header, nor an article's body. */
    private fun XUser.slim() = XUser(id, screenName, name, avatarUrl, verified, protected, verifiedType = verifiedType, affiliation = affiliation)
    private fun XPost.slim(): XPost =
        copy(author = author.slim(), quoted = quoted?.slim(), reposted = reposted?.slim(), article = article?.copy(content = J(null)))

    @Test
    fun postsReadBackFromDiskAreThePostsWritten() {
        val threads = XParser.timeline(J.parse(javaClass.getResource("/x/user_originals_page.json")!!.readText())).threads
        assertTrue(threads.isNotEmpty())
        val expected = threads.map { thread -> thread.copy(posts = thread.posts.map { it.slim() }) }
        assertEquals(expected, XCodec.decode(XCodec.encode(threads)))
    }

    @Test
    fun everythingARowDrawsIsReadBackFromDisk() {
        val pages = listOf("community_timeline_cards.json", "user_tweets_subscriber_previews.json", "tweet_detail_subscriber_preview.json")
            .map { XParser.conversation(J.parse(javaClass.getResource("/x/$it")!!.readText())).threads }
        val extras = XPost(
            id = "7",
            author = XUser("1", "a", "A", verifiedType = XVerifiedType.GOVERNMENT, affiliation = XAffiliation("https://b/badge.png", "Org")),
            createdAt = null,
            text = "t",
            note = XCommunityNote("note", listOf(XEntity.Link(0, 4, "u", "u", "d")), "Readers added context", "https://x.com/i/birdwatch/n/1"),
            article = XArticle("9", "Title", "Preview", "https://cover"),
            live = XLive(mediaKey = "28_a", broadcastId = "1a", imageUrl = "https://thumb", aspectRatio = 2f),
            grokShare = XGrokShare("https://x.com/i/grok/share/1", "q", "a", "grok", "https://grok.png"),
            quotedWithheld = XWithheld("3", "gone", subscribersOnly = true, screenName = "b"),
            card = XCard.Link(
                "https://l", "T", null, "l", null, null, null, large = true, media = listOf(XMedia(XMedia.Kind.PHOTO, "https://p.jpg", 1, 1)), kind = "summary_large_image",
                imageSizes = mapOf(PhotoSize.THUMB to XCardImage("https://s.jpg", 144, 144), PhotoSize.LARGE to XCardImage("https://xl.jpg", null, null)),
            ),
        )
        val threads = pages.flatten() + XThread("7", listOf(extras))
        val expected = threads.map { thread -> thread.copy(posts = thread.posts.map { it.slim() }) }
        assertEquals(expected, XCodec.decode(XCodec.encode(threads)))
    }

    @Test
    fun aCacheOfAnotherShapeIsDropped() {
        assertTrue(XCodec.decode("""{"v": 99, "threads": []}""").isEmpty())
        assertTrue(XCodec.decode("garbage").isEmpty())
    }
}

class XCardTest {
    private fun fixture(name: String) = J.parse(javaClass.getResource("/x/$name")!!.readText())

    @Test
    fun linkPreviewsReadTheirTitleSitePictureAndWhereTheyLead() {
        // The large card is on the pinned post; the small one on a post quoted by another.
        val cards = XParser.timeline(fixture("community_timeline_cards.json")).threads
            .flatMap { it.posts }.flatMap { listOfNotNull(it, it.quoted) }
            .mapNotNull { it.card as? XCard.Link }
        val large = cards.first { it.large }
        assertEquals("Ultimate Flutter Roadmap for 2025", large.title)
        assertEquals("youtube.com", large.domain)
        assertEquals(800, large.imageWidth)
        assertTrue(large.imageUrl!!.startsWith("https://pbs.twimg.com/card_img/"))
        assertEquals("https://youtu.be/a9cJ3FrTokI", large.url, "the card leads where its t.co link does")
        val small = cards.first { !it.large }
        assertEquals("glint_mcp | Dart package", small.title)
        assertEquals("pub.dev", small.domain)
    }

    @Test
    fun aPollReadsItsChoicesCountsAndEnd() {
        val poll = XParser.post(fixture("tweet_result_image_poll.json"))!!.card as XCard.Poll
        assertEquals(listOf("Option A", "Option B", "Option C"), poll.choices.map { it.label })
        assertEquals(listOf(0L, 0L, 1L), poll.choices.map { it.votes })
        assertEquals(1L, poll.total)
        assertEquals(java.time.Instant.parse("2026-10-12T15:12:54Z"), poll.endsAt)
        assertEquals(false, poll.final)
        val back = XCodec.decode(XCodec.encode(listOf(XThread("1", listOf(XParser.post(fixture("tweet_result_image_poll.json"))!!))))).single()
        assertEquals(poll, back.posts.single().card, "the cache keeps the poll")
    }
}
