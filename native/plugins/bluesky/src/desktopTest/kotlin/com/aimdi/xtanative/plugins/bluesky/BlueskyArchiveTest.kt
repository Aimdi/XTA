package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.bluesky.BlueskyFacet
import com.aimdi.xtanative.networks.bluesky.BlueskyLinkCard
import com.aimdi.xtanative.networks.bluesky.BlueskyMedia
import com.aimdi.xtanative.networks.bluesky.BlueskyPost
import com.aimdi.xtanative.store.saved.SavedContent
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlueskyArchiveTest {
    private val quoted = BlueskyPost(
        uri = "at://did:plc:sam/app.bsky.feed.post/2",
        cid = "c2",
        handle = "sam.bsky.social",
        did = "did:plc:sam",
        authorName = "Sam",
        text = "The quoted one",
        url = "https://bsky.app/profile/sam.bsky.social/post/2",
    )

    private val post = BlueskyPost(
        uri = "at://did:plc:maya/app.bsky.feed.post/1",
        cid = "c1",
        handle = "maya.bsky.social",
        did = "did:plc:maya",
        authorName = "Maya Chen",
        text = "Kiln day #ceramics",
        url = "https://bsky.app/profile/maya.bsky.social/post/1",
        avatarUrl = "https://cdn.example/maya.jpg",
        media = listOf(
            BlueskyMedia("https://cdn.example/a.jpg", aspectRatio = 1.5, alt = "A bowl"),
            BlueskyMedia("https://cdn.example/poster.jpg", isVideo = true, videoUrl = "https://video.example/v.m3u8"),
        ),
        facets = listOf(BlueskyFacet(10, 19, BlueskyFacet.Kind.TAG, "ceramics")),
        publishedAt = Instant.parse("2026-11-04T09:30:00.123Z"),
        repostCount = 3,
        likeCount = 12,
        repostedByName = "Sam",
        repostedByDid = "did:plc:sam",
        quotedPost = quoted,
        linkCard = BlueskyLinkCard("https://example.com", "Example", "A page", "https://example.com/i.jpg"),
        labels = listOf("sensitive"),
    )

    @Test
    fun aPostIsFiledInXtasRecordAndReadBackWhole() {
        val archive = BlueskyArchive.of(post)
        assertEquals("bluesky:at://did:plc:maya/app.bsky.feed.post/1", archive.id)
        assertEquals("did:plc:maya", archive.authorId)
        assertEquals(post, BlueskyArchive.post(archive.content))
    }

    @Test
    fun savedReadsTheRecordAsBlueskysAndOffersOnlyItsPicture() {
        val kept = SavedContent.of(BlueskyArchive.of(post).content)
        assertEquals(SavedContent.Kind.BLUESKY, kept.kind)
        assertEquals("bluesky", kept.network)
        assertTrue(kept.matches("kiln day"))
        assertTrue(kept.matches("the quoted one"))
        assertEquals(listOf("https://cdn.example/a.jpg"), kept.photos.map { it.url })
    }

    @Test
    fun aRecordXtaWroteReadsAsItsPost() {
        val xta = J.parse(
            """{"xtaPlugin":"bluesky","post":{"uri":"at://did:plc:a/app.bsky.feed.post/9","handle":"a.test","did":"did:plc:a",
            "text":"From XTA","images":["https://cdn/x.jpg"],"imageIsVideo":[false],"facets":[{"byteStart":0,"byteEnd":4,"kind":"link","value":"https://x"}],
            "publishedAt":"2026-01-02T03:04:05.000Z","replyToHandle":"b.test","likeCount":2.0}}""",
        ).raw!!
        val read = BlueskyArchive.post(xta)!!
        assertEquals("a.test", read.authorName, "no name: the handle")
        assertEquals(listOf("https://cdn/x.jpg"), read.images)
        assertEquals(BlueskyFacet.Kind.LINK, read.facets.single().kind)
        assertEquals(Instant.parse("2026-01-02T03:04:05Z"), read.publishedAt)
        assertTrue(read.isReply, "a reply when it names whom it answers")
        assertEquals(2L, read.likeCount)
    }

    @Test
    fun anotherNetworksRecordOrOneWithoutItsUriIsNone() {
        assertNull(BlueskyArchive.post(J.parse("""{"xtaPlugin":"reddit","post":{"id":"x"}}""").raw!!))
        assertNull(BlueskyArchive.post(J.parse("""{"xtaPlugin":"bluesky","post":{"uri":"nope"}}""").raw!!))
    }
}
