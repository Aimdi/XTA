package com.aimdi.xtanative.networks.bluesky

import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlueskyLocalTest {
    private val maya = BlueskyFollow("did:plc:maya", "maya.bsky.social", "Maya Chen", "https://cdn.example/maya.jpg", 1000)
    private val sam = BlueskyFollow("", "sam.bsky.social", "Sam", null, 2000)

    @Test
    fun followingAgainKeepsThePlaceTheDateAndWhatWasKnown() {
        val again = BlueskyFollow("", "MAYA.bsky.social", "Maya C.", null, 9000)
        val follows = BlueskyLocal.followed(listOf(maya, sam), again)
        assertEquals(listOf("Maya C.", "Sam"), follows.map { it.name })
        assertEquals(1000, follows.first().followedAt)
        assertEquals("did:plc:maya", follows.first().did, "the DID known before stays")
    }

    @Test
    fun aBackupsRowsReplaceThoseOfTheirAccountsAndKeepTheRest() {
        val incoming = listOf(BlueskyFollow("", "maya.bsky.social", "Maya (XTA)", null, 5000), BlueskyFollow("did:plc:lee", "", "Lee", null, 6000))
        val merged = BlueskyLocal.merged(listOf(maya, sam), incoming)
        assertEquals(listOf("Maya (XTA)", "Sam", "Lee"), merged.map { it.name })
        assertEquals("did:plc:maya", merged.first().did)
        assertEquals(5000, merged.first().followedAt, "XTA's row is the newer word on its account")
    }

    @Test
    fun anImportAddsOnlyWhoIsNotFollowedYet() {
        val people = listOf(
            BlueskyProfile("did:plc:maya", "maya.bsky.social", "Maya"),
            BlueskyProfile("did:plc:kim", "kim.bsky.social", "Kim"),
            BlueskyProfile("", "", "nobody"),
        )
        val follows = BlueskyLocal.withNew(listOf(maya), people, at = 7000)
        assertEquals(listOf("did:plc:maya", "did:plc:kim"), follows.map { it.actor })
        assertEquals(7000, follows.last().followedAt)
    }

    @Test
    fun aLikeGoesFirstAndLikedAgainMovesUp() {
        val likes = BlueskyLocal.liked(BlueskyLocal.liked(emptyList(), uriOf("1"), 1), uriOf("2"), 2)
        assertEquals(listOf(uriOf("2"), uriOf("1")), likes.map { it.uri })
        assertEquals(listOf(uriOf("1"), uriOf("2")), BlueskyLocal.liked(likes, uriOf("1"), 3).map { it.uri })
        assertEquals(listOf(uriOf("2")), BlueskyLocal.unliked(likes, uriOf("1")).map { it.uri })
    }

    @Test
    fun likesABackupBringsComeAfterThoseKept() {
        val kept = BlueskyLocal.liked(emptyList(), uriOf("1"), 1)
        val merged = BlueskyLocal.mergedLikes(kept, listOf(uriOf("1"), uriOf("2"), "https://bsky.app/x", uriOf("2")), at = 5)
        assertEquals(listOf(BlueskyLike(uriOf("1"), 1), BlueskyLike(uriOf("2"), 5)), merged)
    }

    @Test
    fun theLikedTabShowsTheNewestKeptPostsThatAreStillLiked() {
        val posts = (1..BlueskyLocal.LIKED_POSTS_CAP).map { bluePost("$it") }
        val kept = BlueskyLocal.withLikedPost(posts, bluePost("new"))
        assertEquals(BlueskyLocal.LIKED_POSTS_CAP, kept.size)
        assertEquals(uriOf("new"), kept.first().uri)
        assertEquals(uriOf("${BlueskyLocal.LIKED_POSTS_CAP - 1}"), kept.last().uri)
        assertEquals(listOf(uriOf("1")), BlueskyLocal.withLikedPost(listOf(bluePost("1")), bluePost("1")).map { it.uri })
        val likes = listOf(BlueskyLike(uriOf("2"), 1), BlueskyLike(uriOf("gone"), 2))
        assertEquals(listOf(uriOf("2")), BlueskyLocal.library(listOf(bluePost("1"), bluePost("2")), likes).map { it.uri })
    }

    @Test
    fun aProfilesLikesTabListsOnlyItsAuthorsLikedPosts() {
        val posts = listOf(bluePost("1"), bluePost("2", did = "did:plc:sam"))
        assertEquals(listOf(uriOf("1")), BlueskyLocal.likedBy(posts, did = "did:plc:maya", handle = "").map { it.uri })
        assertEquals(listOf(uriOf("1")), BlueskyLocal.likedBy(posts, did = "", handle = "MAYA.bsky.social").map { it.uri })
        assertEquals(emptyList(), BlueskyLocal.likedBy(posts, did = "", handle = ""))
    }

    @Test
    fun theFilesGiveBackWhatTheyKept() {
        val follows = listOf(maya, sam.copy(inFeed = false))
        assertEquals(follows, BlueskyLocal.follows(BlueskyLocal.followsJson(follows)))
        val likes = listOf(BlueskyLike(uriOf("1"), 10), BlueskyLike(uriOf("2"), 20))
        assertEquals(likes, BlueskyLocal.likes(BlueskyLocal.likesJson(likes)))
        val posts = listOf(bluePost("1", publishedAt = at("2026-09-20T10:00:00Z")))
        assertEquals(posts.map { it.uri }, BlueskySnapshot.posts(BlueskyLocal.likedPostsJson(posts)).map { it.uri })
    }

    @Test
    fun damagedFilesReadAsNothingLost() {
        assertEquals(emptyList(), BlueskyLocal.follows("""{"follows": [{"name": "nobody"}, 3]}"""))
        assertEquals(emptyList(), BlueskyLocal.likes("not json"))
        assertEquals(listOf(uriOf("1")), BlueskyLocal.likes("""{"likes": [{"uri": "${uriOf("1")}"}, {"uri": "https://x"}]}""").map { it.uri })
    }

    @Test
    fun xtasRowsBecomeFollowsAndGoBackAsXtaWritesThem() {
        val zone = ZoneId.systemDefault()
        val rows = """[
            {"id": "maya.bsky.social", "name": "Maya", "avatar_url": "https://cdn.example/maya.jpg", "in_feed": 1, "created_at": "2026-01-02 03:04:05"},
            {"id": "did:plc:lee", "name": "", "avatar_url": null, "in_feed": 0},
            {"id": "", "name": "no id"}, {"id": "kim.bsky.social"}
        ]"""
        val now = Instant.parse("2026-09-01T00:00:00Z")
        val follows = BlueskyXtaRows.follows(rows, now)
        assertEquals(2, follows.size)
        assertEquals(BlueskyFollow("", "maya.bsky.social", "Maya", "https://cdn.example/maya.jpg", java.time.LocalDateTime.parse("2026-01-02T03:04:05").atZone(zone).toInstant().toEpochMilli()), follows[0])
        assertEquals(BlueskyFollow("did:plc:lee", "", "did:plc:lee", null, now.toEpochMilli(), inFeed = false), follows[1])

        val written = BlueskyXtaRows.followRows(follows)
        assertEquals(follows, BlueskyXtaRows.follows(written, now))
        assertTrue("\"created_at\":\"2026-01-02 03:04:05\"" in written)
        assertTrue("\"in_feed\":0" in written)
    }

    @Test
    fun xtasLikeRowsAreThePostsUris() {
        val rows = """[{"id": "${uriOf("1")}"}, {"id": "nonsense"}, {"id": "${uriOf("1")}"}, {}]"""
        assertEquals(listOf(uriOf("1")), BlueskyXtaRows.likes(rows))
        assertEquals("""[{"id":"${uriOf("1")}"}]""", BlueskyXtaRows.likeRows(listOf(BlueskyLike(uriOf("1"), 1))))
    }
}
