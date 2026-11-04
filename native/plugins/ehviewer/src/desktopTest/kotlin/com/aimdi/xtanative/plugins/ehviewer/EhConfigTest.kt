package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.networks.ehviewer.EhCategory
import com.aimdi.xtanative.store.settings.EhReadingMode
import com.aimdi.xtanative.store.settings.EhViewerPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EhConfigTest {
    @Test
    fun categoriesAreKeptUnderXtasNamesAndReadBack() {
        val text = EhConfig.categoriesText(setOf(EhCategory.ARTIST_CG, EhCategory.DOUJINSHI, EhCategory.NON_H))
        assertEquals("""["doujinshi","artistCg","nonH"]""", text)
        val settings = Settings().with(EhViewerPrefs.categories, text)
        assertEquals(setOf(EhCategory.DOUJINSHI, EhCategory.ARTIST_CG, EhCategory.NON_H), EhConfig.categories(settings))
        EhCategory.entries.forEach { assertEquals(it, EhCategory.tryParse(EhConfig.storedName(it)), it.name) }
    }

    @Test
    fun noneStoredIsEveryCategoryAndTheLastOneChosenStays() {
        assertEquals(EhCategory.entries.toSet(), EhConfig.categories(Settings()))
        val manga = Settings().with(EhViewerPrefs.categories, EhConfig.categoriesText(setOf(EhCategory.MANGA)))
        assertEquals(setOf(EhCategory.MANGA), EhConfig.categories(EhConfig.withCategory(manga, EhCategory.MANGA, chosen = false)))
        val two = EhConfig.withCategory(manga, EhCategory.COSPLAY, chosen = true)
        assertEquals(setOf(EhCategory.MANGA, EhCategory.COSPLAY), EhConfig.categories(two))
        assertEquals(setOf(EhCategory.COSPLAY), EhConfig.categories(EhConfig.withCategory(two, EhCategory.MANGA, chosen = false)))
    }

    @Test
    fun theClientReadsWithTheCookiesHereAndAlwaysAnswersTheContentWarning() {
        val settings = Settings().with(EhViewerPrefs.useExhentai, true)
        val client = EhConfig.client(settings, "ipb_member_id=1; ipb_pass_hash=abc")

        assertTrue(client.adultConsent, "XTA always answers the site's content warning")
        assertEquals("https://exhentai.org", client.host)
        assertEquals("https://e-hentai.org", EhConfig.client(settings, "").host, "ExHentai only with cookies")
    }

    @Test
    fun theReadingPreferencesHaveXtasDefaults() {
        assertEquals(EhReadingMode.LEFT_TO_RIGHT, EhConfig.readingMode(Settings()))
        assertTrue(EhConfig.preferJapanese(Settings()))
        assertTrue(EhConfig.keepScreenOn(Settings()))
        assertEquals(EhReadingMode.VERTICAL, EhConfig.readingMode(Settings().with(EhViewerPrefs.readingMode, EhReadingMode.VERTICAL)))
    }
}
