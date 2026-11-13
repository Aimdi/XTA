package com.aimdi.xtanative.store.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class DownloadDestinationTest {
    @Test
    fun eachSettingHasItsDestinationAndADirectoryWithoutAFolderStillAsks() {
        assertTrue(DownloadDestination.of(null, null).background, "a fresh install saves in the background")
        assertTrue(DownloadDestination.of(DownloadDestination.TYPE_ASK, null).asks)
        assertEquals(DownloadDestination("content://tree/x", false), DownloadDestination.of(DownloadDestination.TYPE_DIRECTORY, "content://tree/x"))
        assertTrue(DownloadDestination.of(DownloadDestination.TYPE_DIRECTORY, "").asks)
        assertEquals(DownloadDestination.SHARED, DownloadDestination.of(DownloadDestination.TYPE_AUTO, "content://tree/old"))
    }

    @Test
    fun theInheritedAskMovesToBackgroundSavingButADeliberateChoiceStays() {
        assertEquals(DownloadDestination.TYPE_AUTO, DownloadDestination.migratedType(DownloadDestination.TYPE_ASK, folderChosen = false))
        assertEquals(DownloadDestination.TYPE_ASK, DownloadDestination.migratedType(DownloadDestination.TYPE_ASK, folderChosen = true))
        assertEquals(DownloadDestination.TYPE_DIRECTORY, DownloadDestination.migratedType(DownloadDestination.TYPE_DIRECTORY, folderChosen = false))
    }

    @Test
    fun aBatchAsksForAFolderOnceOnlyWhenTheSettingAsks() = runBlocking {
        var asked = 0
        val pick: suspend () -> String? = { asked++; "content://tree/picked" }
        assertEquals(DownloadDestination.SHARED, DownloadDestination.forBatch(DownloadDestination.SHARED, pick))
        assertEquals(0, asked)
        assertEquals(DownloadDestination.folder("content://tree/picked"), DownloadDestination.forBatch(DownloadDestination.ASK, pick))
        assertNull(DownloadDestination.forBatch(DownloadDestination.ASK) { null })
    }
}
