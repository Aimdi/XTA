package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class LocalPostsTest {
    @Test
    fun aBlankNoteIsRefusedAndALongOneIsCutNotThrown() {
        assertNull(LocalPosts.normalizeBody(""))
        assertNull(LocalPosts.normalizeBody("   \n\t"))
        assertEquals("hello", LocalPosts.normalizeBody("  hello  "))
        assertEquals(LocalPosts.MAX_LENGTH, LocalPosts.normalizeBody("a".repeat(LocalPosts.MAX_LENGTH + 12))?.length)
        assertFalse(LocalPosts.hasContent("  ", emptyList()))
        assertTrue(LocalPosts.hasContent("", listOf(NoteMedia("m1", "pic.jpg", "image/jpeg"))))
    }

    @Test
    fun aSearchIgnoresCaseAndFindsFileNames() {
        assertTrue(LocalPosts.matchesQuery("anything", ""))
        assertTrue(LocalPosts.matchesQuery("Hello Nextcloud", "next"))
        assertFalse(LocalPosts.matchesQuery("Hello Nextcloud", "x.com"))
        assertTrue(LocalPosts.recordMatches(LocalPost("n", "", 0, 0, listOf(NoteMedia("m", "Holiday.JPG", "image/jpeg"))), "holiday"))
    }

    @Test
    fun aFileTypeComesFromThePickerElseFromItsExtension() {
        assertEquals("image/png", LocalPosts.inferMime("x.bin", "image/png"))
        assertEquals("video/mp4", LocalPosts.inferMime("clip.mp4", null))
        assertEquals("image/jpeg", LocalPosts.inferMime("pic.JPEG", ""))
        assertEquals("application/octet-stream", LocalPosts.inferMime("data", "application/octet-stream"))
    }

    private fun note(id: String, reply: String? = null, at: Long = id.hashCode().toLong()) = LocalPost(id, id, at, at, replyTo = reply)

    @Test
    fun repliesMakeThreadsWalkedOldestSiblingFirst() {
        assertEquals(listOf("a", "c"), LocalPosts.roots(listOf(note("a"), note("b", "a"), note("c"))).map { it.id })
        assertEquals("b", LocalPosts.roots(listOf(note("b", "missing"))).single().id)
        val family = listOf(note("a"), note("b", "a"), note("c", "b"), note("d", "a"))
        assertEquals(2, LocalPosts.directReplyCount(family, "a"))
        assertEquals(1, LocalPosts.directReplyCount(family, "b"))
        val thread = LocalPosts.thread(listOf(note("a", at = 1), note("c", "a", 3), note("b", "a", 2), note("d", "b", 3)), "a")
        assertEquals(listOf("a", "b", "d", "c"), thread.map { it.post.id })
        assertEquals(listOf(0, 1, 2, 1), thread.map { it.depth })
        assertEquals("a", LocalPosts.threadRootId(family, "c"))
        assertEquals("a", LocalPosts.threadRootId(family, "a"))
    }

    @Test
    fun aLoopOfRepliesDoesNotHangTheWalk() {
        val loop = listOf(note("a", "b"), note("b", "a"))
        assertEquals(setOf("a", "b"), LocalPosts.thread(loop, "a").map { it.post.id }.toSet())
        assertEquals("a", LocalPosts.threadRootId(loop, "a"))
    }

    private val dir = tempStore()
    private var time = 100L
    private var ids = 0
    private fun store() = LocalPostsStore(dir, clock = { time++ }, newId = { "note-${ids++}" })

    @Test
    fun aRewriteKeepsWhenItWasWrittenWhatItQuotesAndWhatItAnswers() = runBlocking {
        val notes = store()
        assertNull(notes.save("   "))
        val first = notes.save("first", quotedId = "t1", quoted = json("""{"id_str":"t1"}"""), replyTo = "root")!!
        val second = notes.save("second")!!
        assertEquals(listOf(second.id, first.id), notes.notes().map { it.id })

        val rewritten = notes.save("  first, again ", id = first.id)!!
        assertEquals("first, again", rewritten.body)
        assertEquals(first.createdAt, rewritten.createdAt)
        assertTrue(rewritten.updatedAt > first.updatedAt)
        assertEquals("t1", rewritten.quotedId)
        assertEquals("root", rewritten.replyTo)
        assertEquals(listOf(second.id, first.id), store().notes().map { it.id }, "a rewrite keeps its place, on disk too")
    }

    @Test
    fun filesLeaveWithTheirNoteAndWhenANoteDropsThem() = runBlocking {
        val notes = store()
        notes.writeMedia("n1", "photo", byteArrayOf(1, 2, 3))
        notes.writeMedia("n1", "clip", byteArrayOf(4))
        val photo = NoteMedia("photo", "photo.jpg", "image/jpeg")
        notes.save("with files", listOf(photo, NoteMedia("clip", "clip.mp4", "video/mp4")), id = "n1")
        notes.save("one file left", listOf(photo), id = "n1")
        assertFalse(notes.mediaFile("n1", "clip").exists())
        assertTrue(notes.mediaFile("n1", "photo").exists())
        notes.delete("n1")
        assertFalse(notes.mediaFile("n1", "photo").exists())
        assertTrue(notes.notes().isEmpty())
    }

    @Test
    fun aBackupCarriesTheFilesBytesAndRestoringMakesThemFilesAgain() = runBlocking {
        val notes = store()
        notes.writeMedia("n1", "photo", byteArrayOf(9, 8, 7))
        notes.save("photo", listOf(NoteMedia("photo", "a.jpg", "image/jpeg")), id = "n1")
        val backup = notes.content()
        assertEquals("CQgH", backup.single().media.single().data)

        val elsewhere = LocalPostsStore(tempStore())
        elsewhere.replace(backup + LocalPost("bad", "broken file", 1, 1, listOf(NoteMedia("x", "x.jpg", "image/jpeg", data = "%%%"))))
        assertContentEquals(byteArrayOf(9, 8, 7), elsewhere.mediaFile("n1", "photo").readBytes())
        assertNull(elsewhere.notes().first { it.id == "n1" }.media.single().data, "bytes live in files, not in the list")
        assertEquals(2, elsewhere.notes().size, "a damaged file does not stop the import")
    }

    @Test
    fun anIdFromABackupCannotLeaveTheNotesFolder() = runBlocking {
        val notes = store()
        val file = notes.mediaFile("../../escape", "../x")
        assertTrue(file.canonicalPath.startsWith(File(dir.root, "notes-media").canonicalPath))
    }

    @Test
    fun anXtaNoteRowRoundTripsWithItsFiles() {
        val note = LocalPost("n2", "photo", 1_787_918_400_000, 1_787_918_400_000, listOf(NoteMedia("m2", "a.jpg", "image/jpeg", "Zm9v")), "t1", json("""{"id_str":"t1"}"""), "n1")
        val row = XtaRows.row(note)
        assertFalse(J(row)["media_json"].string!!.contains("Zm9v"), "bytes travel only in media")
        assertEquals(note, XtaRows.localPost(J(row), now = 0))
        val legacy = XtaRows.localPost(J(json("""{"id":"old","body":"plain","created_at":"2026-08-28T12:00:00.000Z","updated_at":"2026-08-28T12:00:00.000Z"}""")), now = 0)!!
        assertTrue(legacy.media.isEmpty())
        assertNull(legacy.quotedId)
        assertNull(legacy.replyTo)
        assertEquals(1_787_918_400_000, legacy.createdAt)
    }
}
