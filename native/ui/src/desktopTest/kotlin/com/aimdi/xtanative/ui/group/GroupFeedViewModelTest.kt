package com.aimdi.xtanative.ui.group

import androidx.lifecycle.viewModelScope
import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.FeedReads
import com.aimdi.xtanative.core.feed.GroupFeed
import com.aimdi.xtanative.core.feed.ReadPosition
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.AppGraph
import com.aimdi.xtanative.store.settings.PostsPrefs
import java.io.File
import java.nio.file.Files
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * The group feed's view model with X stood in for (feed_reset_reload_test,
 * feed_session_cache_test, stale_feed_preview_test and the feed's first page
 * over its stored chunks, _feed.dart): what the reader sees after a cold
 * start, on reopening a feed, while a feed is hidden and when its plan changes.
 */
class GroupFeedViewModelTest {
    private val base = Instant.parse("2026-11-01T00:00:00Z")
    private val nasa = FollowedAccount("1", "nasa", "NASA", null, verified = false, inFeed = true, followedAt = 1)
    private val esa = FollowedAccount("2", "esa", "ESA", null, verified = false, inFeed = true, followedAt = 2)
    private val session = Session(following = listOf(nasa, esa), groups = listOf(FollowGroup("g1", "Space", listOf("1"))))

    private fun thread(id: Long, by: FollowedAccount = nasa) =
        XThread(id.toString(), listOf(XPost(id.toString(), XUser(by.id, by.screenName, by.name), base.plusSeconds(id), "post $id")))

    private fun page(vararg ids: Long, next: String? = null, by: FollowedAccount = nasa) = XTimelinePage(ids.map { thread(it, by) }, next, null)

    /** Stands in for X: a search answers by the first key its query contains, page by page. */
    private class Reads(var pages: Map<Pair<String, String?>, XTimelinePage> = emptyMap()) : FeedReads {
        val searches = CopyOnWriteArrayList<Pair<String, String?>>()

        /** Every search waits for it, while it is set. */
        @Volatile
        var gate: CompletableDeferred<Unit>? = null

        /** Searches (by a key their query contains) that never answer. */
        val silent: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        override suspend fun search(query: String, cursor: String?): XTimelinePage {
            searches += query to cursor
            gate?.await()
            if (silent.any { query.contains(it) }) awaitCancellation()
            return pages.entries.firstOrNull { (key, _) -> query.contains(key.first) && key.second == cursor }?.value ?: XTimelinePage(emptyList(), null, null)
        }

        override suspend fun userPosts(userId: String, includeReplies: Boolean) = XTimelinePage(emptyList(), null, null)

        fun cursors() = searches.map { it.second }
    }

    private class Setup(val graph: AppGraph, val dir: File, val sessions: FeedSessionCache)

