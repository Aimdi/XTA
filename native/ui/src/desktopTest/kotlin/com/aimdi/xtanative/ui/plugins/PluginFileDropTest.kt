package com.aimdi.xtanative.ui.plugins

import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.datatransfer.Transferable
import java.io.File
import java.io.IOException
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class PluginFileDropTest {
    private val dir: File = Files.createTempDirectory("xta-drops").toFile()

    @AfterTest
    fun cleanUp() {
        dir.deleteRecursively()
    }

    private fun file(name: String, text: String = "<opml/>"): File = File(dir, name).apply { writeText(text) }

    private fun filesOf(vararg files: File): Transferable = object : Transferable {
        override fun getTransferDataFlavors() = arrayOf(DataFlavor.javaFileListFlavor)
        override fun isDataFlavorSupported(flavor: DataFlavor) = flavor == DataFlavor.javaFileListFlavor
        override fun getTransferData(flavor: DataFlavor): Any = files.toList()
    }

    @Test
    fun aDropOfFilesHoldsItsRegularFilesInOrder() {
        val b = file("b.opml")
        val a = file("a.xml")
        val transfer = filesOf(b, dir, a)
        assertTrue(DroppedFiles.offered(transfer))
        assertEquals(listOf(b, a), DroppedFiles.files(transfer))
    }

    @Test
    fun aDropOfTextHoldsNoFiles() {
        val text = StringSelection("https://example.com/feed")
        assertFalse(DroppedFiles.offered(text))
        assertEquals(emptyList(), DroppedFiles.files(text))
    }

    @Test
    fun aPluginGetsTheFilesItAcceptsWithTheirTypes() {
        val opml = file("feeds.OPML")
        val picture = file("cat.png")
        val taken = DroppedFiles.forPlugin(listOf(opml, picture), accept = { _, mime -> mime == "text/x-opml" })
        assertEquals(listOf("feeds.OPML"), taken.map { it.name })
        assertEquals("text/x-opml", taken.single().mime)
        assertEquals(opml.length(), taken.single().size)
        assertEquals("image/png", DroppedFiles.mime(picture))
    }

    @Test
    fun aFileIsReadOnlyWhenAsked() = runBlocking {
        val opml = file("feeds.opml", "first")
        val dropped = DroppedFiles.forPlugin(listOf(opml), accept = { _, _ -> true }).single()
        opml.writeText("second")
        assertEquals("second", dropped.text())
        assertContentEquals("second".encodeToByteArray(), dropped.bytes())
    }

    @Test
    fun aFileLargerThanTheCapIsLeftOut() {
        val big = file("big.opml", "x".repeat(20))
        assertEquals(emptyList(), DroppedFiles.forPlugin(listOf(big), accept = { _, _ -> true }, max = 10))
    }

    @Test
    fun aFileGoneOrGrownPastTheCapIsRefused() {
        val opml = file("feeds.opml", "x".repeat(20))
        assertFailsWith<IOException> { DroppedFiles.readUpTo(opml, max = 10) }
        opml.delete()
        assertFailsWith<IOException> { DroppedFiles.readUpTo(opml, max = 100) }
    }

    @Test
    fun anAcceptThatThrowsTakesNothing() {
        assertEquals(emptyList(), DroppedFiles.forPlugin(listOf(file("a.opml")), accept = { _, _ -> error("plugin bug") }))
    }
}
