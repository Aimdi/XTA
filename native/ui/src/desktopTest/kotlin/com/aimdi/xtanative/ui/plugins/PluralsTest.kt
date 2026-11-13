package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralsTest {
    private fun categories(language: String, vararg counts: Long): List<String> = counts.map { pluralCategory(it, language) }

    @Test
    fun englishAndGermanTakeOneForOneAlone() {
        assertEquals(listOf("other", "one", "other", "other"), categories("en", 0, 1, 2, 21))
        assertEquals(listOf("other", "one", "other"), categories("de-AT", 0, 1, 5))
    }

    @Test
    fun theEastSlavicLanguagesAndPolishCountInFewAndMany() {
        assertEquals(listOf("many", "one", "few", "many", "many", "one", "few", "many"), categories("ru", 0, 1, 3, 5, 11, 21, 22, 25))
        assertEquals(categories("ru", 0, 1, 3, 5, 11, 21, 22, 112), categories("uk", 0, 1, 3, 5, 11, 21, 22, 112))
        assertEquals(listOf("one", "few", "many", "many", "few", "many"), categories("pl", 1, 2, 5, 12, 22, 21))
    }

    @Test
    fun czechArabicAndRomanianHaveTheirOwnForms() {
        assertEquals(listOf("other", "one", "few", "other"), categories("cs", 0, 1, 4, 5))
        assertEquals(listOf("zero", "one", "two", "few", "many", "other"), categories("ar", 0, 1, 2, 3, 11, 100))
        assertEquals(listOf("few", "one", "few", "other"), categories("ro", 0, 1, 19, 20))
    }

    @Test
    fun frenchAndBrazilianPortugueseCallZeroOneAndMillionsMany() {
        assertEquals(listOf("one", "one", "other", "many"), categories("fr", 0, 1, 2, 1_000_000))
        assertEquals(listOf("one", "one", "other"), categories("pt-BR", 0, 1, 2))
        assertEquals(listOf("other", "one"), categories("pt-PT", 0, 1))
        assertEquals(listOf("one", "one", "other"), categories("hi", 0, 1, 2))
    }

    @Test
    fun languagesWithoutPluralsAlwaysTakeOther() {
        assertEquals(listOf("other", "other"), categories("ja", 1, 2))
        assertEquals(listOf("other", "other"), categories("zh-Hant-TW", 1, 2))
    }

    private object Texts : PluginStrings(
        mapOf(
            "" to mapOf("pages#one" to "1 page", "pages#other" to "%1\$d pages"),
            "ru" to mapOf("pages#one" to "%1\$d страница", "pages#few" to "%1\$d страницы", "pages#many" to "%1\$d страниц", "pages#other" to "%1\$d страницы"),
            "fr" to mapOf("pages#one" to "%1\$d page", "pages#other" to "%1\$d pages"),
        ),
    ) {
        val pages = plural("pages")
    }

    @Test
    fun aPluralTakesTheFormItsLanguageUsesElseOther() {
        assertEquals("3 страницы", Texts.pages.get(3, "ru", 3))
        assertEquals("5 страниц", Texts.pages.get(5, "ru", 5))
        assertEquals("21 страница", Texts.pages.get(21, "ru", 21))
        assertEquals("0 page", Texts.pages.get(0, "fr", 0), "French says one of zero")
        assertEquals("1000000 pages", Texts.pages.get(1_000_000, "fr", 1_000_000), "a form the language does not write falls back to other")
    }
}
