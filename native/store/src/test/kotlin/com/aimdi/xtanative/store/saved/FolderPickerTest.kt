package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.store.downloads.DownloadDestination
import com.aimdi.xtanative.store.downloads.DownloadRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FolderPickerTest {
    private val folders = (0 until 8).map { SavedFolder("f$it", "Folder $it", it) }

    private fun saved(id: String, folderId: String?) = SavedPost(id, "u", null, folderId)

    @Test
    fun theLatestSavedPostsLeadWithTheirFoldersEachOnceThenTheRest() {
        val order = FolderPicker.openOrder(listOf(saved("a", "f5"), saved("b", null), saved("c", "f2"), saved("d", "f5")), folders)
        assertEquals(listOf("f5", "f2", "f0", "f1", "f3", "f4", "f6", "f7"), order.map { it.id })
    }

    @Test
    fun aPostInAFolderThatIsGoneIsPassedOver() {
        assertEquals(listOf("f1", "f0"), FolderPicker.openOrder(listOf(saved("a", "gone"), saved("b", "f1")), folders.take(2)).map { it.id })
    }

    @Test
    fun aTapOnThePostsOwnFolderTakesItOutStillSaved() {
        assertNull(FolderPicker.afterTap("art", "art"))
        assertEquals("news", FolderPicker.afterTap("art", "news"))
        assertEquals("art", FolderPicker.afterTap(null, "art"))
    }

    @Test
    fun aPlainTapFilesWhereTheLastPickWentOnlyWhileRememberingIsOn() {
        assertNull(FolderPicker.rememberedFolder(rememberOn = false, storedId = "news"))
        assertNull(FolderPicker.rememberedFolder(rememberOn = true, storedId = ""))
        assertEquals("news", FolderPicker.rememberedFolder(rememberOn = true, storedId = "news"))
        assertEquals("", FolderPicker.toRemember(rememberOn = true, picked = null))
        assertNull(FolderPicker.toRemember(rememberOn = false, picked = "news"))
    }

    private val eight = (0 until 8).map { "Choice $it" }

    @Test
    fun sixShowUntilAllWhichShowsEveryOne() {
        val recent = QuickPick.chips(eight, "", showAll = false, nameOf = { it })
        assertEquals(QuickPick.SHOWN, recent.items.size)
        assertEquals(QuickPick.Section.RECENT, recent.section)
        assertTrue(recent.hasMore)
        val all = QuickPick.chips(eight, "", showAll = true, nameOf = { it })
        assertEquals(8, all.items.size)
        assertEquals(QuickPick.Section.ALL, all.section)
        assertFalse(all.hasMore)
        assertFalse(QuickPick.chips(eight, "", showAll = false, nameOf = { it }, shown = 8).hasMore)
    }

    @Test
    fun choicesMadeInTheSheetLeadAndEveryChoiceShowsOnceWhileItExists() {
        assertEquals(listOf("new", "c", "a", "b"), QuickPick.ordered(listOf("a", "b", "c", "new"), listOf("c", "a", "b"), listOf("new")) { it })
        assertEquals(listOf("b", "a"), QuickPick.ordered(listOf("a", "b"), listOf("gone", "b", "a"), emptyList()) { it })
    }

    @Test
    fun aSearchIgnoresCaseAndOffersOnlyANameNoChoiceHas() {
        val names = listOf("Art", "Japan Trip", "News")
        val found = QuickPick.chips(names, " JAP", showAll = false, nameOf = { it })
        assertEquals(listOf("Japan Trip"), found.items)
        assertEquals(QuickPick.Section.MATCHES, found.section)
        assertTrue(found.canCreate)
        assertTrue(QuickPick.nameTaken(names, "japan trip") { it })
        assertFalse(QuickPick.chips(names, "japan trip ", showAll = false, nameOf = { it }).canCreate)
        val none = QuickPick.chips(names, "Kyoto", showAll = false, nameOf = { it })
        assertEquals(QuickPick.Section.NO_MATCH, none.section)
        assertTrue(none.canCreate)
    }

    @Test
    fun aFolderThatDownloadsTakesThePostsPicturesAtFullSizeNamedAfterTheAuthor() {
        val content = SavedContent.of(
            json(
                """{"id_str":"1","user":{"id_str":"u","screen_name":"amy","name":"Amy"},"extended_entities":{"media":[
                {"id_str":"m1","type":"photo","media_url_https":"https://pbs.twimg.com/media/a.jpg"},
                {"id_str":"m2","type":"video","media_url_https":"https://pbs.twimg.com/poster.jpg"}]}}""",
            ),
        )
        assertNull(FolderPicker.autoDownloads(content, DownloadDestination.ASK), "a filing cannot answer a folder picker")
        assertEquals(
            listOf(DownloadRequest("https://pbs.twimg.com/media/a.jpg:orig", "amy-a.jpg", background = true)),
            FolderPicker.autoDownloads(content, DownloadDestination.SHARED),
        )
        val mastodon = SavedContent.of(json("""{"xtaPlugin":"mastodon","post":{"version":1,"id":"1","acct":"artist@studio.example","url":"https://studio.example/@artist/1","images":["https://studio.example/files/garden.png"]}}"""))
        assertEquals("artist-garden.png", FolderPicker.autoDownloads(mastodon, DownloadDestination.folder("content://tree/x"))?.single()?.fileName)
    }
}
