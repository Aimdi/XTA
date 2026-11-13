package com.aimdi.xtanative.plugins.pixiv

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.aimdi.xtanative.networks.pixiv.PixivIllust
import com.aimdi.xtanative.networks.pixiv.PixivTag
import com.aimdi.xtanative.store.settings.PixivDetailLayout
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's pixiv_copy_info_test, pixiv_detail_caption_test, pixiv_detail_parts_test and pixiv_detail_split_test. */
class PixivDetailTest {
    private val work = PixivIllust(
        id = 120, title = "Sommerfest", caption = "", type = "illust", thumbnailUrl = "https://i.pximg.net/120.jpg", pageCount = 1,
        userId = 42, userName = "Mika", userAccount = "mika", tags = listOf(PixivTag("cat"), PixivTag("夏")),
    )

    // pixiv_copy_info_test

    @Test
    fun copyInfoFillsEveryPlaceholder() {
        assertEquals("Sommerfest|120|42|Mika|#cat #夏", PixivCopyInfo.filled("{title}|{illust_id}|{user_id}|{user_name}|{tags}", work))
        assertEquals("https://www.pixiv.net/artworks/120", PixivCopyInfo.filled(PixivCopyInfo.ARTWORK_URL, work))
        assertEquals("https://www.pixiv.net/users/42", PixivCopyInfo.filled(PixivCopyInfo.USER_URL, work))
    }

    @Test
    fun unknownBracesAndATitleThatLooksLikeAPlaceholderStayAsWritten() {
        assertEquals("{user_name} by Mika {page}", PixivCopyInfo.filled("{title} by {user_name} {page}", work.copy(title = "{user_name}")))
    }

    @Test
    fun theDefaultTemplateNamesTitleArtistAndWorkId() {
        assertEquals("Title: Sommerfest\nArtist: Mika\nWork ID: 120", PixivCopyInfo.filled(defaultCopyTemplate("en"), work))
    }

    @Test
    fun theEditorInsertsPlaceholdersWhereTheCursorIs() {
        val typed = TextFieldValue("ID ", TextRange(3))
        val withId = inserted(typed, "{illust_id}")
        assertEquals("ID {illust_id}", withId.text)
        assertEquals(TextRange(14), withId.selection)
        assertEquals("ID {illust_id}${PixivCopyInfo.ARTWORK_URL}", inserted(withId, PixivCopyInfo.ARTWORK_URL).text)
        assertEquals("X{title}", inserted(TextFieldValue("Xy", TextRange(1, 2)), "{title}").text, "a selection is replaced")
        assertEquals(defaultCopyTemplate("en"), PixivCopyInfo.template(Settings().with(PixivPrefs.copyTemplate, ""), defaultCopyTemplate("en")), "reset stores nothing and shows the default")
    }

    // pixiv_detail_caption_test

    @Test
    fun aCaptionKeepsLineBreaksBoldAndLinksAndReadsOtherTagsForTheirText() {
        val parts = PixivHtml.parts("""Hello<br />see <a href="pixiv://users/42" target="_blank">my page</a> and <strong>bold</strong><span> span</span>""")
        assertEquals("Hello\nsee my page and bold span", parts.joinToString("") { it.text })
        assertEquals("pixiv://users/42", parts.single { it.text == "my page" }.href)
        assertTrue(parts.single { it.text == "bold" }.bold)
        assertEquals(1, parts.count { it.href != null })
    }

    @Test
    fun pixivPathsAreMadeAbsoluteAndJumpPhpUnwrapped() {
        assertEquals("https://www.pixiv.net/artworks/5", PixivHtml.href("/artworks/5"))
        assertEquals("https://example.com/a", PixivHtml.href("/jump.php?https%3A%2F%2Fexample.com%2Fa"))
        assertEquals("https://example.com", PixivHtml.href("/jump.php?url=https%3A%2F%2Fexample.com"))
        assertNull(PixivHtml.href("javascript:alert(1)"))
        assertNull(PixivHtml.href(""))
        assertNull(PixivHtml.href(null))
    }

