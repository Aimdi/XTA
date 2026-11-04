package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.reddit.RedditPost
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedditPostJsonTest {
    private val post = RedditPost(
        id = "abc123",
        title = "Dart 4 is out",
        subreddit = "dartlang",
        permalink = "/r/dartlang/comments/abc123/dart_4_is_out/",
        author = "someone",
        score = 412,
        commentCount = 37,
        createdAt = Instant.parse("2026-01-21T12:53:20Z"),
        url = "https://v.redd.it/xyz",
        over18 = true,
        flair = "News",
        domain = "v.redd.it",
        galleryImages = listOf("https://i.redd.it/1.jpg", "https://i.redd.it/2.jpg"),
        previewImage = "https://preview.redd.it/p.jpg",
        videoManifestUrl = "https://v.redd.it/xyz/DASHPlaylist.mpd",
        videoFallbackUrl = "https://v.redd.it/xyz/DASH_720.mp4",
        videoAspectRatio = 1.5,
    )

    @Test
    fun aPostComesBackAsItWasKept() {
        assertEquals(post, RedditPostJson.decode(J(RedditPostJson.encode(post))))
        assertEquals(listOf(post), RedditPostJson.list(RedditPostJson.listText(listOf(post))))
    }

    @Test
    fun itIsKeptUnderXtasKeys() {
        val kept = J(RedditPostJson.encode(post))
        assertEquals(37L, kept["commentCount"].long)
        assertEquals("https://v.redd.it/xyz/DASHPlaylist.mpd", kept["videoDashUrl"].string)
        assertEquals("2026-01-21T12:53:20.000Z", kept["createdAt"].string)
        assertTrue(kept["over18"].boolean == true)
    }

    @Test
    fun whatXtaSavedReadsHere() {
        val saved = """[{"id":"q1","title":"A local one","subreddit":"pics","permalink":"/r/pics/comments/q1/a/","author":null,
            "score":3.0,"commentCount":0,"createdAt":"2025-06-01T10:00:00.000","url":null,"isSelf":true,"selfText":"words",
            "over18":false,"spoiler":true,"stickied":false,"thumbnail":"self","flair":null,"domain":"self.pics","galleryImages":[],
            "previewImage":null,"videoDashUrl":null,"videoFallbackUrl":null,"videoAspectRatio":null},
            {"id":"nameless"}, 7]"""
        val read = RedditPostJson.list(saved).single()
        assertEquals("q1", read.id)
        assertEquals(3, read.score)
        assertTrue(read.isSelf && read.spoiler)
        assertEquals("words", read.selfText)
        assertEquals(null, read.thumbnailUrl)
        assertTrue(read.createdAt != null)
        assertEquals(emptyList(), RedditPostJson.list("not a list"))
    }
}
