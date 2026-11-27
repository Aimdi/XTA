package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreDir
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/** How Saved and the likes are kept on the disk: one post's worth of writing per change, and nothing lost on the way there. */
class KeptPostsTest {
    private val dir = tempStore()
    private val folder = File(dir.root, "saved")
    private var time = 5_000L
    private var ids = 0

    private fun saved(at: StoreDir = dir) = SavedStore(at, clock = { time++ }, newId = { "folder-${ids++}" })
    private fun likes(at: StoreDir = dir) = LikesStore(at) { time++ }

    private fun content(id: String, text: String = "post $id") =
        json("""{"id_str":"$id","full_text":"$text","user":{"id_str":"u$id","screen_name":"s$id","name":"N $id"},"n":1.50,"big":12345678901234567890}""")

    /** A library as an earlier build wrote it: every kind of row it kept. */
    private val oldLibrary = SavedLibrary(
        posts = listOf(
            SavedPost("30", "u30", content("30"), "f1", "a note\nof two lines", 300),
            SavedPost("mastodon:https://example.social/@a/1", "a@example.social", content("m1"), null, null, 200),
            SavedPost("20", null, null, "gone-folder", "noted, nothing kept", 100),
            SavedPost("10", "u10", content("10", "quote \\\" and \\\\ slash"), null, null, 50),
        ),
        folders = listOf(SavedFolder("f1", "Art", 0, 1, autoDownload = true), SavedFolder("f2", "Later", 1, 2, autoUpload = true)),
    )

    private val oldLikes = listOf(
        LikedPost("3", "u3", content("3"), 30),
        LikedPost("reddit:t3_abc", "r/kotlin", content("r"), 20),
        LikedPost("1", null, null, 10),
    )

    private fun writeOld(library: SavedLibrary? = oldLibrary, liked: List<LikedPost>? = oldLikes) {
        folder.mkdirs()
        library?.let { File(folder, "library.json").writeText(SavedCodec.library(it).toString()) }
        liked?.let { File(folder, "likes.json").writeText(SavedCodec.likes(it).toString()) }
    }

    /** Every file under `saved/` with its bytes. */
    private fun files(): Map<String, String> = folder.walkTopDown().filter { it.isFile }.associate { it.relativeTo(folder).path to it.readText() }

    private fun written(before: Map<String, String>, after: Map<String, String>): Map<String, Int> =
        after.filter { before[it.key] != it.value }.mapValues { (name, text) ->
            if (text.startsWith(before[name].orEmpty())) text.length - before[name].orEmpty().length else text.length
        }

    @Test
    fun theOldSingleFilesBecomeAnIndexAndAFilePerPostWithNothingChanged() = runBlocking {
        writeOld()
        val oldLibraryText = File(folder, "library.json").readText()
        val oldLikesText = File(folder, "likes.json").readText()

        val library = saved().library()
        assertEquals(oldLibrary, library, "posts in their order, with content, notes and folders")
        assertEquals(oldLikes, likes().likes())
        assertEquals(oldLibraryText, SavedCodec.library(saved().content()).toString(), "what a backup is made from, byte for byte")
        assertEquals(oldLikesText, SavedCodec.likes(likes().content()).toString())
        assertEquals(XtaRows.run { oldLibrary.posts.map { row(it) } }, XtaRows.run { saved().content().posts.map { row(it) } }, "the backup's own rows")
        assertEquals(XtaRows.run { oldLikes.map { row(it) } }, XtaRows.run { likes().content().map { row(it) } })

        assertFalse(File(folder, "library.json").exists(), "gone once the new files are written")
        assertFalse(File(folder, "likes.json").exists())
        assertTrue(File(folder, "library.index.json").isFile)
        assertEquals(setOf("30.json", "10.json"), File(folder, "library").list()!!.filterNot { it.startsWith("x") }.toSet())
        assertEquals(3, File(folder, "library").list()!!.size, "one file per post with content, none for the post without")
        assertFalse(File(folder, "library.index.json").readText().contains("full_text"), "the index holds no content")

        val index = saved().index()
        assertEquals(oldLibrary.posts.map { it.copy(content = null) }, index.posts)
        assertEquals(oldLibrary.folders, index.folders)
        assertEquals("a note\nof two lines", index.post("30")?.note)
        assertEquals("gone-folder", index.folderOf("20"))
        assertEquals(1, index.countIn("f1"))
        assertEquals(oldLikes.map { it.copy(content = null) }, likes().index())
        assertEquals(content("m1"), saved().contentOf("mastodon:https://example.social/@a/1"))
        assertNull(saved().contentOf("20"))
        assertNull(saved().contentOf("never saved"))
    }