    @Test
    fun anEmptyOrBrokenCaptionIsNoPartsRatherThanAnError() {
        assertTrue(PixivHtml.parts("  ").isEmpty())
        assertEquals("", PixivHtml.parts("""<a href=">broken""").joinToString("") { it.text })
        assertTrue(PixivHtml.parts("<b>open").single().bold)
    }

    @Test
    fun aPixivLinkInTheCaptionOpensInTheAppAndAnyOtherAsTheAppOpensLinks() {
        val host = PixivTestHost()
        withRuntime(host) { runtime ->
            runtime.openHref("pixiv://users/42")
            runtime.openHref("https://example.com/a")
        }
        assertEquals(listOf(PixivRoutes.of(PixivRoute.User(42))), host.opened)
        assertEquals(listOf("https://example.com/a"), host.links)
    }

    // pixiv_detail_parts_test

    @Test
    fun theMenuOffersEveryEntryAndSavingAllOnlyForManyPages() {
        assertEquals(
            listOf(PixivWorkMenu.BOOKMARK, PixivWorkMenu.COPY_LINK, PixivWorkMenu.COPY_INFO, PixivWorkMenu.OPEN, PixivWorkMenu.MUTE, PixivWorkMenu.MORE),
            PixivWorkMenu.of(work),
        )
        val pages = work.copy(pageCount = 3, pageUrls = listOf("a", "b", "c"))
        assertEquals(PixivWorkMenu.DOWNLOAD_ALL, PixivWorkMenu.of(pages).first())
    }

    // pixiv_detail_split_test

    @Test
    fun automaticSplitsFrom840StackedNeverSideBySideWhereverBothPanesFit() {
        assertFalse(PixivLayout.splits(839f, PixivDetailLayout.AUTO))
        assertTrue(PixivLayout.splits(840f, PixivDetailLayout.AUTO))
        assertFalse(PixivLayout.splits(2000f, PixivDetailLayout.VERTICAL))
        assertFalse(PixivLayout.splits(575f, PixivDetailLayout.SPLIT))
        assertTrue(PixivLayout.splits(576f, PixivDetailLayout.SPLIT))
    }

    @Test
    fun theDetailsKeep320AndThePictures240WhateverTheDividerSays() {
        assertEquals(500f, PixivLayout.splitImageWidth(1000f, 0.5f))
        assertEquals(840f - PixivLayout.SPLIT_GUTTER - PixivLayout.SPLIT_MIN_INFO, PixivLayout.splitImageWidth(840f, 0.9f))
        assertEquals(PixivLayout.SPLIT_MIN_IMAGES, PixivLayout.splitImageWidth(840f, 0.1f))
    }

    @Test
    fun theLayoutAndDividerReadFromTheSettingsWithDefaultsForAnythingUnusable() {
        fun stored(key: String, value: PrefValue) = Settings(mapOf(key to value))
        assertEquals(PixivDetailLayout.AUTO, Settings()[PixivPrefs.detailLayout])
        assertEquals(PixivDetailLayout.SPLIT, stored(PixivPrefs.detailLayout.key, PrefValue.Text("split"))[PixivPrefs.detailLayout])
        assertEquals(PixivDetailLayout.AUTO, stored(PixivPrefs.detailLayout.key, PrefValue.Text("odd"))[PixivPrefs.detailLayout])
        assertEquals(0.5, stored(PixivPrefs.detailSplit.key, PrefValue.Real(0.5))[PixivPrefs.detailSplit])
        assertEquals(0.9, stored(PixivPrefs.detailSplit.key, PrefValue.Whole(3))[PixivPrefs.detailSplit])
        assertEquals(0.64, stored(PixivPrefs.detailSplit.key, PrefValue.Text("x"))[PixivPrefs.detailSplit])
    }
}
