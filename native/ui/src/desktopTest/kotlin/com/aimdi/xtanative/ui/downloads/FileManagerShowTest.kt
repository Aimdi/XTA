package com.aimdi.xtanative.ui.downloads

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlinx.coroutines.runBlocking

class FileManagerShowTest {
    @Test
    fun theFileManagerIsAskedForTheFilesAddress() {
        assertEquals(listOf(listOf("file:///home/me/Pictures/a%20b.jpg"), ""), FileManagerShow.showItems(File("/home/me/Pictures/a b.jpg")))
    }

    @Test
    fun aFileThatIsGoneIsNotShown() = runBlocking {
        assertFalse(FileManagerShow.show(File("/nowhere/gone.jpg").toURI().toString(), connect = { null }))
    }

    @Test
    fun aFileManagerStillStartingCountsAsShowing() {
        kotlin.test.assertTrue(FileManagerShow.answered(com.aimdi.xtanative.ui.media.mpris.DBusType.METHOD_RETURN))
        kotlin.test.assertTrue(FileManagerShow.answered(null))
        assertFalse(FileManagerShow.answered(com.aimdi.xtanative.ui.media.mpris.DBusType.ERROR))
    }
}
