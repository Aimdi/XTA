package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

/** A plugin's plurals in the table they come from: its forms, counted as that table's language counts. */
class PluralTextsTest {
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
