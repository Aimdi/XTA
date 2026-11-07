package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.pixiv.FakePixivHttp
import com.aimdi.xtanative.networks.pixiv.PixivComment
import com.aimdi.xtanative.networks.pixiv.PixivUser
import com.aimdi.xtanative.networks.pixiv.answer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** XTA's pixiv_comments_store_test and the rules of pixiv_comments_screen_test that live outside Compose. */
class PixivCommentsListTest {
    private val spam = PixivUser(7, "Spam", "spam")
    private fun comment(id: Long, user: PixivUser? = PixivUser(1, "A", "a"), hasReplies: Boolean = false) = PixivComment(id, "Comment $id", user = user, hasReplies = hasReplies)

    private suspend fun PixivList<PixivComment>.settled(check: (PixivListState<PixivComment>) -> Boolean): List<Long> =
        withTimeout(5_000) { state.first(check).items.map { it.id } }

    @Test
    fun aMutedCommentAndEverythingByAMutedAuthorAreHidden() {
        val comments = listOf(comment(1), comment(2, spam), comment(3), comment(4, user = null))
        val mutes = PixivMutes(commentIds = setOf(3), authorIds = setOf(7))
        assertEquals(listOf(1L, 4L), comments.filter(mutes::shows).map { it.id })
        assertEquals(listOf(1L, 2L, 3L, 4L), comments.filter(PixivMutes()::shows).map { it.id })
    }

    @Test
    fun theMutesAreTakenAsEachPageLands(): Unit = runBlocking {
        var mutes = PixivMutes(commentIds = setOf(2), authorIds = setOf(7))
        val pages = mapOf<String?, Page<PixivComment>>(
            null to Page(listOf(comment(1), comment(2), comment(3, spam), comment(4, user = null)), "next-1"),
            "next-1" to Page(listOf(comment(5), comment(6)), null),
        )
        val list = PixivList<PixivComment>(CoroutineScope(coroutineContext), { it.id }, { pages.getValue(it) }) { it.filter(mutes::shows) }
        list.refresh()
        assertEquals(listOf(1L, 4L), list.settled { it.loaded })
        mutes = mutes.copy(commentIds = setOf(2, 6))
        list.more()
        assertEquals(listOf(1L, 4L, 5L), list.settled { !it.hasMore && !it.loadingMore })
    }

    @Test
    fun pagesTheMutesEmptyAreSkippedAndALastOneStillEndsTheList(): Unit = runBlocking {
        val mutes = PixivMutes(authorIds = setOf(7))
        val skipped = PixivList<PixivComment>(CoroutineScope(coroutineContext), { it.id }, { next ->
            if (next == null) Page(listOf(comment(1, spam)), "next-1") else Page(listOf(comment(2)), null)
        }) { it.filter(mutes::shows) }
        skipped.refresh()
        assertEquals(listOf(2L), skipped.settled { it.loaded })
        assertFalse(skipped.state.value.hasMore)

        val ending = PixivList<PixivComment>(CoroutineScope(coroutineContext), { it.id }, { next ->
            if (next == null) Page(listOf(comment(1)), "next-1") else Page(listOf(comment(2, spam)), null)
        }) { it.filter(mutes::shows) }
        ending.refresh()
        ending.settled { it.loaded }
        ending.more()
        assertEquals(listOf(1L), ending.settled { !it.hasMore && !it.loadingMore })
    }

    @Test
    fun theFirstLoadCountsAsLoadingNotAsAnEmptyList(): Unit = runBlocking {
        val list = PixivList<PixivComment>(CoroutineScope(coroutineContext), { it.id }, { Page(emptyList(), null) })
        assertTrue(list.state.value.waiting, "before anything arrived")
        list.refresh()
        assertTrue(list.state.value.waiting)
        list.settled { it.loaded }
        assertFalse(list.state.value.waiting)
    }

    @Test
    fun mutingTheCommentAThreadHangsOffLeavesTheThread() {
        val parent = comment(1, hasReplies = true)
        assertFalse(PixivMutes().leavesThread(parent))
        assertFalse(PixivMutes(commentIds = setOf(1)).leavesThread(null), "the comments of a work never leave")
        assertTrue(PixivMutes(commentIds = setOf(1)).leavesThread(parent))
        assertTrue(PixivMutes(authorIds = setOf(1)).leavesThread(parent))
    }

    @Test
    fun aCommentShownPastItsLinkNoteStaysShownInItsThread() = withRuntime { runtime ->
        assertFalse(1L in runtime.revealedComments.value)
        runtime.revealComment(1)
        assertTrue(1L in runtime.revealedComments.value, "the thread's screen reads the same set")
    }

    @Test
    fun theNovelTargetReadsTheNovelsCommentsAndReplies() {
        val http = FakePixivHttp { answer("""{"comments": [{"id": 1, "comment": "hi", "has_replies": true}]}""") }
        withRuntime(PixivTestHost(signedInSettings(), http)) { runtime ->
            val target = com.aimdi.xtanative.networks.pixiv.PixivCommentTarget.novel(55)
            runtime.comments.comments(target)
            runtime.comments.replies(target, 1)
            assertEquals(listOf("/v3/novel/comments?novel_id=55", "/v2/novel/comment/replies?comment_id=1"), http.reads.map { "${it.url.encodedPath}?${it.url.encodedQuery}" })
        }
    }
}
