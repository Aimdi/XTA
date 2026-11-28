package com.aimdi.xtanative.store.history

import com.aimdi.xtanative.store.StoreDir
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

class ViewHistoryTest {
    private val root: File = Files.createTempDirectory("history").toFile()

    private fun post(id: String, at: Long, text: String = "post $id") = HistoryEntry(HistoryKind.POST, id, "Ann", "ann", text, at)

    @Test
    fun anOpenedPostOrProfileGoesFirstOnceAndIsKeptForTheNextLaunch(): Unit = runBlocking {
        val history = ViewHistory(StoreDir(root))
        history.record(post("1", 1))
        history.record(HistoryEntry(HistoryKind.PROFILE, "Jack", "Jack", "Jack", at = 2))
        history.record(post("1", 3))
        history.record(HistoryEntry(HistoryKind.PROFILE, "jack", "Jack", "jack", at = 4))
        assertEquals(listOf("profile:jack", "post:1"), history.entries.first().map { it.key }, "opened again, it moves up; a handle's case is the same account")
        assertEquals(listOf(4L, 3L), ViewHistory(StoreDir(root)).load().map { it.at })
        val file = File(root, ViewHistory.FILE).toPath()
        runCatching { Files.getPosixFilePermissions(file) }.getOrNull()?.let { perms ->
            assertFalse(PosixFilePermission.OTHERS_READ in perms || PosixFilePermission.GROUP_READ in perms, "only its owner reads it")
        }
    }

    @Test
    fun itKeepsTheNewestFiveHundredAndTheStartOfLongText() {
        val all = (1..HistoryRules.CAP + 20).fold(emptyList<HistoryEntry>()) { entries, n -> HistoryRules.recorded(entries, post("$n", n.toLong())) }
        assertEquals(HistoryRules.CAP, all.size)
        assertEquals("520", all.first().target)
        assertEquals("21", all.last().target)
        assertEquals(HistoryRules.TEXT_CHARS, HistoryRules.recorded(emptyList(), post("x", 0, "a".repeat(1_000))).single().text.length)
    }

    @Test
    fun oneIsRemovedOrAllClearedWithTheFile(): Unit = runBlocking {
        val history = ViewHistory(StoreDir(root))
        history.record(post("1", 1))
        history.record(post("2", 2))
        history.remove("post:1")
        assertEquals(listOf("2"), history.load().map { it.target })
        history.clear()
        assertTrue(history.load().isEmpty())
        assertFalse(File(root, ViewHistory.FILE).exists())
    }

    @Test
    fun theSearchFindsANameAHandleOrText() {
        val entry = post("1", 0, "Compose on the desktop")
        assertTrue(entry.matches("desktop"))
        assertTrue(entry.matches("ANN"))
        assertTrue(entry.matches(" "))
        assertFalse(entry.matches("kotlin"))
    }

    @Test
    fun anUnreadableFileIsSetAsideAndTheHistoryStartsAfresh(): Unit = runBlocking {
        File(root, ViewHistory.FILE).writeText("{broken")
        assertTrue(ViewHistory(StoreDir(root)).load().isEmpty())
        assertTrue(File(root, ViewHistory.FILE + ".unreadable").isFile)
        assertEquals(emptyList(), ViewHistory.decode("""{"v":1,"entries":[{"kind":"other","target":"x"},{"kind":"post"}]}"""))
    }
}
