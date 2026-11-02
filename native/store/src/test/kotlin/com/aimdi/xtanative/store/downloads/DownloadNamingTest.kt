package com.aimdi.xtanative.store.downloads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DownloadNamingTest {
    @Test
    fun onlyXsPictureHostIsAskedForTheOriginalSize() {
        assertEquals("https://pbs.twimg.com/media/a.jpg:orig", originalDownloadUrl("https://pbs.twimg.com/media/a.jpg"))
        assertEquals("https://pbs.twimg.com/media/a.jpg:orig", originalDownloadUrl("https://pbs.twimg.com/media/a.jpg:small"))
        assertEquals("https://pbs.twimg.com/media/a?format=png&name=orig", originalDownloadUrl("https://pbs.twimg.com/media/a?format=png&name=small"))
        listOf("https://mastodon.social/media/a.jpg?token=abc", "https://i.redd.it/a.jpg", "https://pbs.twimg.com.evil.example/a.jpg").forEach {
            assertEquals(it, originalDownloadUrl(it))
        }
    }

    @Test
    fun fileNamesCannotEscapeTheirDestination() {
        assertEquals("photo.jpg", safeDownloadName("../../photo.jpg?token=x"))
        assertEquals("photo.jpg", safeDownloadName("C:\\folder\\photo.jpg"))
        assertEquals("xta-media", safeDownloadName(".."))
        assertEquals("a_b.jpg", safeDownloadName("a:b.jpg"))
        assertEquals(240, safeDownloadName("x".repeat(300)).length)
    }

    @Test
    fun aSubfolderStaysInsideTheChosenFolder() {
        assertEquals("R-18/Mika_42", safeDownloadFolder("R-18/Mika_42"))
        assertEquals("R-18/Mika_42", safeDownloadFolder("..\\..\\R-18/./Mika:42/"))
        assertEquals("name", safeDownloadFolder(" name. /  "))
        assertEquals("hidden/mika_42", safeDownloadFolder(".hidden/ .mika_42"))
        assertNull(safeDownloadFolder("/../."))
        assertEquals("a/b/c/d", safeDownloadFolder("a/b/c/d/e/f"))
        assertEquals("x".repeat(120), safeDownloadFolder("x".repeat(300)))
    }

    @Test
    fun aTemplateIsFilledInOnePassAndKeepsWhatItCannotFill() {
        val values = mapOf("{title}" to "{user_id}", "{part}" to "0", "{user_id}" to "42")
        assertEquals("{user_id}_p0", fillNameTemplate("{title}_p{part}", values))
        assertEquals("{nope}_0", fillNameTemplate("{nope}_{part}", values))
    }

    @Test
    fun aStemLosesWhatFileSystemsRefuseAndFitsAndroidsLimit() {
        assertEquals("a_b_c_d_e_f_g_h_i_j_x_y", safeFileStem("a/b\\c:d*e?f\"g<h>i|j_x\ty"))
        assertEquals("Sommerfest", safeFileStem(" ..Sommerfest.. "))
        assertEquals("x".repeat(180), safeFileStem("x".repeat(400)))
        assertEquals("桜".repeat(60), safeFileStem("桜".repeat(100)))
        assertEquals("😀".repeat(45), safeFileStem("😀".repeat(50)), "never half an emoji")
        assertEquals("", safeFileStem("..."))
        assertTrue(fitUtf8Bytes("😀😀", 5) == "😀")
    }

    @Test
    fun picturesVideosAndEverythingElseLandInTheirOwnSharedFolder() {
        assertEquals(SharedDownloadFolder.PICTURES, SharedDownloadFolder.of("a.jpg"))
        assertEquals(SharedDownloadFolder.PICTURES, SharedDownloadFolder.of("a.webp"))
        assertEquals(SharedDownloadFolder.MOVIES, SharedDownloadFolder.of("a.mp4"))
        assertEquals(SharedDownloadFolder.DOWNLOADS, SharedDownloadFolder.of("a.mp3"))
        assertEquals(listOf("Pictures/XTA", "Movies/XTA", "Download/XTA"), SharedDownloadFolder.entries.map { it.relativePath })
        assertEquals("video/quicktime", mimeTypeFor("clip.MOV"))
    }

    @Test
    fun onlyAFinishedBackgroundSaveReportsItsFolder() {
        val saved = DownloadEntry("j", "https://example.org/cat.jpg", "cat.jpg", background = true, savedUri = "content://media/external_primary/images/media/7")
        assertEquals("Pictures/XTA", sharedFolderOf(saved))
        assertEquals("Pictures/XTA/Mika", sharedFolderOf(saved.copy(subfolder = "Mika")))
        assertNull(sharedFolderOf(saved.copy(savedUri = "/storage/emulated/0/Download/cat.jpg")))
        assertNull(sharedFolderOf(saved.copy(background = false)))
    }

    @Test
    fun pixivPicturesAreFetchedWithTheHeadersPixivAsksFor() {
        assertEquals("https://www.pixiv.net/", downloadRequestHeaders("https://i.pximg.net/img-original/120_p0.png")["Referer"])
        assertTrue(downloadRequestHeaders("https://pbs.twimg.com/media/a.jpg").isEmpty())
    }
}
