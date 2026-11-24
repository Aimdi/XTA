package com.aimdi.xtanative.store.saved

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PutBackTest {
    private var time = 1_000L
    private var ids = 0

    private fun content(id: String) = json("""{"id_str":"$id","full_text":"post $id"}""")

    @Test
    fun itemsGoBackBetweenTheSameNeighboursAndThoseAddedSinceKeepTheirPlaces() {
        val before = listOf("a", "b", "c", "d", "e")
        val taken = PutBack.taken(before, { it == "b" || it == "c" || it == "e" }, { it })!!
        assertEquals(listOf("b", "c", "e"), taken.items)
        val now = listOf("new", "a", "d")
        assertEquals(listOf("new", "a", "b", "c", "d", "e"), PutBack.into(now, taken) { it })
        assertEquals(listOf("b", "c", "e"), PutBack.into(emptyList(), taken) { it }, "with every neighbour gone, in their own order")
        assertNull(PutBack.into(listOf("a", "c"), taken) { it }, "one there again since: nothing is put back")
        assertNull(PutBack.taken(before, { it == "z" }, { it }), "nothing taken")
    }

    @Test
    fun removedSavedPostsComeBackWithTheirContentFolderNoteAndTime() = runBlocking {
        val dir = tempStore()
        val saved = SavedStore(dir, clock = { time++ }, newId = { "folder-${ids++}" })
        val art = saved.createFolder("Art")!!
        listOf("1", "2", "3").forEach { saved.file(it, "u$it", content(it), folderId = if (it == "2") art.id else null) }
        saved.setNote("2", "keep")
        val before = saved.library()

        val taken = saved.removeTaking(listOf("2", "3", "nope"))!!
        assertEquals(listOf("1"), saved.library().posts.map { it.id })
        assertNull(SavedStore(dir).contentOf("2"), "what was kept of it is gone from the disk")

        saved.file("4", null, null)
        assertTrue(saved.putBack(taken))
        assertEquals(listOf("4") + before.posts.map { it.id }, saved.library().posts.map { it.id })
        assertEquals(before.posts, saved.library().posts.drop(1))
        assertEquals(before.posts, SavedStore(dir).library().posts.drop(1), "on the disk too, content and all")
        assertFalse(saved.putBack(taken), "twice: they are there")
        assertNull(saved.removeTaking(listOf("nope")))
    }

    @Test
    fun aRemovedPostWhoseFolderWasDeletedSinceComesBackUnfiled() = runBlocking {
        val saved = SavedStore(tempStore(), clock = { time++ }, newId = { "folder-${ids++}" })
        val art = saved.createFolder("Art")!!
        saved.file("1", null, null, folderId = art.id)
        val taken = saved.removeTaking(listOf("1"))!!
        saved.deleteFolder(art.id)
        assertTrue(saved.putBack(taken))
        assertNull(saved.library().folderOf("1"))
    }

    @Test
    fun aDeletedFolderComesBackInItsPlaceWithItsPostsStillUnfiled() = runBlocking {
        val dir = tempStore()
        val saved = SavedStore(dir, clock = { time++ }, newId = { "folder-${ids++}" })
        val art = saved.createFolder("Art", autoDownload = true)!!
        val news = saved.createFolder("News")!!
        listOf("1", "2", "3").forEach { saved.file(it, null, null, folderId = art.id) }
        val folders = saved.library().folders

        val taken = saved.deleteFolderTaking(art.id)!!
        assertEquals(listOf(news), saved.library().folders)
        saved.setFolder(listOf("2"), news.id)
        saved.remove("3")

        assertTrue(saved.putBack(taken))
        assertEquals(folders, SavedLibrary.ordered(saved.library().folders))
        assertEquals(listOf(art.id, news.id), SavedStore(dir).library().let { lib -> listOf("1", "2").map { lib.folderOf(it) } }, "a post filed elsewhere since stays there")
        assertFalse(saved.putBack(taken))
        assertNull(saved.deleteFolderTaking("nope"))
    }

    @Test
    fun anUnlikeComesBackAsLikedThen() = runBlocking {
        val dir = tempStore()
        val likes = LikesStore(dir, clock = { time++ })
        listOf("1", "2", "3").forEach { likes.like(it, "u$it", content(it)) }
        val before = likes.likes()
        val taken = likes.unlikeTaking("2")!!
        assertEquals(listOf("3", "1"), likes.likes().map { it.id })
        assertTrue(likes.putBack(taken))
        assertEquals(before, LikesStore(dir).likes())
        assertFalse(likes.putBack(taken))
        assertNull(likes.unlikeTaking("nope"))
    }

    @Test
    fun aDeletedNoteComesBackWithItsFilesAndItsRepliesAnswerItAgain() = runBlocking {
        val dir = tempStore()
        val notes = LocalPostsStore(dir, clock = { time++ }, newId = { "note-${ids++}" })
        notes.writeMedia("n1", "photo", byteArrayOf(1, 2, 3))
        val first = notes.save("first", listOf(NoteMedia("photo", "p.png", "image/png")), id = "n1")!!
        notes.save("reply", replyTo = first.id)
        val before = notes.notes()

        val taken = notes.deleteTaking(first.id)!!
        assertFalse(notes.mediaFile(first.id, "photo").exists())
        assertTrue(notes.putBack(taken))
        assertEquals(before, notes.notes())
        assertEquals(before, LocalPostsStore(dir).notes())
        assertContentEquals(byteArrayOf(1, 2, 3), notes.mediaFile(first.id, "photo").readBytes())
        assertFalse(notes.putBack(taken))
        assertNull(notes.deleteTaking("nope"))
    }
}
