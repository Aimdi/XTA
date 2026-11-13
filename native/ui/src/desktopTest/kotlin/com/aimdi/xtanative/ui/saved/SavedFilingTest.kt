package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.saved.SavedContent
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedLibrary
import com.aimdi.xtanative.store.saved.SavedStore
import com.aimdi.xtanative.store.settings.DownloadType
import com.aimdi.xtanative.store.settings.MediaPrefs
import com.aimdi.xtanative.store.settings.SavedPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class SavedFilingTest {
    private val dir = tempStore()
    private val saved = SavedStore(dir)
    private val settings = SettingsStore(dir)
    private val filing = SavedFiling(saved, settings)

    @Test
    fun aPlainTapFilesWhereTheReaderLastPickedOnlyWhileRemembering(): Unit = runBlocking {
        val folder = saved.createFolder("Later")!!
        settings.set(SavedPrefs.stickyFolderId, folder.id)
        assertNull(filing.rememberedFolder(), "remembering is off")
        settings.set(SavedPrefs.stickyFolderEnabled, true)
        assertEquals(folder.id, filing.rememberedFolder())
        saved.deleteFolder(folder.id)
        assertNull(filing.rememberedFolder(), "a deleted folder files unfiled")
    }

    @Test
    fun aFolderSetToDownloadFetchesThePhotosWhereTheSettingSays(): Unit = runBlocking {
        val folder = saved.createFolder("Photos", autoDownload = true)!!
        val filed = filing.file("1", "u1", xTweet("1", photo = true), folder.id)
        assertIs<AutoDownload.Start>(filed)
        assertEquals(listOf("user1-m1.jpg"), filed.requests.map { it.fileName })
        assertTrue(filed.requests.all { it.background })
        assertEquals(folder.id, saved.library().folderOf("1"))

        settings.set(MediaPrefs.downloadType, DownloadType.ASK)
        assertEquals(AutoDownload.NeedsFolder, filing.file("2", "u2", xTweet("2", photo = true), folder.id))
        assertEquals(AutoDownload.None, filing.file("3", "u3", xTweet("3", photo = true), null))
    }

    @Test
    fun aFolderThatDoesNotDownloadAsksForNothing() {
        val plain = SavedFolder("f", "Plain")
        assertEquals(AutoDownload.None, SavedFiling.autoDownload(plain, SavedContent.of(xTweet("1", photo = true)), DownloadDestination.SHARED))
        val auto = plain.copy(autoDownload = true)
        assertEquals(AutoDownload.None, SavedFiling.autoDownload(auto, SavedContent.of(xTweet("1")), DownloadDestination.SHARED), "no photo to fetch")
    }

    @Test
    fun theSheetRemembersItsPickWhileRemembering(): Unit = runBlocking {
        filing.remember("later")
        assertEquals("", settings.load()[SavedPrefs.stickyFolderId])
        filing.setRemember(true, "art")
        assertEquals("art", settings.load()[SavedPrefs.stickyFolderId], "switching on adopts the post's folder")
        filing.remember(null)
        assertEquals("", settings.load()[SavedPrefs.stickyFolderId])
        filing.setRemember(false, "later")
        assertFalse(settings.load()[SavedPrefs.stickyFolderEnabled])
    }

    @Test
    fun theHintsShowOnce(): Unit = runBlocking {
        assertTrue(filing.firstSave())
        assertFalse(filing.firstSave())
        assertTrue(filing.firstLike())
        assertFalse(filing.firstLike())
    }

    @Test
    fun theSheetSaysWhereThePostIs() {
        val library = SavedLibrary(listOf(savedX("1", folder = "art"), savedX("2"), savedX("3", folder = "gone")), listOf(SavedFolder("art", "Art")))
        assertEquals(SaveStatus.InFolder("Art"), SaveStatus.of(library, "1"))
        assertEquals(SaveStatus.Unfiled, SaveStatus.of(library, "2"))
        assertEquals(SaveStatus.Unfiled, SaveStatus.of(library, "3"))
        assertEquals(SaveStatus.NotSaved, SaveStatus.of(library, "4"))
    }
}
