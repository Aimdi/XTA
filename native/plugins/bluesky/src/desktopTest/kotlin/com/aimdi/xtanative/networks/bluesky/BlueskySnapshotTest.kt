package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BlueskySnapshotTest {
    /** A liked post as XTA's BlueskyPost.toJson writes it: its times in the device's time, without a zone. */
    private val xta = """{
        "uri": "at://did:plc:maya/app.bsky.feed.post/3k", "cid": "c1", "handle": "maya.bsky.social", "did": "did:plc:maya",
        "authorName": "Maya", "avatarUrl": "https://cdn.example/a.jpg", "text": "Hello @sam",
        "images": ["https://cdn.example/i.jpg", "https://cdn.example/v.jpg"], "imageAspects": [1.5, null],
        "imageIsVideo": [false, true], "imageAlts": ["A cat", null], "videoUrls": [null, "https://video.example/p.m3u8"],
        "imageIsGif": [false, false],
        "facets": [{"byteStart": 6, "byteEnd": 10, "kind": "mention", "value": "did:plc:sam"}, {"byteStart": 3, "byteEnd": 1, "kind": "tag", "value": "x"}],
        "publishedAt": "2026-09-20T12:00:00.000", "repostedAt": null, "url": "https://bsky.app/profile/maya.bsky.social/post/3k",
        "replyCount": 2, "repostCount": 3, "likeCount": 4, "quoteCount": 1, "repostedByName": null, "repostedByHandle": null,
        "repostedByDid": null, "quotedPost": {"uri": "at://did:plc:sam/app.bsky.feed.post/1", "handle": "sam.bsky.social", "text": "quoted"},
        "linkCard": {"url": "https://example.com", "title": "Example", "description": null, "imageUrl": null},
        "replyToHandle": "sam.bsky.social", "isReply": true, "replyToUri": "at://did:plc:sam/app.bsky.feed.post/1", "labels": ["!warn"]
    }"""

    @Test
    fun aPostXtaKeptReadsWholly() {
        val post = BlueskySnapshot.post(J.parse(xta))!!
        assertEquals("Hello @sam", post.text)
        assertEquals(
            listOf(
                BlueskyMedia("https://cdn.example/i.jpg", 1.5, "A cat"),
                BlueskyMedia("https://cdn.example/v.jpg", isVideo = true, videoUrl = "https://video.example/p.m3u8"),
            ),
            post.media,
        )
        assertEquals(listOf(BlueskyFacet(6, 10, BlueskyFacet.Kind.MENTION, "did:plc:sam")), post.facets, "a damaged facet is dropped, not the post")
        assertEquals(LocalDateTime.parse("2026-09-20T12:00:00").atZone(ZoneId.systemDefault()).toInstant(), post.publishedAt)
        assertEquals(listOf(2L, 3L, 4L, 1L), listOf(post.replyCount, post.repostCount, post.likeCount, post.quoteCount))
        assertEquals("quoted", post.quotedPost?.text)
        assertEquals(BlueskyLinkCard("https://example.com", "Example"), post.linkCard)
        assertTrue(post.isReply)
        assertTrue(post.warnsContent)
    }

    @Test
    fun whatThisAppWritesReadsBackTheSame() {
        val post = BlueskySnapshot.post(J.parse(xta))!!
        assertEquals(post, BlueskySnapshot.post(J(BlueskySnapshot.json(post))))
        val written = BlueskySnapshot.json(post).toString()
        listOf("\"imageIsVideo\":[false,true]", "\"kind\":\"mention\"", "\"videoUrls\":[null,\"https://video.example/p.m3u8\"]").forEach {
            assertTrue(it in written, it)
        }
    }

    @Test
    fun aListOfPostsKeepsThoseWithAUri() {
        val posts = BlueskySnapshot.posts("""[$xta, {"text": "no uri"}, 7]""")
        assertEquals(listOf("at://did:plc:maya/app.bsky.feed.post/3k"), posts.map { it.uri })
        assertEquals(emptyList(), BlueskySnapshot.posts("broken"))
    }

    @Test
    fun deepQuotesStopWhereXtaStops() {
        val nested = (1..6).fold("""{"uri": "at://did:plc:a/app.bsky.feed.post/0"}""") { inner, i ->
            """{"uri": "at://did:plc:a/app.bsky.feed.post/$i", "quotedPost": $inner}"""
        }
        val depth = generateSequence(BlueskySnapshot.post(J.parse(nested))) { it.quotedPost }.count()
        assertEquals(5, depth)
    }

    @Test
    fun pinnedFeedsAndListsReadAsXtaKeptThem() {
        val feed = BlueskySnapshot.feed(J.parse("""{"uri": "at://did:plc:a/app.bsky.feed.generator/cats", "displayName": "", "creatorHandle": "a.bsky.social"}"""))!!
        assertEquals(BlueskyFeedGenerator("at://did:plc:a/app.bsky.feed.generator/cats", "cats", creatorHandle = "a.bsky.social"), feed)
        assertEquals(feed, BlueskySnapshot.feed(J(BlueskySnapshot.json(feed))))
        val list = BlueskySnapshot.list(J.parse("""{"uri": "at://did:plc:a/app.bsky.graph.list/l", "name": "Friends", "itemCount": 3}"""))!!
        assertEquals(BlueskyListInfo("at://did:plc:a/app.bsky.graph.list/l", "Friends", itemCount = 3), list)
        assertEquals(list, BlueskySnapshot.list(J(BlueskySnapshot.json(list))))
        assertNull(BlueskySnapshot.feed(J.parse("""{"displayName": "no uri"}""")))
    }
}
