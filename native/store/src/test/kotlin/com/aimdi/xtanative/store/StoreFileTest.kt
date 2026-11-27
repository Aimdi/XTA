package com.aimdi.xtanative.store

import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlinx.coroutines.runBlocking

class StoreFileTest {
    @Test
    fun aFileReadsBackWhatWasWrittenAndNothingBefore() = runBlocking {
        val dir = StoreDir(Files.createTempDirectory("store").toFile())
        val file = dir.file("saved/posts.json")
        assertNull(file.read())
        file.write("[1]")
        file.write("[1,2]")
        assertEquals("[1,2]", dir.file("saved/posts.json").read())
        assertSame(file, dir.file("saved/posts.json"))
        file.delete()
        assertNull(file.read())
    }

    @Test
    fun aReplacedFileLeavesNoStagingFileAndIsTheOwnersOnly() {
        val folder = Files.createTempDirectory("replace").toFile()
        val file = File(folder, "window.ini").apply { writeText("old") }
        file.replaceWith(byteArrayOf(1, 2, 3))
        assertContentEquals(byteArrayOf(1, 2, 3), file.readBytes())
        assertEquals(listOf("window.ini"), folder.list()!!.toList())
        assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        folder.deleteRecursively()
    }

    @Test
    fun writersAtTheSameTimeEachLeaveOneWholeText() {
        val folder = Files.createTempDirectory("replace").toFile()
        val file = File(folder, "feed.json")
        val texts = (0 until 8).map { n -> n.toString().repeat(200_000) }
        texts.map { text -> Thread { repeat(5) { file.replaceWith(text) } }.apply { start() } }.forEach { it.join() }
        assertEquals(true, file.readText() in texts)
        assertEquals(listOf("feed.json"), folder.list()!!.toList())
        folder.deleteRecursively()
    }

    @Test
    fun theFirstTouchOfAFileClearsTheStagingAnEarlierCrashLeftBesideIt() = runBlocking {
        val folder = Files.createTempDirectory("swept").toFile()
        val hour = 60L * 60 * 1000
        fun staged(name: String, age: Long) = File(folder, name).apply { writeText("half"); setLastModified(System.currentTimeMillis() - age) }
        val crashed = staged(".settings.json.123456789.part", hour)
        val writing = staged(".settings.json.42.part", -hour)
        val other = staged(".settings.json.bak.9.part", hour)
        val notOurs = staged("settings.json.part", hour)
        val feeds = staged(".feed.json.7.part", hour)
        assertNull(StoreDir(folder).file("settings.json").read())
        assertEquals(setOf(writing.name, other.name, notOurs.name, feeds.name), folder.list()!!.toSet())
        File(folder, "feed.json").replaceWith("[]")
        assertEquals(false, feeds.exists())
        assertEquals(true, isStaleStagedWrite(other))
        assertEquals(false, isStaleStagedWrite(notOurs))
        folder.deleteRecursively()
    }
}
