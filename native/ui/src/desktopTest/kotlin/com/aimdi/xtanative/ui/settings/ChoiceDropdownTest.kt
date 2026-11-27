package com.aimdi.xtanative.ui.settings

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The desktop's drop-down list of a choice, and the language list's filter and System entry. */
class ChoiceDropdownTest {
    private val window = IntSize(1600, 900)
    private val popup = IntSize(300, 400)

    @Test
    fun itDropsUnderTheValueEndToEnd() {
        val anchor = IntRect(1300, 80, 1440, 110)
        assertEquals(IntOffset(1140, 114), ChoiceDropdownPlace.position(anchor, window, popup, rtl = false, gap = 4, margin = 8))
        assertEquals(IntOffset(1292, 114), ChoiceDropdownPlace.position(anchor, window, popup, rtl = true, gap = 4, margin = 8))
    }

    @Test
    fun itRisesWhereThereIsNoRoomUnder() {
        val anchor = IntRect(600, 700, 740, 730)
        assertEquals(296, ChoiceDropdownPlace.position(anchor, window, popup, rtl = false, gap = 4, margin = 8).y)
    }

    @Test
    fun itStaysInsideTheWindow() {
        val anchor = IntRect(10, 300, 100, 330)
        assertEquals(8, ChoiceDropdownPlace.position(anchor, window, popup, rtl = false, gap = 4, margin = 8).x)
        val short = IntSize(1600, 420)
        assertEquals(12, ChoiceDropdownPlace.position(IntRect(600, 200, 740, 230), short, popup, rtl = false, gap = 4, margin = 8).y)
    }

    @Test
    fun itKeepsItsSizeWhateverTheFilterLeaves() {
        assertEquals(ChoiceDropdownPlace.MAX_ROWS, ChoiceDropdownPlace.rows(30))
        assertEquals(3, ChoiceDropdownPlace.rows(3))
        assertEquals(1, ChoiceDropdownPlace.rows(0))
    }

    @Test
    fun theArrowsMoveTheHighlightWithinTheList() {
        assertEquals(1, ChoiceDropdownPlace.moved(0, 5, Key.DirectionDown))
        assertEquals(4, ChoiceDropdownPlace.moved(4, 5, Key.DirectionDown))
        assertEquals(0, ChoiceDropdownPlace.moved(0, 5, Key.DirectionUp))
        assertEquals(29, ChoiceDropdownPlace.moved(25, 30, Key.PageDown))
        assertNull(ChoiceDropdownPlace.moved(0, 0, Key.DirectionDown))
        assertNull(ChoiceDropdownPlace.moved(0, 5, Key.A))
    }

    private fun filter(query: String): List<String> {
        val codes = SettingsChoices.languages.map { it.code }
        val terms = codes.map { SettingsChoices.languageTerms(it, Locale.GERMAN) }
        return ChoiceList.matchingAny(terms, query).map { codes[it] }
    }

    @Test
    fun aLanguageIsFoundByItsEnglishAndNativeNameAndCode() {
        assertEquals(listOf("ar"), filter("arab"))
        assertTrue("ar" in filter("ara"))
        assertEquals(listOf("de"), filter("deutsch"))
        assertTrue("de" in filter("german"))
        assertTrue("ja" in filter("japanese"))
        assertTrue("ja" in filter("日本"))
        assertTrue("nb_NO" in filter("nb"))
        assertTrue("zh_Hant" in filter("chinese"))
    }

    @Test
    fun aLanguageIsFoundByItsNameInTheReadersLanguage() {
        assertTrue("fr" in filter("französisch"))
    }

    @Test
    fun theSystemsLanguageIsTheOneTheAppShows() {
        assertEquals("de", SettingsChoices.shownLanguage(Locale.GERMANY))
        assertEquals("ar", SettingsChoices.shownLanguage(Locale("ar", "EG")))
        assertEquals("pt_BR", SettingsChoices.shownLanguage(Locale("pt", "BR")))
        assertEquals("pt", SettingsChoices.shownLanguage(Locale("pt", "PT")))
        assertEquals("zh_Hant", SettingsChoices.shownLanguage(Locale.TAIWAN))
        assertEquals("zh_Hans", SettingsChoices.shownLanguage(Locale.CHINA))
        assertEquals("nb_NO", SettingsChoices.shownLanguage(Locale("nb", "NO")))
        assertEquals("id", SettingsChoices.shownLanguage(Locale("in", "ID")))
        assertEquals("be_Latn", SettingsChoices.shownLanguage(Locale("be", "XL")))
        assertEquals("en", SettingsChoices.shownLanguage(Locale("he", "IL")))
        assertEquals("en", SettingsChoices.shownLanguage(Locale("sv", "SE")))
    }
}
