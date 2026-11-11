package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.networks.bluesky.BlueskyMedia
import com.aimdi.xtanative.networks.bluesky.BlueskyMediaTile
import com.aimdi.xtanative.networks.bluesky.BlueskyReplySort
import com.aimdi.xtanative.networks.bluesky.BlueskyThread
import com.aimdi.xtanative.networks.bluesky.bluePost
import com.aimdi.xtanative.networks.bluesky.uriOf
import com.aimdi.xtanative.ui.plugins.TextRun
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield

/** XTA's bluesky_conversation_controls_test.dart: the opened thread's store and what its cards show. */
class BlueskyThreadStateTest {
    private class Reads(var server: String = "https://first.example") {
        val pending = mutableListOf<CompletableDeferred<BlueskyThread>>()
        suspend fun read(): BlueskyThread = CompletableDeferred<BlueskyThread>().also { pending += it }.await()
    }

    private suspend fun settle() = repeat(5) { yield() }

    private val root = bluePost("root")

    @Test
    fun aFailedRefreshKeepsTheThreadAndItsControlsAndANewOnePrunesFoldsThatAreGone() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val reads = Reads()
        val thread = ThreadState(scope, { reads.server }, reads::read)
        settle()
        reads.pending.last().complete(BlueskyThread(root, replies = listOf(bluePost("a", parent = "root"), bluePost("a1", parent = "a"))))
        settle()
        thread.selectAuthor(true)
        thread.setAllExpanded(false, BlueskyReplySort.MOST_LIKED)
        assertEquals(setOf(uriOf("a")), thread.state.value.collapsed)

        thread.load()
        settle()
        reads.pending.last().completeExceptionally(IllegalStateException("offline"))
        settle()
        thread.toggleContext()
        val failed = thread.state.value
        assertNotNull(failed.problem)
        assertEquals(2, failed.thread?.replies?.size, "the replies stay")
        assertEquals(setOf(uriOf("a")), failed.collapsed)
        assertFalse(failed.loading)
        assertTrue(failed.contextOpen)
        assertTrue(failed.authorOnly)

