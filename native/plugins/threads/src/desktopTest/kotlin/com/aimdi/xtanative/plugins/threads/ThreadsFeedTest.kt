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
    fun aMutedWordHidesAPostWhereverItsWordsShowAndStaysThroughAReset() {
        val options = ThreadsFeedOptions(hideReplies = true).muting("cat, Black Friday\ncat").muting("CAT")
        assertEquals(listOf("cat", "Black Friday"), options.mutedWords, "split as typed, a word muted once in any case")
        assertEquals(2, options.activeCount)
        assertEquals("""{"content":"all","hideReplies":true,"hideReposts":false,"mutedWords":["cat","Black Friday"]}""", options.encode())
        assertEquals(options, ThreadsFeedOptions.decode(options.encode()))
        assertEquals("""{"content":"all","hideReplies":false,"hideReposts":false}""", ThreadsFeedOptions().encode(), "nothing muted: XTA's keys alone")

        val posts = listOf(
            post("1").copy(text = "My Cat sleeps"),
            post("2").copy(text = "a category of its own"),
            post("3").copy(linkCard = ThreadsLinkCard("https://shop.example", title = "black friday deals")),
            post("4").copy(quoted = post("q").copy(text = "cat!")),
            post("5").copy(topicTag = "Cat"),
            post("6"),
        )
        assertEquals(listOf("2", "6"), options.unmuted(posts).map { it.id }, "a single word only whole, a phrase anywhere")
        assertEquals(listOf("2", "6"), options.shown(posts).map { it.id })

        val reset = options.reset()
        assertEquals(ThreadsFeedOptions(mutedWords = listOf("cat", "Black Friday")), reset)
        assertEquals(false, reset.resettable)
        assertEquals(true, reset.filtered)
        assertEquals(listOf("Black Friday"), reset.unmuting("cat").mutedWords)
        assertEquals(posts, ThreadsFeedOptions().unmuted(posts))
    }

    @Test
    fun aPostLinksOutByItsCardItsResolvedLinkOrABareAddress() {
        assertTrue(ThreadsFeedOptions.hasLink(post("1").copy(linkCard = ThreadsLinkCard("https://example.com"))))
        assertTrue(ThreadsFeedOptions.hasLink(post("2").copy(fragments = listOf(ThreadsTextFragment(ThreadsTextFragment.Kind.LINK, "example.com/a…", "https://example.com/a")))))
        assertTrue(ThreadsFeedOptions.hasLink(post("3").copy(text = "see HTTPS://example.com")))
        assertEquals(false, ThreadsFeedOptions.hasLink(post("4")))
    }
}
