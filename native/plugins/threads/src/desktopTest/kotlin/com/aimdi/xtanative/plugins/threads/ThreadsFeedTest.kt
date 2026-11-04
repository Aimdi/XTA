package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsLinkCard
import com.aimdi.xtanative.networks.threads.ThreadsMedia
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreadsFeedTest {
    private fun post(id: String, handle: String = "zuck", at: Long = 1_000) =
        ThreadsPost(id = id, handle = handle, authorName = handle, text = "post $id", publishedAt = Instant.ofEpochSecond(at))

    @Test
    fun theSnapshotKeepsTheNewestPostsAndWhenEachAccountAnswered() {
        val at = Instant.parse("2026-11-04T08:00:00Z")
        val posts = (1..130).map { post("$it") } + post("r", handle = "meta").repostedBy("rp", "mosseri", "Adam")
        val snapshot = ThreadsFeedSnapshot.decode(ThreadsFeedSnapshot(posts, mapOf("zuck" to at, "mosseri" to at)).encode())

        assertEquals(ThreadsFeedSnapshot.CAP, snapshot.posts.size)
        assertEquals(at, snapshot.answeredAt["zuck"])
        val followed = ThreadsFeedSnapshot(listOf(post("1"), post("r", handle = "meta").repostedBy("rp", "mosseri", "Adam")), mapOf("zuck" to at, "mosseri" to at))
            .followedBy(setOf("mosseri"))
        assertEquals(listOf("rp"), followed.posts.map { it.id }, "a repost is its reposter's: an account dropped since does not come back")
        assertEquals(setOf("mosseri"), followed.answeredAt.keys)
        assertEquals(listOf("rp"), followed.postsOf("mosseri").map { it.id })
    }

    @Test
    fun aSnapshotOfAnotherShapeIsNoSnapshot() {
        assertTrue(ThreadsFeedSnapshot.decode("""{"v":2,"posts":[{"id":"1","handle":"zuck"}]}""").isEmpty)
        assertTrue(ThreadsFeedSnapshot.decode("").isEmpty)
        assertTrue(ThreadsFeedSnapshot.decode(null).isEmpty)
    }

    @Test
    fun theFiltersAreReadAsXtaWritesThemAndHideWhatTheyName() {
        val options = ThreadsFeedOptions.decode("""{"content":"media","hideReplies":true,"hideReposts":false}""")
        assertEquals(ThreadsFeedOptions(ThreadsFeedContent.MEDIA, hideReplies = true), options)
        assertEquals(2, options.activeCount)
        assertEquals(options, ThreadsFeedOptions.decode(options.encode()))
        assertEquals(ThreadsFeedOptions(), ThreadsFeedOptions.decode("{oops"))

        val photo = post("p").copy(media = listOf(ThreadsMedia("https://cdn/p.jpg")))
        val quotedPhoto = post("q").copy(quoted = photo)
        val reply = photo.copy(id = "r", isReply = true)
        assertEquals(listOf("p", "q"), options.shown(listOf(photo, quotedPhoto, reply, post("t"))).map { it.id })
    }

    @Test
    fun aPostLinksOutByItsCardItsResolvedLinkOrABareAddress() {
        assertTrue(ThreadsFeedOptions.hasLink(post("1").copy(linkCard = ThreadsLinkCard("https://example.com"))))
        assertTrue(ThreadsFeedOptions.hasLink(post("2").copy(fragments = listOf(ThreadsTextFragment(ThreadsTextFragment.Kind.LINK, "example.com/a…", "https://example.com/a")))))
        assertTrue(ThreadsFeedOptions.hasLink(post("3").copy(text = "see HTTPS://example.com")))
        assertEquals(false, ThreadsFeedOptions.hasLink(post("4")))
    }
}
