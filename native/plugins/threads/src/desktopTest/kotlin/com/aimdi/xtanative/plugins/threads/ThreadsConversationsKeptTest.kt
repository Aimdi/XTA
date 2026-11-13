package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsConversation
import com.aimdi.xtanative.networks.threads.ThreadsImage
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsProfile
import com.aimdi.xtanative.networks.threads.ThreadsProfilePage
import com.aimdi.xtanative.store.StoreDir
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** The conversations kept so that one opened again shows at once what it showed last, and which of them a screen shows. */
class ThreadsConversationsKeptTest {
    private val at = Instant.parse("2026-11-01T09:30:00Z")
    private var now = at
    private val dir = StoreDir(Files.createTempDirectory("threads-conversations").toFile())
    private val url = "https://www.threads.com/@zuck/post/C1"

    private fun shelf() = ThreadsConversationShelf(dir::file) { now }

    private fun post(id: String, handle: String = "zuck") =
        ThreadsPost(id = id, handle = handle, authorName = handle, text = "t$id", url = "https://www.threads.com/@$handle/post/C$id")

    private val read = ThreadsConversation(
        focus = post("1"),
        ancestors = listOf(post("0", "ola")),
        continuation = listOf(post("1b")),
        replies = listOf(listOf(post("2", "a"), post("3", "zuck")), listOf(post("4", "b"))),
        loaded = true,
        levels = mapOf("2" to 1, "3" to 2, "4" to 1),
        unfinished = setOf("4"),
        repliesNext = "cursor",
        related = listOf(post("9", "c")),
    )

    @Test
    fun aConversationReadsBackAsItWasShownWithoutItsCursor() = runBlocking {
        shelf().keep(url, read, at)
        val back = shelf().of("https://www.threads.net/@zuck/post/C1?igshid=x")!!
        assertEquals(read.copy(repliesNext = null), back.value, "kept by its short code, whichever link opens it")
        assertEquals(at, back.readAt)
        assertNull(shelf().of("https://www.threads.com/@zuck/post/C2"))
    }

    @Test
    fun onlyAReadConversationIsKeptAndItIsCutToItsFirstPosts() = runBlocking {
        shelf().keep(url, ThreadsConversation.seed(post("1")), at)
        assertNull(shelf().of(url), "a tapped card alone is no conversation")
        val long = read.copy(replies = (1..30).map { n -> (1..10).map { post("r$n-$it", "u$n") } })
        val kept = ThreadsConversationsKept.trimmed(long)!!
        assertTrue(kept.replies.all { it.size <= ThreadsConversationsKept.CHAIN_POSTS })
        assertTrue(kept.ancestors.size + 1 + kept.continuation.size + kept.replyCount <= ThreadsConversationsKept.POSTS)
        assertTrue(kept.moreReplies, "what was cut is said to be on the web")
        assertTrue(kept.levels.keys.all { id -> kept.replies.flatten().any { it.id == id } })
    }

    @Test
    fun theNewestFiftyOfTheLastWeekAreKept() = runBlocking {
        val shelf = shelf()
        (1..ThreadsConversationShelf.CAP + 2).forEach { n -> shelf.keep("https://www.threads.com/@zuck/post/K$n", read, at.plusSeconds(n.toLong())) }
        assertNull(shelf().of("https://www.threads.com/@zuck/post/K2"))
        assertEquals(read.focus, shelf().of("https://www.threads.com/@zuck/post/K3")?.value?.focus)
        now = at.plusSeconds(ThreadsConversationShelf.MAX_AGE_DAYS * 24 * 3600 + 120)
        assertNull(shelf().of("https://www.threads.com/@zuck/post/K60".replace("K60", "K${ThreadsConversationShelf.CAP + 2}")), "a week on, nothing kept is shown")
    }

    @Test
    fun aScreenShowsWhatWasReadElseWhatWasKeptElseTheTappedCard() {
        val seed = post("1")
        val kept = read.copy(replies = emptyList())
        assertSame(read, ThreadsConversationView.shown(read, kept, seed))
        assertSame(kept, ThreadsConversationView.shown(ThreadsConversation.seed(seed), kept, seed), "while the page is read, the kept one")
        assertEquals(ThreadsConversation.seed(seed), ThreadsConversationView.shown(null, null, seed))
    }

    @Test
    fun aProfileScreenShowsItsCardAsSoonAsItIsKnownOverWhatWasKept() {
        val card = ThreadsProfile(pk = "1", id = "1", username = "zuck", fullName = "Mark")
        val keptPage = ThreadsProfilePage(card.copy(fullName = "Old"), listOf(post("1")))
        val readPage = ThreadsProfilePage(card, listOf(post("2")))
        assertSame(readPage, ThreadsProfileView.shownPage(readPage, card, keptPage))
        assertEquals(ThreadsProfilePage(card, listOf(post("1"))), ThreadsProfileView.shownPage(null, card, keptPage), "the fresh card over the kept threads")
        assertEquals(ThreadsProfilePage(card), ThreadsProfileView.shownPage(null, card, null), "the header before any thread")
        assertSame(keptPage, ThreadsProfileView.shownPage(null, null, keptPage))
        assertNull(ThreadsProfileView.shownPage(null, null, null))
    }

    @Test
    fun theFaceLoadsTheSmallestPictureThatFillsItAndTheBannerTheSmallOne() {
        val profile = ThreadsProfile(
            pk = "1", id = "1", username = "zuck", fullName = "Mark",
            profilePicUrl = "https://cdn.example/s150x150/zuck.jpg", hdPictureUrl = "https://cdn.example/zuck_1080.jpg",
            pictureSizes = listOf(
                ThreadsImage("https://cdn.example/zuck_1080.jpg", 1080, 1080),
                ThreadsImage("https://cdn.example/zuck_320.jpg", 320, 320),
                ThreadsImage("https://cdn.example/s150x150/zuck.jpg"),
            ),
        )
        assertEquals("https://cdn.example/zuck_320.jpg", profile.pictureFor(240), "an 80 dp face at 3x")
        assertEquals("https://cdn.example/s150x150/zuck.jpg", profile.pictureFor(120))
        assertEquals("https://cdn.example/zuck_1080.jpg", profile.pictureFor(2000), "none fills it: the largest")
        assertEquals("https://cdn.example/zuck_1080.jpg", profile.pictureUrl, "the viewer keeps the largest")
        assertEquals("https://cdn.example/s150x150/zuck.jpg", ThreadsProfileView.bannerUrl(profile), "blurred, the small one serves")
        assertEquals("https://cdn.example/zuck_1080.jpg", profile.copy(pictureSizes = emptyList()).pictureFor(240), "sizes unknown: as before")
    }
}