    @Test
    fun oneChangeWritesAboutOnePostHoweverManyAreKept() = runBlocking {
        val many = (1..400).map { SavedPost("$it", "u$it", content("$it", "x".repeat(2_000)), null, null, it.toLong()) }
        writeOld(SavedLibrary(many), many.map { LikedPost(it.id, it.authorId, it.content, it.savedAt) })
        val saved = saved()
        val likes = likes()
        saved.index()
        likes.index()
        val whole = files().values.sumOf { it.length }
        assertTrue(whole > 1_600_000, "the library is large: $whole")

        suspend fun change(what: String, limit: Int, only: Set<String>, act: suspend () -> Unit) {
            val before = files()
            act()
            val after = files()
            val changed = written(before, after)
            assertEquals(only, changed.keys, what)
            assertTrue(changed.values.sum() <= limit, "$what wrote ${changed.values.sum()} bytes of a $whole byte library")
        }

        val post = content("9001", "y".repeat(2_000))
        val size = post.toString().length
        change("the first like after launch", size + 400, setOf("likes/9001.json", "likes.log")) { likes.like("9001", "u9001", post) }
        change("a like", size + 300, setOf("likes/9002.json", "likes.log")) { likes.like("9002", "u9002", post) }
        change("liking again", size + 300, setOf("likes.log")) { likes.like("9001", "u9001", post) }
        change("unliking", 100, setOf("likes.log")) { likes.unlike("9002") }
        assertFalse(File(folder, "likes/9002.json").exists())
        change("a save", size + 400, setOf("library/9001.json", "library.log")) { saved.file("9001", "u9001", post) }
        change("a note", 300, setOf("library.log")) { saved.setNote("7", "remember this") }
        change("a move", 400, setOf("library.log")) { saved.setFolder(listOf("7", "8"), "f1") }
        change("a new folder", 300, setOf("library.log")) { saved.createFolder("Art") }
        change("filing a saved post elsewhere", 300, setOf("library.log")) { saved.file("9", "u9", null, "f2") }
        change("removing", 100, setOf("library.log")) { saved.remove("5") }
        change("nothing to change", 0, emptySet()) {
            saved.setNote("7", "remember this")
            saved.remove("5")
            likes.unlike("9002")
        }

        val again = saved()
        assertEquals(saved.library(), again.library(), "the next launch reads the same")
        assertEquals("remember this", again.index().post("7")?.note)
        assertEquals("f1", again.index().folderOf("8"))
        assertEquals("f2", again.index().folderOf("9"))
        assertEquals(post, again.library().post("9001")?.content)
        assertEquals(content("9", "x".repeat(2_000)), again.library().post("9")?.content, "a move keeps what was kept")
        assertEquals(likes.likes(), likes().likes())
        assertEquals(listOf("9001", "1", "2"), likes().index().take(3).map { it.id })
    }

    @Test
    fun aCrashPartWayThroughTheMoveLeavesTheOldFileInCharge() = runBlocking {
        writeOld()
        File(folder, "library").mkdirs()
        File(folder, "library/30.json").writeText("""{"half":"writ""")
        File(folder, "library/30.json.part").writeText("{")
        File(folder, "library/999.json").writeText("""{"id_str":"left behind"}""")
        File(folder, "library.index.json.part").writeText("""{"v":2,"gen":1,"posts":[""")

        assertEquals(oldLibrary, saved().library())
        assertEquals(oldLibrary, saved().library(), "and at the launch after")
        assertEquals(3, File(folder, "library").list()!!.size, "what the crash left is cleared away")
        assertFalse(File(folder, "library.json").exists())
    }

