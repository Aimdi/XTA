package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.substack.SubstackComment
import com.aimdi.xtanative.networks.substack.SubstackCommentOrder
import com.aimdi.xtanative.networks.substack.SubstackNote
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.store.settings.PostsPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

/** XTA's substack_discussion_reader_test: the discussion's model, and what a note's row shows. */
class SubstackDiscussionTest {
    private val post = SubstackPost("42", "On noticing the small things", "small-things", "https://fieldnotes.substack.com", "Field Notes")
    private val comments = listOf(
        SubstackComment("root", "Do you keep a notebook on your walks?", "Alex"),
        SubstackComment("child", "Yes. It helps me remember the light and colours.", "Maya", parentId = "root", depth = 1),
        SubstackComment("nested", "That is a lovely idea. I will try it this weekend.", "Robin", parentId = "child", depth = 2),
        SubstackComment("sibling", "Thank you for this thoughtful piece.", "Sam"),
    )
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterTest
    fun end() = scope.cancel()

    private suspend fun until(check: () -> Boolean) = withTimeout(5_000) { while (!check()) delay(5) }

    @Test
    fun aFailedRefreshKeepsTheCommentsAndControlsAndARetryForgetsRemovedFolds(): Unit = runBlocking {
        var fail = false
        var answer = comments
        val model = CommentsModel({ if (fail) error("offline") else answer }, post, scope)
        until { model.state.value.comments.isNotEmpty() && !model.state.value.loading }
        model.expandAll(false)
        model.sort(SubstackCommentOrder.OLDEST)
        assertEquals(2, model.state.value.rows.size)
        fail = true
        model.refresh()
        until { !model.state.value.loading }
        assertEquals(comments, model.state.value.comments)
        assertNotNull(model.state.value.error)
        assertEquals(SubstackCommentOrder.OLDEST, model.state.value.order)
        model.search("weekend")
        assertTrue(model.state.value.collapsed.isEmpty())
        assertEquals(3, model.state.value.rows.size)
        model.expandAll(false)
        fail = false
        answer = listOf(SubstackComment("fresh", "fresh"))
        model.refresh()
        until { !model.state.value.loading }
        assertNull(model.state.value.error)
        assertTrue(model.state.value.collapsed.isEmpty(), "folds of comments that are gone are forgotten")
    }

    @Test
    fun theNewerReadWinsAndAGoneScreenIgnoresLateAnswers(): Unit = runBlocking {
        val answers = ArrayDeque<CompletableDeferred<List<SubstackComment>>>()
        val own = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val model = CommentsModel({ CompletableDeferred<List<SubstackComment>>().also { answers.addLast(it) }.await() }, post, own)
        until { answers.size == 1 }
        val old = answers.removeFirst()
        model.refresh()
        until { answers.size == 1 }
        answers.removeFirst().complete(listOf(SubstackComment("new", "new")))
        old.complete(listOf(SubstackComment("old", "old")))
        until { !model.state.value.loading }
        delay(50)
        assertEquals("new", model.state.value.comments.single().id)
        model.refresh()
        until { answers.size == 1 }
        own.cancel()
        answers.removeFirst().complete(listOf(SubstackComment("late", "late")))
        delay(50)
        assertEquals("new", model.state.value.comments.single().id)
    }

    @Test
    fun aNoteWithoutItsAuthorIsByUnknownAndItsPictureIsItsMedia() {
        val drawn = noteAsPost(SubstackNote("note", "A quiet place to read.", imageUrl = "https://review.example/note.png"), "Unknown")
        assertEquals("Unknown", drawn.authorName)
        assertEquals(listOf("https://review.example/note.png"), drawn.media.map { it.imageUrl })
    }

    @Test
    fun aNotesHeartsAreCountedExceptInCalmOrZenMode() {
        val note = SubstackNote("note", "A quiet place to read.", reactionCount = 1200)
        assertEquals(1200L, noteLikes(note, Settings()))
        assertNull(noteLikes(note, Settings().with(PostsPrefs.calmMode, true)))
        assertNull(noteLikes(note, Settings().with(PostsPrefs.zenMode, true)))
        assertNull(noteLikes(note.copy(reactionCount = null), Settings()))
    }
}
