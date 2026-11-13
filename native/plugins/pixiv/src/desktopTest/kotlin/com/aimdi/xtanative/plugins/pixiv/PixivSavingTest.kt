package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.store.downloads.MAX_DOWNLOAD_STEM_BYTES
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixivSavingTest {
    @Test
    fun aPageIsNamedByTheTemplateItsExtensionKept() {
        val illust = work(42, userId = 3).copy(title = "Sky")
        assertEquals("42_p1.png", PixivSaveNaming.fileName(PixivSaveNaming.DEFAULT_TEMPLATE, illust, 1, ".png"))
        assertEquals("Sky-3-0.jpg", PixivSaveNaming.fileName("{title}-{user_id}-{part}", illust, 0, ".jpg"))
    }

    @Test
    fun aTemplateThatLostItsPartIsNotUsed() {
        val settings = Settings().with(PixivPrefs.fileNameTemplate, "{title}")
        assertEquals(PixivSaveNaming.DEFAULT_TEMPLATE, PixivSaveNaming.of(settings).template)
    }

    @Test
    fun theExtensionComesFromTheFileElseJpg() {
        assertEquals(".png", PixivSaveNaming.extensionOf("https://i.pximg.net/img-original/img/1_p0.PNG"))
        assertEquals(".jpg", PixivSaveNaming.extensionOf("https://i.pximg.net/img-original/img/1_p0"))
    }

    @Test
    fun foldersArePerCreatorAndForR18AsAsked() {
        val illust = work(1, userId = 3)
        assertNull(PixivSaveNaming().subfolder(illust))
        assertEquals("Artist 3_3", PixivSaveNaming(folderPerArtist = true).subfolder(illust))
    }

    @Test
    fun savedPagesAreRememberedNewestLastWithoutRepeats() {
        val once = PixivSavedPages.recorded(Settings(), 5, listOf(0, 1))
        val again = PixivSavedPages.recorded(once, 5, listOf(0))
        assertEquals(listOf("5_p1", "5_p0"), PixivSavedPages.of(again).toList())
        assertEquals(listOf(0), PixivSavedPages.savedAmong(PixivSavedPages.of(again), work(5), listOf(0, 3)))
        assertEquals(emptySet(), PixivSavedPages.of(PixivSavedPages.cleared(again)))
    }

    @Test
    fun onlyTheNewestPagesAreKept() {
        val keys = (1..PixivSavedPages.CAP + 2).map { PixivSavedPages.key(it.toLong(), 0) }
        val kept = PixivSavedPages.capped(keys)
        assertEquals(PixivSavedPages.CAP, kept.size)
        assertEquals("3_p0", kept.first())
    }

    @Test
    fun aResaveTakesAllTheNewOnesOrNone() {
        assertEquals(listOf(0, 1, 2), pagesToSave(listOf(0, 1, 2), listOf(1), PixivResave.ALL))
        assertEquals(listOf(0, 2), pagesToSave(listOf(0, 1, 2), listOf(1), PixivResave.NEW_ONLY))
        assertEquals(emptyList(), pagesToSave(listOf(0, 1, 2), listOf(1), null))
    }

    @Test
    fun aTitleThatReadsLikeAPlaceholderStaysAsWritten() {
        val illust = work(42).copy(title = "{user_name}")
        assertEquals("{user_name}_p0.jpg", PixivSaveNaming.fileName("{title}_p{part}", illust, 0, ".jpg"))
    }

    @Test
    fun unknownPlaceholdersAreKeptAndOuterDotsAndSpacesTrimmed() {
        val illust = work(42)
        assertEquals("42-{unknown}_p0.jpg", PixivSaveNaming.fileName("  ..{illust_id}-{unknown}_p{part}.. ", illust, 0, ".jpg"))
    }

    @Test
    fun aLongNameIsCutToAByteBudgetBetweenWholeCharacters() {
        val illust = work(42).copy(title = "あ".repeat(400))
        val name = PixivSaveNaming.fileName("{title}_p{part}", illust, 0, ".jpg")
        val stem = name.removeSuffix(".jpg")
        assertTrue(stem.toByteArray(Charsets.UTF_8).size <= MAX_DOWNLOAD_STEM_BYTES)
        assertTrue(stem.isNotEmpty())
        assertEquals(0, stem.count { it == '\uFFFD' })
    }

    @Test
    fun aNameThatEndsUpEmptyFallsBackToTheWorkAndPage() {
        assertEquals("42_p2.jpg", PixivSaveNaming.fileName("...", work(42), 2, ".jpg"))
    }

    @Test
    fun anArtistFolderNeverStartsWithADot() {
        val illust = work(42, userId = 3).copy(userName = ".hidden")
        val folder = PixivSaveNaming(folderPerArtist = true).subfolder(illust)
        assertTrue(folder != null && !folder.startsWith("."))
        assertEquals("hidden_3", folder)
    }

    @Test
    fun aDamagedStoredIndexReadsAsWhatItValidlyHolds() {
        val settings = Settings().with(PixivPrefs.downloadIndex, """["5_p0","bad","7_p2",3,"9_p1"]""")
        assertEquals(listOf("5_p0", "7_p2", "9_p1"), PixivSavedPages.of(settings).toList())
    }
}
