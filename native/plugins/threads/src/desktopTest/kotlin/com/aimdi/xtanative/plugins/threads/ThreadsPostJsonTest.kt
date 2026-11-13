package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsImage
import com.aimdi.xtanative.networks.threads.ThreadsLinkCard
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPoll
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsVideoRendition
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThreadsPostJsonTest {
    private val video = ThreadsMedia(
        url = "https://cdn/poster.jpg",
        aspectRatio = 0.5625,
        alt = null,
        renditions = listOf(ThreadsVideoRendition("https://cdn/1080.mp4", 1080, 1920), ThreadsVideoRendition("https://cdn/720.mp4", 720, 1280)),
    )
    private val post = ThreadsPost(
        id = "3456",
        handle = "zuck",
        authorName = "Mark",
        text = "Hello @mosseri https://example.com",
        avatarUrl = "https://cdn/zuck.jpg",
        media = listOf(ThreadsMedia("https://cdn/photo.jpg", 1.5, "A dog"), video),
        publishedAt = Instant.parse("2026-11-01T09:30:00Z"),
        url = "https://www.threads.com/@zuck/post/ABC",
        likeCount = 12,
        replyCount = null,
        linkCard = ThreadsLinkCard("https://example.com", "Example", providerName = "example.com"),
        quoted = ThreadsPost(id = "9", handle = "meta", authorName = "Meta", text = "quoted", quoted = ThreadsPost(id = "8", handle = "x", authorName = "x", text = "deep")),
        topicTag = "Books",
        fragments = listOf(ThreadsTextFragment(ThreadsTextFragment.Kind.TEXT, "Hello "), ThreadsTextFragment(ThreadsTextFragment.Kind.MENTION, "@mosseri", "mosseri")),
        selfThreadCount = 2,
        repostedByHandle = "mosseri",
        repostedByName = "Adam",
        isVerified = true,
        replyToHandle = "meta",
        isReply = true,
    )

    @Test
    fun aPostKeptReadsBackAsItWasEveryRenditionAndAQuoteOneLevelDeep() {
        val back = ThreadsPostJson.list(ThreadsPostJson.listText(listOf(post))).single()

        assertEquals(post.copy(quoted = post.quoted?.copy(quoted = null)), back)
        assertEquals(listOf("https://cdn/1080.mp4", "https://cdn/720.mp4"), back.media[1].renditions.map { it.url })
        assertNull(back.replyCount, "a count Meta did not give stays untold")
    }

    @Test
    fun itIsWrittenUnderXtasKeys() {
        val json = J(ThreadsPostJson.json(post))

        assertEquals("zuck", json["handle"].string)
        assertEquals("https://cdn/photo.jpg", json["images"][0].string)
        assertEquals("https://cdn/1080.mp4", json["videoUrls"][1].string)
        assertEquals(null, json["videoUrls"][0].string, "a still has no video")
        assertEquals(720L, json["videoRenditions"][1][1]["width"].long)
        assertEquals("mention", json["fragments"][1]["kind"].string)
        assertEquals("2026-11-01T09:30:00.000Z", json["publishedAt"].string)
        assertEquals(false, json["quoted"]["quoted"].exists, "a quote of a quote is not kept")
    }

    @Test
    fun aPostXtaKeptBeforeRenditionsPlaysItsOneVideo() {
        val kept = """[{"id":"1","handle":"zuck","authorName":"Mark","text":"","images":["https://cdn/p.jpg"],
            "imageAspects":[1.0],"imageAlts":[null],"videoUrls":["https://cdn/v.mp4"],"publishedAt":"2026-11-01T10:00:00.000",
            "likeCount":3,"replyToHandle":"meta"}]"""

        val read = ThreadsPostJson.list(kept).single()

        assertEquals(listOf(ThreadsVideoRendition("https://cdn/v.mp4")), read.media.single().renditions)
        assertEquals(true, read.isReply, "XTA reads a reply from whom it answers when isReply is missing")
        assertEquals(3L, read.likeCount)
    }

    @Test
    fun aRowWithoutAnIdIsDropped() {
        assertEquals(emptyList(), ThreadsPostJson.list("""[{"handle":"zuck"},"x",3]"""))
        assertEquals(emptyList(), ThreadsPostJson.list("not json"))
    }

    @Test
    fun everySizeOfAPictureIsKeptBesideXtasListsSoAKeptPostsTilesStillFit() {
        val sizes = listOf(ThreadsImage("https://cdn/big.jpg", 1440, 1800), ThreadsImage("https://cdn/small.jpg", 320, 400))
        val pictured = post.copy(media = listOf(ThreadsMedia("https://cdn/big.jpg", 0.8, images = sizes)), quoted = null)

        val json = J(ThreadsPostJson.json(pictured))
        assertEquals("https://cdn/big.jpg", json["images"][0].string, "XTA's own list still names the largest")
        assertEquals(320L, json["imageSizes"][0][1]["width"].long)

        val back = ThreadsPostJson.post(json)!!
        assertEquals(sizes, back.media.single().images)
        assertEquals("https://cdn/small.jpg", back.media.single().tileUrl(300))
    }

    @Test
    fun sizesKeptForAnotherAddressAreNotThePictures() {
        val kept = """{"id":"1","handle":"zuck","images":["https://cdn/new.jpg"],
            "imageSizes":[[{"url":"https://cdn/old.jpg","width":1440,"height":1800},{"url":"https://cdn/old-small.jpg","width":320,"height":400}]]}"""

        val read = ThreadsPostJson.post(J.parse(kept))!!

        assertEquals(emptyList(), read.media.single().images)
        assertEquals("https://cdn/new.jpg", read.media.single().tileUrl(300))
    }

    @Test
    fun metasCoverAndTheCardsFactsAreKeptAndTheCoverWrittenOnlyWhenThereIsOne() {
        val facts = ThreadsPostFacts(topicEmoji = "📚", shareCount = 4, aiLabel = true, poll = ThreadsPoll(listOf(ThreadsPoll.Option("Yes", 3), ThreadsPoll.Option("No", 1))))
        val covered = post.copy(quoted = null, mediaCovered = true, mediaCoverWords = "Graphic", facts = facts)

        val back = ThreadsPostJson.list(ThreadsPostJson.listText(listOf(covered))).single()

        assertEquals(covered, back)
        assertEquals(false, J(ThreadsPostJson.json(post))["mediaCovered"].exists, "XTA's keys alone when nothing is covered")
    }
}