    private fun withGraph(session: Session = this.session, test: suspend Setup.() -> Unit) = runBlocking {
        val dir = Files.createTempDirectory("group-feed").toFile()
        try {
            val graph = AppGraph(dir, kotlinx.coroutines.flow.flowOf(true), "test")
            graph.sessions.update { session }
            Setup(graph, dir, FeedSessionCache()).test()
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun Setup.open(reads: Reads, groupId: String? = "g1") = GroupFeedViewModel(graph, groupId, reads = reads, sessions = sessions)

    private suspend fun GroupFeedViewModel.until(what: (GroupFeedUi) -> Boolean): GroupFeedUi = withTimeout(10_000) { ui.first(what) }

    private suspend fun waitFor(what: () -> Boolean) = withTimeout(10_000) { while (!what()) delay(10) }

    private suspend fun GroupFeedViewModel.onMain(action: GroupFeedViewModel.() -> Unit) = withContext(Dispatchers.Main) { action() }

    private fun GroupFeedViewModel.close() = viewModelScope.cancel()

    private fun ids(ui: GroupFeedUi) = ui.run.shown.threads.map { it.id.toLong() }

    private suspend fun Setup.lastRead(): ReadPosition? = ReadMarks(graph.feedCache(ReadMarks.key("g1"))).position()

    @Test
    fun aColdStartShowsWhatWasWrittenSinceAboveTheKeptPostsAndBringsTheReaderBackToTheLastReadOne() = withGraph {
        val before = Reads(mapOf(("from:nasa" to null) to page(100, 90, 80, next = "a2")))
        val earlier = GroupFeed(session.feedPlan("g1"), before)
        val shown = earlier.first().threads
        graph.feedCache("g1").write(SavedFeed(shown, earlier.state(), savedAt = 1, key = earlier.plan.readKey))
        ReadMarks(graph.feedCache(ReadMarks.key("g1"))).record(thread(90), 1)

        val reads = Reads(
            mapOf(
                ("from:nasa" to null) to page(130, 120, next = "n2"),
                ("from:nasa" to "n2") to page(110, 100, next = "n3"),
                ("from:nasa" to "a2") to page(70),
            ),
        )
        val vm = open(reads)
        val ui = vm.until { it.run.answered }
        assertEquals(listOf(130L, 120, 110, 100, 90, 80), ids(ui))
        assertEquals(4, ui.run.divider, "the divider sits on the last read post")
        assertEquals(4, ui.run.jump?.threadIndex, "and the reader is brought back to it")
        assertEquals(listOf(null, "n2"), reads.cursors(), "paged down to the kept posts, not past them")
        assertEquals("90", lastRead()?.threadId, "nothing new was marked read")

        vm.onMain { loadMore() }
        assertEquals(listOf(130L, 120, 110, 100, 90, 80, 70), ids(vm.until { it.run.shown.threads.size == 7 }), "loading more reads on below the kept posts")
        vm.close()
    }

    @Test
    fun aFirstPageThatDoesNotReachTheLastReadPostMarksNothingRead() = withGraph {
        graph.feedCache("g1").write(SavedFeed(listOf(thread(60)), null, savedAt = 1))
        ReadMarks(graph.feedCache(ReadMarks.key("g1"))).record(thread(50), 1)
        val reads = Reads(mapOf(("from:nasa" to null) to page(130, 120, next = "n2")))
        val vm = open(reads)
        assertEquals(listOf(130L, 120), ids(vm.until { it.run.answered }))
        delay(100)
        assertEquals("50", lastRead()?.threadId, "the posts between were never on screen")
        vm.close()
    }

    @Test
    fun aFeedOpenedAgainInTheSameRunShowsItsPagesWhereTheReaderLeftThemWithoutAskingX() = withGraph {
        val reads = Reads(mapOf(("from:nasa" to null) to page(130, 120, next = "n2")))
        val first = open(reads)
        first.until { it.run.answered }
        first.onMain { keepPlace(1, 20) }
        waitFor { sessions.feed("g1") != null }
        first.close()

        val again = Reads(mapOf(("from:nasa" to "n2") to page(110, 100)))
        val vm = open(again)
        val ui = vm.until { it.run.answered }
        assertEquals(listOf(130L, 120), ids(ui))
        assertEquals(FeedJump(1, 20, ui.run.jump!!.token), ui.run.jump)
        assertTrue(again.searches.isEmpty(), "nothing was asked of X")

        vm.onMain { loadMore() }
        assertEquals(listOf(130L, 120, 110, 100), ids(vm.until { it.run.shown.threads.size == 4 }))
        assertEquals(listOf<String?>("n2"), again.cursors(), "it reads on from its cursor")
        vm.close()
    }

    @Test
    fun theSessionKeepsTheEightFeedsUsedLast() {
        val cache = FeedSessionCache(maxEntries = 2)
        cache.keepPlace("a", KeptPlace("1", 0, true))
        cache.keepPlace("b", KeptPlace("2", 0, true))
        assertNotNull(cache.place("a"))
        cache.keepPlace("a", KeptPlace("1", 5, false))
        cache.keepPlace("c", KeptPlace("3", 0, true))
        assertEquals(null, cache.place("b"), "the least recently used goes, not the first made")
        assertNotNull(cache.place("a"))
        assertEquals(2, cache.size)
        cache.evict("a")
        assertEquals(null, cache.place("a"))
        cache.clear()
        assertEquals(0, cache.size)
        assertEquals(8, FeedSessionCache().let { full -> repeat(10) { full.keepPlace("$it", KeptPlace("$it", 0, true)) }; full.size })
    }

    @Test
    fun aHiddenFeedStopsReadingAndReadsOnceSeenAgain() = withGraph {
        val reads = Reads(mapOf(("from:nasa" to null) to page(130, 120)))
        reads.gate = CompletableDeferred()
        val vm = open(reads)
        waitFor { reads.searches.size == 1 }
        vm.onMain { visible(false) }
        reads.gate = null
        delay(200)
        val hidden = vm.until { it.loaded }
        assertFalse(hidden.run.refreshing)
        assertFalse(hidden.run.answered)
        assertEquals(1, reads.searches.size, "a read that was only stopped is not started again while hidden")

        vm.onMain { refresh() }
        delay(100)
        assertEquals(1, reads.searches.size, "a hidden feed reads nothing")

        vm.onMain { visible(true) }
        assertEquals(listOf(130L, 120), ids(vm.until { it.run.answered }))
        assertEquals(2, reads.searches.size)
        vm.close()
    }

    @Test
    fun onlyAChangeOfWhatTheFeedReadsReadsItAgain() = withGraph {
        val reads = Reads(mapOf(("from:nasa" to null) to page(130, 120), ("from:esa" to null) to page(125, by = esa)))
        val vm = open(reads)
        vm.until { it.run.answered }
        graph.settings.set(PostsPrefs.languages, "de")
        graph.settings.set(PostsPrefs.zenMode, true)
        delay(1_000)
        assertEquals(1, reads.searches.size, "the language filter and zen mode apply from the next load on")

        graph.sessions.update { it.copy(groups = listOf(FollowGroup("g1", "Space", listOf("1", "2")))) }
        waitFor { reads.searches.size > 1 }
        assertTrue(reads.searches.last().first.contains("from:esa"), "a member added reads the feed again")
        vm.close()
    }

    @Test
    fun aFirstLoadShowsTheChunksAsTheyAnswer() {
        val alpha = FollowedAccount("1", "alpha".padEnd(250, '_'), "Alpha", null, verified = false, inFeed = true, followedAt = 1)
        val beta = FollowedAccount("2", "beta".padEnd(250, '_'), "Beta", null, verified = false, inFeed = true, followedAt = 2)
        val apart = Session(following = listOf(alpha, beta), groups = listOf(FollowGroup("g1", "Space", listOf("1", "2"))))
        withGraph(apart) {
            val reads = Reads(mapOf(("from:alpha" to null) to page(130, 120, next = "a2", by = alpha)))
            reads.silent += "from:beta"
            val vm = open(reads)
            val ui = vm.until { it.run.shown.threads.isNotEmpty() }
            assertEquals(listOf(130L, 120), ids(ui), "alpha's posts show while beta is still being read")
            assertFalse(ui.run.answered)
            vm.close()
        }
    }
}