    @Test
    fun anOldFileThatCannotBeMovedYetIsStillReadAndMovedByTheNextChangeThatCan() = runBlocking {
        writeOld()
        val oldText = File(folder, "library.json").readText()
        val inTheWay = File(folder, "library").apply { writeText("not a folder") }

        val store = saved()
        assertEquals(oldLibrary, store.library(), "read from the old file")
        assertEquals(oldLibrary.posts.map { it.copy(content = null) }, store.index().posts)
        assertEquals(oldText, File(folder, "library.json").readText(), "which stays until the new files are whole")
        assertFalse(File(folder, "library.index.json").exists())
        assertTrue(runCatching { store.setNote("10", "cannot be kept yet") }.isFailure)
        assertNull(store.index().post("10")?.note, "a change that could not be written is not shown")

        inTheWay.delete()
        store.setNote("10", "kept now")
        assertFalse(File(folder, "library.json").exists())
        val expected = oldLibrary.copy(posts = oldLibrary.posts.map { if (it.id == "10") it.copy(note = "kept now") else it })
        assertEquals(expected, store.library())
        assertEquals(expected, saved().library())
    }

    @Test
    fun aCrashBeforeTheOldFileWasDeletedChangesNothing() = runBlocking {
        writeOld()
        val oldText = File(folder, "library.json").readText()
        assertEquals(oldLibrary, saved().library())
        File(folder, "library.json").writeText(oldText)

        val store = saved()
        assertEquals(oldLibrary, store.library())
        assertFalse(File(folder, "library.json").exists())
        store.remove("30")
        assertEquals(listOf("mastodon:https://example.social/@a/1", "20", "10"), saved().index().posts.map { it.id }, "and what is removed stays removed")
    }

    @Test
    fun whatAnOlderBuildSavedSinceIsKeptAlongsideTheRest() = runBlocking {
        writeOld()
        saved().file("77", "u77", content("77"))
        // An older build finds no file of its own, starts empty and writes one.
        val since = SavedLibrary(listOf(SavedPost("88", "u88", content("88"), "f9", "from the old build", 900)), listOf(SavedFolder("f9", "Old", 0, 9)))
        File(folder, "library.json").writeText(SavedCodec.library(since).toString())

        val library = saved().library()
        assertEquals(listOf("88", "77", "30", "mastodon:https://example.social/@a/1", "20", "10"), library.posts.map { it.id })
        assertEquals(content("88"), library.post("88")?.content)
        assertEquals(content("77"), library.post("77")?.content)
        assertEquals(setOf("f1", "f2", "f9"), library.folders.map { it.id }.toSet())
        assertEquals(library, saved().library())
    }

    @Test
    fun aChangeCutShortByACrashNeverHappenedAndTheRestStand() = runBlocking {
        val first = likes()
        first.like("1", "u1", content("1"))
        first.like("2", "u2", content("2"))
        first.like("3", "u3", content("3"))
        val log = File(folder, "likes.log")
        log.writeText(log.readText().trimEnd().dropLast(25))

        val second = likes()
        assertEquals(listOf("2", "1"), second.index().map { it.id }, "the torn line is the like that was never kept")
        second.like("4", "u4", content("4"))
        second.unlike("1")
        assertEquals(listOf("4", "2"), likes().likes().map { it.id }, "and what is liked after it is not garbled by it")
        assertEquals(content("4"), likes().contentOf("4"))
        assertFalse(File(folder, "likes/3.json").exists(), "its content goes with it")
    }

    @Test
    fun aLogOfAnIndexSinceRewrittenIsNotAppliedTwice() = runBlocking {
        val first = likes()
        first.like("1", "u1", null)
        first.like("2", "u2", null)
        val staleLog = File(folder, "likes.log").readText()
        first.replace(listOf(LikedPost("9", null, null, 1)))
        File(folder, "likes.log").writeText(staleLog)

        assertEquals(listOf("9"), likes().index().map { it.id })
        val next = likes()
        next.like("10", null, null)
        assertEquals(listOf("10", "9"), likes().index().map { it.id })
    }

