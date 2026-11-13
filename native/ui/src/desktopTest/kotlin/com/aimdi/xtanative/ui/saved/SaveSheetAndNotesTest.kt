package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.NoteEditorState
import com.aimdi.xtanative.store.saved.NoteMedia
import com.aimdi.xtanative.store.saved.QuickPick
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaveSheetAndNotesTest {
    private val folders = (1..8).map { SavedFolder("f$it", "Folder $it", position = it) }

    @Test
    fun theChipsOpenWithTheLatestSavesFoldersAndShowSixUntilAll() {
        val library = SavedLibrary(listOf(savedX("1", folder = "f5"), savedX("2", folder = "f2")), folders)
        val order = SaveSheetView.openOrder(library)
        assertEquals(listOf("f5", "f2", "f1", "f3"), order.take(4))
        val chips = SaveSheetView().chips(library, order)
        assertEquals(6, chips.items.size)
        assertTrue(chips.hasMore)
        assertEquals(QuickPick.Section.RECENT, chips.section)
        val all = SaveSheetView().showingAll().chips(library, order)
        assertEquals(8, all.items.size)
        assertEquals(QuickPick.Section.ALL, all.section)
    }

    @Test
    fun aSearchCanMakeAFolderThatThenLeads() {
        val library = SavedLibrary(emptyList(), folders)
        val searching = SaveSheetView().search("japan")
        val none = searching.chips(library, emptyList())
        assertEquals(QuickPick.Section.NO_MATCH, none.section)
        assertTrue(none.canCreate)
        assertTrue(SaveSheetView.labelShown(none))
        val made = searching.created("new")
        assertEquals("", made.query)
        val withNew = SavedLibrary(emptyList(), folders + SavedFolder("new", "Japan", position = 9))
        assertEquals("new", made.chips(withNew, emptyList()).items.first().id)
        assertFalse(SaveSheetView().search("folder 1").chips(library, emptyList()).canCreate, "Folder 1 exists")
    }

    @Test
    fun eachNoteEditorKeepsItsOwnDraft() {
        val note = LocalPost("n1", "Hi", 1, 1)
        assertEquals("edit:n1", NoteRules.draftKey(NoteTarget.Note(editing = note)))
        assertEquals("reply:n1", NoteRules.draftKey(NoteTarget.Note(replyTo = note)))
        assertEquals("quote:42", NoteRules.draftKey(NoteTarget.Note(quotedId = "42")))
        assertEquals("new", NoteRules.draftKey(NoteTarget.Note()))
        assertEquals("saved:7", NoteRules.savedNoteKey("7"))
        assertEquals(NoteTitle.REPLY, NoteRules.title(NoteTarget.Note(replyTo = note)))
        assertEquals(NoteTitle.EDIT, NoteRules.title(NoteTarget.Text("kept", "k")))
        assertEquals(NoteTitle.NEW, NoteRules.title(NoteTarget.Text(null, "k")))
    }

    @Test
    fun aNoteSavesWithSomethingInItAndAnEditOnlyOnceChanged() {
        val empty = NoteEditorState("")
        val written = NoteEditorState("Hello")
        assertFalse(NoteRules.canSave(NoteTarget.Note(), empty, dirty = true))
        assertTrue(NoteRules.canSave(NoteTarget.Note(), written, dirty = true))
        assertFalse(NoteRules.canSave(NoteTarget.Note(editing = LocalPost("n", "Hello", 1, 1)), written, dirty = false))
        assertTrue(NoteRules.canSave(NoteTarget.Text(null, "k", allowUnchanged = true), empty, dirty = false))
        assertFalse(NoteRules.canSave(NoteTarget.Text(null, "k"), empty, dirty = false))
    }

    @Test
    fun closingAsksOnlyWhenSomethingWouldBeLost() {
        assertEquals(NoteClose.WAIT, NoteRules.onClose(NoteEditorState("x", saving = true), dirty = true))
        assertEquals(NoteClose.CLOSE, NoteRules.onClose(NoteEditorState("x"), dirty = false))
        assertEquals(NoteClose.CLOSE, NoteRules.onClose(NoteEditorState("x", saved = true), dirty = true))
        assertEquals(NoteClose.CONFIRM, NoteRules.onClose(NoteEditorState("x"), dirty = true))
    }

    @Test
    fun aNotesFilesAreItsPicturesThenTheRest() {
        val media = listOf(NoteMedia("1", "a.jpg", "image/jpeg"), NoteMedia("2", "b.mp4", "video/mp4"), NoteMedia("3", "c.png", "image/png"))
        val layout = NoteRules.layout(media)
        assertEquals(listOf("1", "3"), layout.images.map { it.id })
        assertFalse(layout.single)
        assertEquals(listOf("2"), layout.files.map { it.id })
        assertTrue(NoteRules.layout(media.take(1)).single)
        assertEquals(listOf(0, 12, 24, 24), listOf(0, 1, 2, 5).map(NoteRules::indent))
        assertEquals(0.5f, NoteRules.fullness("a".repeat(10_000)))
        assertTrue(NoteRules.replyOpensThread(2))
        assertFalse(NoteRules.replyOpensThread(0))
    }
}
