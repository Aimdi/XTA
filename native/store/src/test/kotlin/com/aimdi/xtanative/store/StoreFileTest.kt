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
}
