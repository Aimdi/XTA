package com.aimdi.xtanative.store.saved

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SavedStoreTest {
    private val dir = tempStore()
    private var time = 1_000L
    private var ids = 0
    private fun store() = SavedStore(dir, clock = { time++ }, newId = { "folder-${ids++}" })

    private val post = json("""{"id_str":"1","full_text":"first","user":{"id_str":"u1","screen_name":"a","name":"A"}}""")

    @Test
    fun savingPutsThePostFirstAndFilingItAgainOnlyMovesIt() = runBlocking {
        val saved = store()
        saved.file("1", "u1", post)
        saved.file("2", "u2", json("""{"id_str":"2","full_text":"second"}"""))
        assertEquals(listOf("2", "1"), saved.library().posts.map { it.id })

        val moved = saved.file("1", "u1", json("""{"id_str":"1","full_text":"changed since"}"""), folderId = "art")
        assertEquals("art", moved.folderId)
        assertEquals(listOf("2", "1"), saved.library().posts.map { it.id }, "a saved post keeps its place")
        assertEquals(post, saved.library().post("1")?.content, "and what was kept of it")
        assertTrue(saved.library().isSaved("1"))
        assertEquals("art", saved.library().folderOf("1"))
        assertNull(saved.library().folderOf("2"))
    }

    @Test
    fun postsMoveTogetherAndANoteIsTrimmedOrRemoved() = runBlocking {
        val saved = store()
        listOf("1", "2", "3").forEach { saved.file(it, null, null) }
        saved.setFolder(listOf("1", "3"), "later")
        assertEquals(2, saved.library().countIn("later"))
        saved.setFolder(listOf("1"), null)
        assertNull(saved.library().folderOf("1"))

        saved.setNote("2", "  read again  ")
        assertEquals("read again", saved.library().post("2")?.note)
        saved.setNote("2", "   ")
        assertNull(saved.library().post("2")?.note)

        saved.remove(listOf("1", "2"))
        assertEquals(listOf("3"), saved.library().posts.map { it.id })
    }

    @Test
    fun foldersAreMadeInOrderAndADeletedFoldersPostsStaySavedUnfiled() = runBlocking {
        val saved = store()
        assertNull(saved.createFolder("   "), "a blank name makes no folder")
        val art = saved.createFolder(" Art ", autoDownload = true)!!
        val news = saved.createFolder("News")!!
        assertEquals(listOf("Art" to 0, "News" to 1), saved.library().folders.map { it.name to it.position })
        assertTrue(art.autoDownload)

        saved.updateFolder(news.id, "Daily news", autoUpload = true)
        assertEquals(SavedFolder(news.id, "Daily news", 1, news.createdAt, autoDownload = false, autoUpload = true), saved.library().folder(news.id))
        saved.updateFolder(news.id, "  ")
        assertEquals("Daily news", saved.library().folder(news.id)?.name)

        saved.file("1", null, null, art.id)
        saved.deleteFolder(art.id)
        assertNull(saved.library().folder(art.id))
        assertTrue(saved.library().isSaved("1"))
        assertNull(saved.library().folderOf("1"))
    }

    @Test
    fun theLibraryIsOnDiskForTheNextLaunch() = runBlocking {
        val first = store()
        val folder = first.createFolder("Art")!!
        first.file("1", "u1", post, folder.id)
        first.setNote("1", "mine")
        val again = store().library()
        assertEquals(first.library(), again)
        assertEquals("first", again.post("1")?.about?.xPost?.text)
    }

    @Test
    fun anUnreadableLibraryIsKeptAsideNotWrittenOver() = runBlocking {
        val file = File(dir.root, "saved/library.json").apply { parentFile.mkdirs(); writeText("{broken") }
        val saved = store()
        assertTrue(saved.library().posts.isEmpty())
        saved.file("1", null, null)
        assertEquals("{broken", File(dir.root, "saved/library.json.unreadable").readText())
        assertTrue(file.readText().contains("\"1\""))
    }

    @Test
    fun aBackupReplacesTheWholeLibraryWithFoldersInXtasOrder() = runBlocking {
        val saved = store()
        saved.file("old", null, null)
        val library = SavedLibrary(
            posts = listOf(SavedPost("b", null, null, "f2", savedAt = 2), SavedPost("a", null, null, savedAt = 1)),
            folders = listOf(SavedFolder("f2", "Second", 1, 20), SavedFolder("f1", "First", 0, 10), SavedFolder("f3", "Same place, newer", 1, 30)),
        )
        saved.replace(library)
        assertEquals(listOf("b", "a"), saved.content().posts.map { it.id })
        assertEquals(listOf("f1", "f2", "f3"), saved.content().folders.map { it.id })
        assertFalse(saved.library().isSaved("old"))
    }
}
