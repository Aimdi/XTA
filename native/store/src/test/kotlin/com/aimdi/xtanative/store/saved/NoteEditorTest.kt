package com.aimdi.xtanative.store.saved

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.delay


class NoteEditorTest {
    private val photo = NoteMedia("photo", "photo.jpg", "image/jpeg")

    @Test
    fun leavingLosesNothingOnlyWhenTheTextAndFilesAreAsOpened() = timed {
        val editor = NoteEditor(body = "Original", media = listOf(photo))
        assertFalse(editor.dirty)
        editor.setBody("Changed")
        assertTrue(editor.dirty)
        editor.setBody("Original")
        assertFalse(editor.dirty)
        editor.removeMedia("photo")
        assertTrue(editor.dirty)
        assertEquals(listOf(photo), editor.initialMedia)
        editor.attach { photo }
        assertFalse(editor.dirty)
    }

    @Test
    fun aFailedSaveKeepsTheTextAndFilesToTryAgain() = timed {
        val editor = NoteEditor(media = listOf(photo))
        editor.setBody("Keep this draft")
        assertFalse(editor.save { error("disk full") })
        assertTrue(editor.state.value.saveFailed)
        assertFalse(editor.state.value.saved)
        assertFalse(editor.state.value.busy)
        assertEquals("Keep this draft", editor.state.value.body)
        assertEquals(listOf(photo), editor.state.value.media)
        assertTrue(editor.save { })
        assertFalse(editor.state.value.saveFailed)
        assertTrue(editor.state.value.saved)
    }

    @Test
    fun aSaveUnderWayHoldsOffASecondSaveAndAnyChangeOfFiles() = timed {
        val editor = NoteEditor(body = "Note", media = listOf(photo))
        val pending = CompletableDeferred<Unit>()
        val saving = async { editor.save { pending.await() } }
        waitUntil { editor.state.value.saving }
        var calls = 0
        assertFalse(editor.save { calls++ })
        editor.attach { calls++; photo }
        editor.removeMedia("photo")
        assertEquals(0, calls)
        assertEquals(listOf(photo), editor.state.value.media)
        pending.complete(Unit)
        assertTrue(saving.await())
    }

    @Test
    fun aCancelledOrFailedPickKeepsTheFilesThereWere() = timed {
        val editor = NoteEditor(media = listOf(photo))
        editor.attach { null }
        assertFalse(editor.dirty)
        editor.attach { error("unreadable file") }
        assertTrue(editor.state.value.attachFailed)
        assertFalse(editor.state.value.busy)
        assertEquals(listOf(photo), editor.state.value.media)
    }

    @Test
    fun aDraftBringsBackTheTextTheFilesAndTheNoteItWasFor() = timed {
        val dir = tempStore()
        val drafts = NoteDrafts(dir)
        val first = NoteEditor(draftKey = "new", postId = "original", drafts = drafts)
        assertTrue(first.state.value.restoring)
        first.restoreDraft()
        first.setBody("a thought")
        first.attach { NoteMedia("image", "image.png", "image/png") }
        first.persistDraft()
        first.close()

        val next = NoteEditor(draftKey = "new", postId = "different", drafts = NoteDrafts(dir))
        next.restoreDraft()
        assertEquals("a thought", next.state.value.body)
        assertEquals("image", next.state.value.media.single().id)
        assertEquals("original", next.postId)
        assertTrue(next.state.value.recovered)
        assertFalse(next.save { error("disk full") })
        assertNotNull(NoteDrafts(dir).read("new"))
        assertTrue(next.save { })
        assertNull(NoteDrafts(dir).read("new"))
    }

    @Test
    fun discardingDropsOnlyItsOwnDraftAndNoLateWriteBringsItBack() = timed {
        val drafts = NoteDrafts(tempStore())
        drafts.write("saved:other", NoteDraft("other"))
        val editor = NoteEditor(draftKey = "saved:one", drafts = drafts, scope = this)
        editor.restoreDraft()
        editor.setBody("discard me")
        assertTrue(editor.discard())
        editor.persistDraft()
        assertNull(drafts.read("saved:one"))
        assertNotNull(drafts.read("saved:other"))
    }

    @Test
    fun typingKeepsTheDraftAsItGoes() = timed {
        val drafts = NoteDrafts(tempStore())
        val editor = NoteEditor(draftKey = "typing", drafts = drafts, scope = this)
        editor.restoreDraft()
        editor.setBody("half a thought")
        var kept: NoteDraft? = null
        val end = System.currentTimeMillis() + 30_000
        while (kept?.body != "half a thought" && System.currentTimeMillis() < end) {
            delay(5)
            kept = drafts.read("typing")
        }
        assertEquals(NoteDraft("half a thought"), kept)
    }
}