    @Test
    fun theLogIsFoldedIntoTheIndexBeforeItGrowsLong() = runBlocking {
        val likes = likes()
        (1..300).forEach { likes.like("$it", "u$it", content("$it")) }
        (1..300 step 3).forEach { likes.unlike("$it") }
        val log = File(folder, "likes.log")
        assertTrue(!log.exists() || log.readLines().size < 300, "folded at least once")
        val expected = (300 downTo 1).filter { it % 3 != 1 }.map { "$it" }
        assertEquals(expected, likes.likes().map { it.id })
        assertEquals(likes.likes(), likes().likes())
        assertEquals(expected.map { "$it.json" }.toSet(), File(folder, "likes").list()!!.toSet(), "one content file per like left, no more")
    }

    @Test
    fun aRestoredBackupReplacesEverythingAndTheOldContentGoes() = runBlocking {
        writeOld()
        val saved = saved()
        val likes = likes()
        val backup = SavedLibrary(
            posts = listOf(
                SavedPost("b", "ub", content("b"), "f2", "n", 2),
                SavedPost("30", "u30", content("30", "as the backup has it"), null, null, 1),
                SavedPost("b", "dup", null, null, null, 0),
            ),
            folders = listOf(SavedFolder("f2", "Second", 1, 0), SavedFolder("f1", "First", 0, 0)),
        )
        saved.replace(backup)
        likes.replace(listOf(LikedPost("z", "uz", content("z"), 9)))

        val expected = SavedLibrary(backup.posts.take(2), backup.folders.reversed())
        assertEquals(expected, saved.library())
        assertEquals(expected, saved().library())
        assertEquals(expected.posts.map { it.copy(content = null) }, saved().index().posts)
        assertEquals(2, File(folder, "library").list()!!.size, "the posts no longer saved are deleted")
        assertEquals(listOf(LikedPost("z", "uz", content("z"), 9)), likes().likes())
        assertEquals(1, File(folder, "likes").list()!!.size)
        assertFalse(files().values.any { it.contains("post 10") || it.contains("post m1") || it.contains("post r") })
    }

    @Test
    fun aRestoreMergesWithEverythingKeptNow() = runBlocking {
        writeOld()
        val saved = saved()
        saved.index()
        var seen: SavedLibrary? = null
        saved.restore { now ->
            seen = now
            now.copy(posts = now.posts.filterNot { it.id == "20" } + SavedPost("new", "un", content("new"), "f2", null, 1))
        }
        assertEquals(oldLibrary, seen, "the merge sees every post with its content")
        assertEquals(listOf("30", "mastodon:https://example.social/@a/1", "10", "new"), saved().library().posts.map { it.id })
        assertEquals(content("new"), saved().library().post("new")?.content)
        saved.restore { null }
        val likes = likes()
        likes.restore { now -> listOf(LikedPost("first", null, content("first"), 99)) + now }
        assertEquals(listOf("first", "3", "reddit:t3_abc", "1"), likes().likes().map { it.id })
        assertEquals(oldLikes, likes().likes().drop(1))
    }

    @Test
    fun whatIsNoLongerKeptIsDeletedFromTheDisk() = runBlocking {
        val saved = saved()
        val likes = likes()
        saved.file("1", "u1", content("1", "SECRET-SAVED"))
        saved.file("bluesky:at://did/app.bsky.feed.post/1", "did", content("b", "SECRET-OTHER"))
        saved.file("2", "u2", content("2"))
        likes.like("1", "u1", content("1", "SECRET-LIKED"))
        likes.like("2", "u2", content("2"))
        assertEquals(3, File(folder, "library").list()!!.size)

        saved.remove(listOf("1", "bluesky:at://did/app.bsky.feed.post/1"))
        likes.unlike("1")
        assertEquals(listOf("2.json"), File(folder, "library").list()!!.toList())
        assertEquals(listOf("2.json"), File(folder, "likes").list()!!.toList())
        assertFalse(files().values.any { it.contains("SECRET") }, "neither the files nor the log keep it")
        assertNull(saved.contentOf("1"))

        likes.like("2", "u2", null)
        assertTrue(File(folder, "likes").list()!!.isEmpty(), "liked again with nothing to keep")
        assertNull(likes().likes().single().content)
    }

