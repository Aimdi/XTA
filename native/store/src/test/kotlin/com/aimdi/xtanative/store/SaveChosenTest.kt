package com.aimdi.xtanative.store

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A file the reader chose in a save dialog: a failed write over one already there leaves it as it was. */
class SaveChosenTest {
    private fun folder() = Files.createTempDirectory("chosen").toFile()

    @Test
    fun aWriteThatFailsOverAnExistingFileLeavesItAndNoStagingFile() {
        val folder = folder()
        val file = File(folder, "notes.opml").apply { writeText("old") }
        assertFailsWith<IOException> {
            file.saveChosen { out ->
                out.write("half".toByteArray())
                throw IOException("No space left on device")
            }
        }
        assertEquals("old", file.readText())
        assertEquals(listOf("notes.opml"), folder.list()!!.toList())
        folder.deleteRecursively()
    }

    @Test
    fun aReadOnlyFileIsNotReplaced() {
        val folder = folder()
        val file = File(folder, "kept.json").apply { writeText("old") }
        file.setWritable(false)
        assertFailsWith<IOException> { file.saveChosen { it.write("new".toByteArray()) } }
        assertEquals("old", file.readText())
        assertEquals(listOf("kept.json"), folder.list()!!.toList())
        folder.deleteRecursively()
    }

    @Test
    fun aReplacedFileKeepsItsPermissions() {
        val folder = folder()
        val file = File(folder, "pack.json").apply { writeText("old") }
        Files.setPosixFilePermissions(file.toPath(), PosixFilePermissions.fromString("rw-r--r--"))
        file.saveChosen { it.write("new".toByteArray()) }
        assertEquals("new", file.readText())
        assertEquals("rw-r--r--", PosixFilePermissions.toString(Files.getPosixFilePermissions(file.toPath())))
        assertEquals(listOf("pack.json"), folder.list()!!.toList())
        folder.deleteRecursively()
    }

    @Test
    fun aNewFileWhoseWriteFailsIsNotLeftHalfWritten() {
        val folder = folder()
        val file = File(folder, "photo.jpg")
        assertFailsWith<IOException> { file.saveChosen { throw IOException("cancelled") } }
        assertFalse(file.exists())
        File(folder, "made.txt").saveChosen { it.write("x".toByteArray()) }
        assertEquals("x", File(folder, "made.txt").readText())
        folder.deleteRecursively()
    }

    @Test
    fun aLinkedFileIsReplacedWhereTheLinkPoints() {
        val folder = folder()
        val real = File(folder, "real.json").apply { writeText("old") }
        val link = File(folder, "link.json").also { Files.createSymbolicLink(it.toPath(), real.toPath()) }
        link.saveChosen { it.write("new".toByteArray()) }
        assertEquals("new", real.readText())
        assertEquals(true, Files.isSymbolicLink(link.toPath()))
        folder.deleteRecursively()
    }

    @Test
    fun aFileThereWhoseFolderTakesNoFileBesideIsNotDeletedByAFailedWrite() {
        val folder = folder()
        val file = File(folder, "shared.json").apply { writeText("old") }
        folder.setWritable(false)
        try {
            assertFailsWith<IOException> { file.saveChosen { throw IOException("cancelled") } }
            assertTrue(file.exists(), "the reader's file is not removed")
        } finally {
            folder.setWritable(true)
            folder.deleteRecursively()
        }
    }
}
