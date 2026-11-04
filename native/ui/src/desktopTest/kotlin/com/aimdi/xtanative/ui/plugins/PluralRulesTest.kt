package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class PluralRulesTest {
    private fun forms(language: String, vararg counts: Int) = counts.map { PluralRules.category(language, it) }

    @Test
    fun oneAndOtherForEnglishAndTheLanguagesThatCountSo() {
        assertEquals(listOf("other", "one", "other", "other"), forms("en", 0, 1, 2, 21))
        assertEquals(listOf("other", "one", "other"), forms("de", 0, 1, 5))
        assertEquals(listOf("one", "other"), forms("tlh", 1, 2))
    }

    @Test
    fun russianUkrainianAndBelarusianCountOneFewAndMany() {
        assertEquals(listOf("one", "few", "many", "many", "one", "few", "many", "many"), forms("ru", 1, 3, 5, 11, 21, 22, 25, 112))
        assertEquals(forms("ru", 1, 2, 5, 11, 101), forms("uk", 1, 2, 5, 11, 101))
        assertEquals(forms("ru", 1, 2, 5, 11, 101), forms("be", 1, 2, 5, 11, 101))
    }

    @Test
    fun polishCzechAndRomanianHaveTheirOwnFew() {
        assertEquals(listOf("one", "few", "many", "many", "many", "few"), forms("pl", 1, 2, 5, 12, 21, 22))
        assertEquals(listOf("one", "few", "other", "other"), forms("cs", 1, 4, 5, 0))
        assertEquals(listOf("one", "few", "few", "other", "few"), forms("ro", 1, 0, 19, 20, 101))
    }

    @Test
    fun arabicHasSixAndTheLanguagesWithoutPluralsOne() {
        assertEquals(listOf("zero", "one", "two", "few", "many", "other"), forms("ar", 0, 1, 2, 3, 11, 100))
        assertEquals(listOf("other", "other"), forms("ja", 1, 2))
        assertEquals(listOf("other", "other"), forms("zh-CN", 1, 2))
    }

    @Test
    fun zeroIsOneInFrenchHindiAndBrazilianPortugueseOnly() {
        assertEquals("one", PluralRules.category("fr", 0))
        assertEquals("one", PluralRules.category("hi", 0))
        assertEquals("one", PluralRules.category("pt-BR", 0))
        assertEquals("other", PluralRules.category("pt", 0))
        assertEquals("one", PluralRules.category("pt_PT", 1))
    }

    private object Texts : PluginStrings(
        mapOf(
            "" to mapOf("count#one" to "%1\$d comment", "count#other" to "%1\$d comments"),
            "ru" to mapOf(
                "count#one" to "%1\$d комментарий",
                "count#few" to "%1\$d комментария",
                "count#many" to "%1\$d комментариев",
                "count#other" to "%1\$d комментария",
            ),
            "fr" to mapOf("other_text" to "Bonjour"),
            "ja" to mapOf("count#other" to "%1\$d 件"),
        ),
    ) {
        val count = plural("count")
    }

    @Test
    fun aPluralTakesEachLanguagesForm() {
        assertEquals(listOf("1 комментарий", "3 комментария", "5 комментариев", "21 комментарий"), listOf(1, 3, 5, 21).map { Texts.count.get(it, "ru", it) })
        assertEquals("1 件", Texts.count.get(1, "ja", 1))
    }

    @Test
    fun aLanguageWithoutThePluralCountsAsTheDefaultDoes() {
        // French says "one" for 0, but the English it falls back to says "0 comments".
        assertEquals("0 comments", Texts.count.get(0, "fr", 0))
        assertEquals("1 comment", Texts.count.get(1, "fr", 1))
        assertEquals("missing", Texts.plural("missing", 2, "ru"))
    }
}
