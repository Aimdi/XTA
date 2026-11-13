package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivNovelMarkup
import kotlin.test.Test
import kotlin.test.assertEquals

class PixivNovelExportTest {
    private val markup = "[chapter:始まり]\n[[rb:青空>あおぞら]]の下で\n[newpage]\n終わり"

    @Test
    fun theFileIsNamedAfterTheNovel() {
        assertEquals("青空の下で.txt", PixivNovelExport.fileName("青空の下で", 42))
        assertEquals("a_b_c.txt", PixivNovelExport.fileName("a/b:c", 42))
    }

    @Test
    fun aTitleWithNothingAFileCanHoldNamesItByItsId() {
        assertEquals("pixiv-novel-42.txt", PixivNovelExport.fileName("  ...  ", 42))
        assertEquals("pixiv-novel-42.txt", PixivNovelExport.fileName("", 42))
    }

    @Test
    fun plainTextLeavesTheMarkupOut() {
        val text = PixivNovelExport.text(markup, PixivNovelMarkup.parse(markup), PixivNovelExportFormat.PLAIN)
        assertEquals("始まり\n青空(あおぞら)の下で\n\n終わり", text)
    }

    @Test
    fun theMarkupIsKeptAsPixivStoresIt() {
        assertEquals(markup, PixivNovelExport.text(markup, PixivNovelMarkup.parse(markup), PixivNovelExportFormat.MARKUP))
    }
}
