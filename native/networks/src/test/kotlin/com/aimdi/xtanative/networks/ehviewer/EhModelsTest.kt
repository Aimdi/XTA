package com.aimdi.xtanative.networks.ehviewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class EhModelsTest {
    @Test
    fun theJapaneseTitleOnlyWhenAskedFor() {
        val gallery = EhGallery(gid = 1, token = "t", title = "English", titleJpn = "日本語")
        assertEquals("日本語", gallery.titleFor(preferJapanese = true))
        assertEquals("English", gallery.titleFor(preferJapanese = false))
        assertEquals("English", gallery.copy(titleJpn = "  ").displayTitle)
    }

    @Test
    fun categoriesParseTheSitesLabelsAndXtasStoredNamesAndBuildTheExcludeMask() {
        assertEquals(EhCategory.DOUJINSHI, EhCategory.tryParse("Doujinshi"))
        assertEquals(EhCategory.ARTIST_CG, EhCategory.tryParse("Artist CG"))
        assertEquals(EhCategory.ARTIST_CG, EhCategory.tryParse("artistCg"))
        assertEquals(EhCategory.NON_H, EhCategory.tryParse("Non-H"))
        assertNull(EhCategory.tryParse("Private"))
        assertEquals(1023 - 2 - 4, EhCategory.excludeMask(setOf(EhCategory.DOUJINSHI, EhCategory.MANGA)))
        assertEquals(0, EhCategory.excludeMask(EhCategory.entries.toSet()))
        assertEquals(0, EhCategory.excludeMask(emptySet()), "none chosen reads as all")
    }

    @Test
    fun aTagSearchesForExactlyItself() {
        assertEquals("female:\"big breasts$\"", EhTag.parse("female:big breasts").query)
        assertEquals("\"lolicon$\"", EhTag.parse("lolicon").query)
        assertNull(EhTag.parse("weird:thing").namespace)
        assertEquals("weird:thing", EhTag.parse("weird:thing").raw)
        assertEquals(EhTag(EhNamespace.FEMALE, "big breasts"), EhTag.parse(" Female : big breasts "))
    }

    @Test
    fun groupsFollowTheSiteOrderWithNamespacelessTagsLast() {
        val groups = EhTagGroup.of(listOf("female:a", "artist:b", "x", "language:c", "female:d"))
        assertEquals(listOf(EhNamespace.LANGUAGE, EhNamespace.ARTIST, EhNamespace.FEMALE, null), groups.map { it.namespace })
        assertEquals(listOf("a", "d"), groups[2].tags.map { it.name })
    }

    @Test
    fun translationMarksAreNoLanguage() {
        assertNull(EhGallery(gid = 1, token = "t", title = "x", tags = listOf("language:translated")).tagLanguage)
        assertEquals("japanese", EhGallery(gid = 1, token = "t", title = "x", tags = listOf("language:rewrite", "language:japanese")).tagLanguage)
    }

    @Test
    fun theSpriteCropIsTheTileInSheetPixelsNeverPastTheSheet() {
        assertEquals(EhSpriteCrop(400.0, 0.0, 200.0, 283.0), EhSpriteCrop.of(4000.0, 290.0, offsetX = -400.0, tileWidth = 200.0, tileHeight = 283.0))
        assertEquals(EhSpriteCrop(0.0, 0.0, 200.0, 290.0), EhSpriteCrop.of(4000.0, 290.0, offsetX = 0.0))
        assertEquals(EhSpriteCrop(3900.0, 0.0, 100.0, 290.0), EhSpriteCrop.of(4000.0, 290.0, offsetX = -3900.0))
        assertEquals(EhSpriteCrop(0.0, 0.0, 140.0, 200.0), EhSpriteCrop.of(140.0, 200.0, offsetX = 0.0))
        val tile = EhPreview(pageToken = "t", page = 3, thumbOffsetX = -400.0, thumbWidth = 200.0, thumbHeight = 283.0)
        assertEquals(EhSpriteCrop(400.0, 0.0, 200.0, 283.0), tile.spriteCrop(4000.0, 290.0))
        assertEquals(EhSpriteCrop(0.0, 0.0, 140.0, 200.0), EhPreview(pageToken = "t", page = 1).spriteCrop(140.0, 200.0), "a lone thumbnail is a sheet of one")
    }

    @Test
    fun continuePageComesFromHistoryPastTheFirstPageOnlyInsideTheGallery() {
        val unknownLength = EhGallery(gid = 9, token = "tok", title = "T")
        assertNull(unknownLength.continuePage(null))
        assertNull(unknownLength.continuePage(1))
        assertEquals(7, unknownLength.continuePage(7))
        assertEquals(24, unknownLength.copy(pageCount = 24).continuePage(30))
    }

    @Test
    fun aGalleryAndItsPagesHaveTheirAddressesOnTheSite() {
        val gallery = EhGallery(gid = 9, token = "tok", title = "T")
        assertEquals("https://e-hentai.org/g/9/tok/", gallery.galleryUrl(EhSettings.EHENTAI))
        assertEquals("https://exhentai.org/s/pt3/9-3", gallery.pageUrl(EhSettings.EXHENTAI, pageToken = "pt3", page = 3))
        assertEquals("9/tok", gallery.id)
    }
}
