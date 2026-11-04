package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.store.saved.SavedStore
import com.aimdi.xtanative.store.settings.SavedPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import com.aimdi.xtanative.ui.saved.Keepable
import com.aimdi.xtanative.ui.saved.json
import com.aimdi.xtanative.ui.saved.tempStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlinx.coroutines.runBlocking

class PluginNotesTest {
    private val dir = tempStore()
    private val saved = SavedStore(dir)
    private val settings = SettingsStore(dir)
    private val notes = PluginNotes(saved, settings)
    private val post = Keepable("mastodon:https://mastodon.social/@a/1", "a@mastodon.social", json("""{"xtaPlugin":"mastodon","post":{"id":"1"}}"""))

    @Test
    fun aNoteSavesAPostNotSavedYetWhereAPlainTapFiles(): Unit = runBlocking {
        val folder = saved.createFolder("Later")!!
        settings.set(SavedPrefs.stickyFolderEnabled, true)
        settings.set(SavedPrefs.stickyFolderId, folder.id)
        notes.keep(post, "Read again")
        val kept = saved.library().post(post.id)!!
        assertEquals("Read again", kept.note)
        assertEquals(folder.id, kept.folderId)
        assertEquals(post.authorId, kept.authorId)
        assertEquals("Read again", notes.noteOf(post.id))
    }

    @Test
    fun aSavedPostKeepsItsFolderAndABlankNoteRemovesTheNote(): Unit = runBlocking {
        val folder = saved.createFolder("Mine")!!
        saved.file(post.id, post.authorId, post.content, folder.id)
        notes.keep(post, "  Kept  ")
        assertEquals("Kept", notes.noteOf(post.id))
        notes.keep(post, " ")
        assertNull(notes.noteOf(post.id))
        assertEquals(folder.id, saved.library().folderOf(post.id))
    }

    @Test
    fun aPostNeverSavedHasNoNote(): Unit = runBlocking {
        assertNull(notes.noteOf(post.id))
        assertFalse(saved.library().isSaved(post.id))
    }
}
