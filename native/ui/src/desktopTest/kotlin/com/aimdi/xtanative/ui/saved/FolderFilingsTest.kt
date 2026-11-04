package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.data.AppGraph
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking

/** A post filed in a folder is handed on (to the app's plugins), as XTA's filing hands it to the folder's upload. */
class FolderFilingsTest {
    @Test
    fun aPostFiledInAFolderIsHandedOnWithTheFolderAndAnUnfiledSaveIsNot(): Unit = runBlocking {
        val graph = AppGraph(Files.createTempDirectory("folder-filings").toFile(), flowOf(true), "test")
        val heard = mutableListOf<Pair<String, String>>()
        FolderFilings.listen(graph) { folder, item -> synchronized(heard) { heard += folder.name to item.id } }
        val folder = graph.saved.createFolder("Art", autoUpload = true)!!

        Keeping.fileFromSheet(graph, Keepable("1", "u1", xTweet("1", photo = true)), folder.id)
        Keeping.fileFromSheet(graph, Keepable("2", "u2", xTweet("2")), null)
        graph.awaitWrites()

        assertEquals(listOf("Art" to "1"), heard)
        assertEquals(folder.id, graph.saved.library().folderOf("1"), "filed first, then handed on")
    }

    @Test
    fun anotherGraphsFilingsAreNotHeard(): Unit = runBlocking {
        val graph = AppGraph(Files.createTempDirectory("folder-filings-a").toFile(), flowOf(true), "test")
        val other = AppGraph(Files.createTempDirectory("folder-filings-b").toFile(), flowOf(true), "test")
        val heard = mutableListOf<String>()
        FolderFilings.listen(graph) { _, item -> synchronized(heard) { heard += item.id } }
        val folder = other.saved.createFolder("Art")!!

        Keeping.fileFromSheet(other, Keepable("3", "u3", xTweet("3")), folder.id)
        other.awaitWrites()

        assertEquals(emptyList<String>(), heard)
    }
}