        thread.load()
        settle()
        reads.pending.last().complete(BlueskyThread(root, replies = listOf(bluePost("new", parent = "root"))))
        settle()
        assertNull(thread.state.value.problem)
        assertTrue(thread.state.value.collapsed.isEmpty(), "a fold of a reply that is gone goes")
        scope.cancel()
    }

    @Test
    fun theNewestRefreshWinsAndAnEndedStoreReadsNothing() = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        val reads = Reads()
        val thread = ThreadState(scope, { reads.server }, reads::read)
        thread.load()
        settle()
        val (old, new) = reads.pending
        new.complete(BlueskyThread(bluePost("new")))
        settle()
        old.complete(BlueskyThread(bluePost("old")))
        settle()
        assertEquals("new", thread.state.value.thread?.post?.text)

        scope.cancel()
        thread.load()
        settle()
        assertEquals(2, reads.pending.size, "nothing is asked once the screen is gone")
        assertEquals("new", thread.state.value.thread?.post?.text)
    }

    @Test
    fun aLateAnswerFromTheAppViewThatWasLeftIsDroppedAndTheThreadReadAgain() = runBlocking {
        for (fails in listOf(false, true)) {
            val scope = CoroutineScope(coroutineContext + Job())
            val reads = Reads()
            val thread = ThreadState(scope, { reads.server }, reads::read)
            settle()
            reads.server = "https://second.example"
            if (fails) reads.pending.first().completeExceptionally(IllegalStateException("old server error"))
            else reads.pending.first().complete(BlueskyThread(bluePost("stale")))
            settle()
            assertEquals(2, reads.pending.size)
            assertNull(thread.state.value.thread, "the stale answer is not shown")
            assertTrue(thread.state.value.loading)
            assertNull(thread.state.value.problem)
            reads.pending.last().complete(BlueskyThread(bluePost("current")))
            settle()
            assertEquals("current", thread.state.value.thread?.post?.text)
            assertFalse(thread.state.value.loading)
            scope.cancel()
        }
    }

    @Test
    fun aWarningCoversTheTextAndIsShownAgainWhenThePostChanges() {
        val first = bluePost("warned", labels = listOf("!warn"), text = "Hidden first revision").toNetworkPost("en")
        assertEquals(Strings.content_warning.get("en"), first.warning)
        assertTrue(first.coversText)
        val second = bluePost("warned", labels = listOf("!warn"), text = "Hidden new revision").toNetworkPost("en")
        assertEquals(first.id, second.id)
        assertNotEquals(first.revision, second.revision, "a reveal holds for one revision only")
        assertEquals(first.revision, bluePost("warned", labels = listOf("!warn"), text = "Hidden first revision").toNetworkPost("en").revision)
    }

    @Test
    fun anUnavailablePostOrQuoteOffersNoRevealAndAMediaLabelLeavesTheText() {
        val unavailable = Strings.content_unavailable.get("en")
        assertEquals("This post cannot be displayed.", unavailable)
        for (label in listOf("!hide", "!no-unauthenticated")) {
            val hidden = bluePost("hidden", labels = listOf(label), text = "Restricted body")
            val shown = hidden.toNetworkPost("en")
            assertEquals(listOf(TextRun.Plain(unavailable)), shown.text)
            assertNull(shown.warning, "nothing to reveal")
            val quoted = bluePost("outer", quote = hidden).toNetworkPost("en").quoted!!
            assertEquals(listOf(TextRun.Plain(unavailable)), quoted.text)
            assertNull(quoted.warning)
        }
        val media = bluePost("media-only", labels = listOf("nudity"), media = listOf(BlueskyMedia("https://cdn.example/p.jpg"))).toNetworkPost("en")
        assertEquals(listOf(TextRun.Plain("media-only")), media.text)
        assertFalse(media.coversText)
        assertNotNull(media.warning)
    }

    @Test
    fun aMediaLabelOnAPostThatQuotesOrLinksCoversTheQuoteAndCardWithTheMediaButNotTheText() {
        val quote = bluePost("quoted")
        val covered = bluePost("outer", labels = listOf("nudity"), quote = quote).toNetworkPost("en").coveringAttachments()!!
        assertTrue(covered.coversText, "one cover holds the quote back")
        assertEquals(emptyList(), covered.text, "the text is drawn over the cover")
        assertNotNull(covered.quoted)
        assertNull(bluePost("plain", quote = quote).toNetworkPost("en").coveringAttachments(), "no label, no cover")
        assertNull(bluePost("warned", labels = listOf("!warn"), quote = quote).toNetworkPost("en").coveringAttachments(), "!warn covers it all already")
        assertNull(bluePost("media-only", labels = listOf("nudity"), media = listOf(BlueskyMedia("https://cdn.example/p.jpg"))).toNetworkPost("en").coveringAttachments(), "media alone keeps the media cover")
        assertNull(bluePost("hidden", labels = listOf("!hide"), quote = quote).toNetworkPost("en").coveringAttachments())
    }

    @Test
    fun theFooterNamesEachActionWhenItsCountsAreHidden() {
        val post = bluePost("root", likes = 4).copy(replyCount = 2, repostCount = 3, quoteCount = 1)
        val hidden = footerCounts(post, liked = true, countsHidden = true)
        assertEquals(listOf("Thread", "Reposted by", "Quotes", "Remove like from this device"), hidden.map { it.label.get("en") })
        assertTrue(hidden.all { it.n == 0L })
        assertEquals(listOf(2L, 3L, 1L, 5L), footerCounts(post, liked = true, countsHidden = false).map { it.n }, "the device's like adds one")
        assertEquals("Like on this device", footerCounts(post, liked = false, countsHidden = false).last().label.get("en"))
    }

    @Test
    fun aMediaTileIsAnnouncedWithItsDescriptionUnlessItIsCovered() {
        val post = bluePost("alt", media = listOf(BlueskyMedia("https://cdn.example/p.jpg", alt = "A bright red bridge")))
        assertEquals("Maya Chen · A bright red bridge", BlueskyMediaTile(post).label("en"))
        val covered = BlueskyMediaTile(post.copy(labels = listOf("!no-unauthenticated")))
        assertEquals("Maya Chen · Content warning", covered.label("en"))
        assertNull(covered.alt, "no ALT badge either")
        assertEquals("Maya Chen · Media", BlueskyMediaTile(bluePost("bare", media = listOf(BlueskyMedia("https://cdn.example/q.jpg")))).label("en"))
    }
}
