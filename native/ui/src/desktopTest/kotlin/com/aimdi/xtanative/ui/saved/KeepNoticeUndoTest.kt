package com.aimdi.xtanative.ui.saved

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class KeepNoticeUndoTest {
    private fun keepNow(write: suspend () -> Unit) = runBlocking { write() }

    @Test
    fun theShownUndoRunsOnceAndTheNoticeGoes() {
        var undone = 0
        SavedUndo.offer("taken") { undone++; true }
        assertTrue(KeepNotices.undo(KeepNotices.shown.value, ::keepNow))
        assertEquals(1, undone)
        assertNull(KeepNotices.shown.value)
        assertFalse(KeepNotices.undo(KeepNotices.shown.value, ::keepNow), "nothing shown: Ctrl+Z goes on")
        assertEquals(1, undone)
    }

    @Test
    fun anUndoThatCannotBeMadeSaysSoAndANoticeWithoutOneTakesNoKey() {
        SavedUndo.offer("taken") { false }
        assertTrue(KeepNotices.undo(KeepNotices.shown.value, ::keepNow))
        assertEquals(KeepNotice.UndoFailed, KeepNotices.shown.value?.notice)
        assertFalse(KeepNotices.undo(KeepNotices.shown.value, ::keepNow))

        SavedUndo.offer<String>(null) { error("nothing was taken") }
        assertEquals(KeepNotice.UndoFailed, KeepNotices.shown.value?.notice, "a removal that took nothing offers no undo")
    }

    @Test
    fun whatAnUndoHeldIsLetGoOnlyWhenTheNoticeGoesWithoutIt() {
        var discarded = 0
        SavedUndo.offer("note", { true }, discard = { discarded++ })
        assertTrue(KeepNotices.undo(KeepNotices.shown.value, ::keepNow))
        assertEquals(0, discarded, "undone: the files went back, nothing to let go")

        SavedUndo.offer("note", { true }, discard = { discarded++ })
        KeepNotices.hide(KeepNotices.shown.value!!)
        assertEquals(1, discarded, "timed out")

        SavedUndo.offer("note", { true }, discard = { discarded++ })
        KeepNotices.show(KeepNotice.MediaSaved)
        assertEquals(2, discarded, "replaced by another notice")
        KeepNotices.hide(KeepNotices.shown.value!!)
        assertEquals(2, discarded)
    }
}
