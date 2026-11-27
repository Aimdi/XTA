package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.ui.settings.SettingsChoices
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The system's language where the C library has no locale for it, and which languages turn the screens right to left. */
class SystemLocaleTest {
    private fun env(vararg pairs: Pair<String, String>): (String) -> String? = pairs.toMap()::get

    @Test
    fun anUngeneratedLocaleStillNamesTheLanguage() {
        val locale = SystemLocale.of(env("LANG" to "ar_EG.UTF-8"), Locale.US)
        assertEquals("ar", locale.language)
        assertEquals("EG", locale.country)
    }

    @Test
    fun theJvmsOwnStandsWhereItAgrees() {
        val jvm = Locale.GERMANY
        assertEquals(jvm, SystemLocale.of(env("LANG" to "de_AT.UTF-8"), jvm))
        assertEquals(jvm, SystemLocale.of(env(), jvm))
        assertEquals(jvm, SystemLocale.of(env("LANG" to "C.UTF-8"), jvm))
        assertEquals(jvm, SystemLocale.of(env("LC_ALL" to "POSIX"), jvm))
    }

    @Test
    fun theVariablesAreReadInTheCLibrarysOrder() {
        val both = env("LC_ALL" to "fr_FR.UTF-8", "LC_MESSAGES" to "ar_EG.UTF-8", "LANG" to "de_DE.UTF-8")
        assertEquals("fr", SystemLocale.of(both, Locale.US).language)
        assertEquals("ar", SystemLocale.of(env("LC_MESSAGES" to "ar_EG.UTF-8", "LANG" to "de_DE.UTF-8"), Locale.US).language)
        assertEquals("ar", SystemLocale.of(env("LC_ALL" to "", "LANG" to "ar"), Locale.US).language)
    }

    @Test
    fun parsingKeepsLanguageAndRegionOnly() {
        assertEquals(Locale("sr", "RS"), SystemLocale.parse("sr_RS.UTF-8@latin"))
        assertEquals(Locale("eo"), SystemLocale.parse("eo"))
        assertNull(SystemLocale.parse("C"))
        assertNull(SystemLocale.parse("POSIX"))
        assertNull(SystemLocale.parse("en_US/broken"))
    }

    @Test
    fun onlyAShownRightToLeftLanguageTurnsTheScreens() {
        assertTrue(AppLocale.rightToLeft("ar"))
        assertFalse(AppLocale.rightToLeft("he"))
        assertFalse(AppLocale.rightToLeft("de"))
        assertFalse(AppLocale.rightToLeft(null))
    }

    @Test
    fun everyRightToLeftTranslationIsShownSo() {
        val translated = SettingsChoices.languages.map { it.code.substringBefore('_') }.filter { it in AppLocale.RIGHT_TO_LEFT }.toSet()
        assertEquals(translated, AppLocale.SHOWN_RIGHT_TO_LEFT)
    }
}
