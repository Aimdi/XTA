package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditCommentSort
import com.aimdi.xtanative.networks.reddit.RedditCommentsCursor
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.RedditThread
import com.aimdi.xtanative.store.StoreFile
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class RedditStatesTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private fun post(id: String) = RedditPost(id = id, title = "Post $id", subreddit = "dartlang", permalink = "/r/dartlang/comments/$id/x/")

    private suspend fun <T> settled(state: kotlinx.coroutines.flow.StateFlow<T>, done: (T) -> Boolean): T = withTimeout(5_000) { state.first(done) }

    @Test
    fun theFeedIsReadOnceAndShownAgainWhileFresh(): Unit = runBlocking {
        val reads = AtomicInteger()
        var now = 0L
        val feed = RedditFeed(scope, read = { names, _ -> reads.incrementAndGet(); names.map(::post) }, clock = { now })
        feed.refresh(listOf("a", "b"))
        assertEquals(listOf("a", "b"), settled(feed.state) { it.loaded && !it.loading }.posts.map { it.id })
        feed.refresh(listOf("a", "b"))
        assertEquals(1, reads.get())
        now += RedditFeed.FRESH_MILLIS + 1
        feed.refresh(listOf("a", "b"))
        settled(feed.state) { !it.loading }
        assertEquals(2, reads.get())
        scope.cancel()
    }

    @Test
    fun aPullOrOtherCommunitiesReadAgain(): Unit = runBlocking {
        val forced = mutableListOf<Boolean>()
        val feed = RedditFeed(scope, read = { names, force -> synchronized(forced) { forced += force }; names.map(::post) })
        feed.refresh(listOf("a"))
        settled(feed.state) { it.posts.size == 1 && !it.loading }
        feed.refresh(listOf("a"), force = true)
        settled(feed.state) { !it.loading }
        feed.refresh(listOf("a", "b"))
        assertEquals(2, settled(feed.state) { it.posts.size == 2 && !it.loading }.posts.size)
        assertEquals(listOf(false, true, false), synchronized(forced) { forced.toList() })
        scope.cancel()
    }

    @Test
    fun followingNothingIsAnAnswerAndAFailureKeepsWhatIsShown(): Unit = runBlocking {
        var fail = false
        val feed = RedditFeed(scope, read = { names, _ -> if (fail) throw NetworkRequestException("reddit", 429, "slow down") else names.map(::post) })
        feed.refresh(emptyList())
        val nothing = settled(feed.state) { it.loaded }
        assertTrue(nothing.posts.isEmpty() && !nothing.following)

        feed.refresh(listOf("a"))
        settled(feed.state) { it.posts.size == 1 && !it.loading }
        fail = true
        feed.refresh(listOf("a"), force = true)
        val kept = settled(feed.state) { !it.loading }
        assertEquals(1, kept.posts.size)
        assertNull(kept.problem)

        val empty = RedditFeed(scope, read = { _, _ -> throw NetworkRequestException("reddit", 403, "blocked") })
        empty.refresh(listOf("a"))
        assertNotNull(settled(empty.state) { it.problem != null }.problem)
        scope.cancel()
    }

    @Test
    fun aReadUnderWayIsJoinedNotRepeated(): Unit = runBlocking {
        val gate = CompletableDeferred<Unit>()
        val reads = AtomicInteger()
        val feed = RedditFeed(scope, read = { names, _ -> reads.incrementAndGet(); gate.await(); names.map(::post) })
        feed.refresh(listOf("a"))
        feed.refresh(listOf("a"))
        gate.complete(Unit)
        settled(feed.state) { it.posts.isNotEmpty() }
        assertEquals(1, reads.get())
        scope.cancel()
    }

    private val stub = RedditComment(id = "more", body = "", permalink = "/r/dartlang/comments/p/x/", moreCount = -1, nextPage = RedditCommentsCursor("/svc", "c"))

    private fun thread(vararg ids: String, more: Boolean = false) =
        RedditThread(ids.map { RedditComment(id = it, body = "text $it", replies = listOf(RedditComment(id = "$it-reply", body = "reply"))) } + listOfNotNull(stub.takeIf { more }))

    @Test
    fun aThreadReadsItsCommentsInTheOrderChosenAndFoldsThem(): Unit = runBlocking {
        val sorts = mutableListOf<RedditCommentSort>()
        val state = RedditThreadState(
            post("p"),
            scope,
            visit = { Instant.EPOCH },
            read = { _, sort -> synchronized(sorts) { sorts += sort }; thread("a", "b") },
            fill = { null },
            more = { emptyList() },
        )
        assertEquals(4, settled(state.state) { it.comments != null }.comments?.size)
        assertEquals(Instant.EPOCH, settled(state.state) { it.lastVisit != null }.lastVisit)
        state.toggle("a")
        assertEquals(setOf("a"), state.state.value.collapsed)
        state.toggleAll()
        assertEquals(setOf("a", "b"), state.state.value.collapsed)
        state.resort(RedditCommentSort.NEW)
        assertTrue(state.state.value.collapsed.isEmpty())
        settled(state.state) { it.comments != null }
        assertEquals(listOf(RedditCommentSort.BEST, RedditCommentSort.NEW), synchronized(sorts) { sorts.toList() })
        scope.cancel()
    }

    @Test
    fun heldBackCommentsLoadInPlaceOrSendTheReaderToReddit(): Unit = runBlocking {
        var refuse = false
        val state = RedditThreadState(
            post("p"),
            scope,
            visit = { null },
            read = { _, _ -> thread("a", more = true) },
            fill = { null },
            more = { if (refuse) throw NetworkRequestException("reddit", 403, "no") else listOf(RedditComment(id = "a", body = "again"), RedditComment(id = "c", body = "new")) },
        )
        settled(state.state) { it.comments != null }
        state.loadMore(stub) { error("not refused") }
        val loaded = settled(state.state) { it.comments.orEmpty().none { row -> row.comment.isStub } }
        assertEquals(listOf("a", "a-reply", "c"), loaded.comments?.map { it.comment.id })

        refuse = true
        val refused = CompletableDeferred<Unit>()
        state.loadMore(stub) { refused.complete(Unit) }
        withTimeout(5_000) { refused.await() }
        assertTrue(settled(state.state) { it.moreRefused }.loadingMore.isEmpty())
        scope.cancel()
    }

    @Test
    fun aPostThatArrivedBareIsFilledInFromItsOwnPage(): Unit = runBlocking {
        val bare = RedditPost(id = "p", title = "p", subreddit = "dartlang", permalink = "/r/dartlang/comments/p/")
        val state = RedditThreadState(
            bare,
            scope,
            visit = { null },
            read = { _, _ -> RedditThread(emptyList(), selfText = "from the page") },
            fill = { bare.copy(title = "The real title", score = 9, selfText = "from the card") },
            more = { emptyList() },
        )
        val filled = settled(state.state) { it.post.title == "The real title" && it.comments != null }
        assertEquals(9, filled.post.score)
        assertTrue(filled.selfText == "from the page" || filled.selfText == "from the card")
        scope.cancel()
    }

    @Test
    fun aCommunitysPictureIsLookedUpOnceAndKept(): Unit = runBlocking {
        val folder: File = Files.createTempDirectory("reddit-icons").toFile()
        val lookups = AtomicInteger()
        val gate = CompletableDeferred<Unit>()
        val icons = RedditIcons(RedditIconCache(StoreFile(File(folder, "icons.json"))), scope) { name ->
            lookups.incrementAndGet()
            gate.await()
            if (name == "dartlang") "https://styles.redditmedia.com/dart.png" else null
        }
        val asked = (1..3).map { async { icons.icon("DartLang") } }
        gate.complete(Unit)
        assertEquals(List(3) { "https://styles.redditmedia.com/dart.png" }, asked.awaitAll())
        assertNull(icons.icon("nothing"))
        assertEquals(2, lookups.get())

        val later = RedditIcons(RedditIconCache(StoreFile(File(folder, "icons.json"))), scope) { lookups.incrementAndGet(); null }
        assertEquals("https://styles.redditmedia.com/dart.png", later.icon("dartlang"))
        assertNull(later.icon("nothing"))
        assertEquals(3, lookups.get(), "a miss is not kept on the device: the next session asks again")
        scope.cancel()
    }
}