    @Test
    fun theIndexIsReadWithoutTheContentAndBothFollowEachChange() = runBlocking {
        writeOld()
        val likes = likes()
        assertNull(likes.index.value)
        assertTrue(likes.isLiked("3"))
        assertEquals(listOf("3", "reddit:t3_abc", "1"), likes.index.value?.map { it.id })
        assertTrue(likes.index.value!!.all { it.content == null })
        assertNull(likes.state.value, "content is not read until a screen asks")

        likes.like("5", "u5", content("5"))
        assertNull(likes.state.value)
        assertEquals("5", likes.index.value?.first()?.id)

        val whole = likes.likes()
        assertEquals(content("5"), whole.first().content)
        likes.like("6", "u6", content("6"))
        likes.unlike("1")
        val after = likes.state.value!!
        assertEquals(listOf("6", "5", "3", "reddit:t3_abc"), after.map { it.id })
        assertEquals(content("6"), after.first().content)
        assertSame(whole[1], after[2], "a post a change leaves alone stays the same instance")
        assertEquals(after.map { it.copy(content = null) }, likes.index.value)
    }

    @Test
    fun filingASavedPostHandsBackWhatWasKeptOfIt() = runBlocking {
        val saved = saved()
        saved.file("1", "u1", content("1"))
        val moved = saved().file("1", "u1", content("1", "changed since"), "f1")
        assertEquals(content("1"), moved.content)
        assertEquals("f1", moved.folderId)
        assertEquals(content("1"), saved().file("1", "u1", null, "f1").content)
    }

    @Test
    fun deletingAFolderAndUnfilingItsPostsIsOneChange() = runBlocking {
        val saved = saved()
        val art = saved.createFolder("Art")!!
        (1..40).forEach { saved.file("$it", null, content("$it"), art.id) }
        saved.deleteFolder(art.id)
        val again = saved().index()
        assertTrue(again.folders.isEmpty())
        assertTrue(again.posts.all { it.folderId == null })
        assertEquals(40, saved().library().posts.count { it.content != null })
    }

    @Test
    fun anIndexThisBuildCannotReadIsKeptAsideWithItsContents() = runBlocking {
        val first = likes()
        first.like("1", "u1", content("1"))
        first.replace(first.likes())
        val index = File(folder, "likes.index.json")
        val newer = index.readText().replace("\"v\":2", "\"v\":3")
        index.writeText(newer)

        val likes = likes()
        assertTrue(likes.likes().isEmpty())
        likes.like("2", "u2", content("2"))
        assertEquals(newer, File(folder, "likes.index.json.unreadable").readText())
        assertEquals(content("1").toString(), File(folder, "likes.unreadable/1.json").readText(), "its contents are not cleared away")
        assertEquals(listOf("2"), likes().likes().map { it.id })
    }

    @Test
    fun aContentFileThatWentMissingIsAPostWithNothingKept() = runBlocking {
        val likes = likes()
        likes.like("1", "u1", content("1"))
        likes.like("2", "u2", content("2"))
        File(folder, "likes/1.json").delete()
        File(folder, "likes/2.json").writeText("{not json")
        val read = likes().likes()
        assertEquals(listOf("2", "1"), read.map { it.id })
        assertTrue(read.all { it.content == null })
        assertTrue(J.parse(File(folder, "likes.index.json").readText()).raw != null)
    }

    @Test
    fun theContentIsLetGoUnwatchedAndReadAgainWhenWatchedAgain() = timed {
        val watching = CoroutineScope(Dispatchers.Default + SupervisorJob())
        try {
            val kept = KeptPosts(dir, "likes", LikesShape, watching, releaseAfterMillis = 100)
            kept.edit { Edit(listOf(LikedPost("1", "u1", content("1"), 10)), Unit, fresh = "1") }
            kept.whole()
            val screen = watching.launch { kept.whole.collect {} }
            delay(300)
            assertEquals(content("1"), kept.whole.value?.single()?.content, "kept while watched")
            screen.cancel()
            waitUntil { kept.whole.value == null }
            assertEquals(listOf("1"), kept.bare.value?.map { it.id }, "the index stays")

            val again = watching.launch { kept.whole.collect {} }
            waitUntil { kept.whole.value != null }
            assertEquals(content("1"), kept.whole.value?.single()?.content)
            again.cancel()
        } finally {
            watching.cancel()
        }
    }
}
