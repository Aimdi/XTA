package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class PluginStringsTest {
    /** As the plugins build generates a plugin's Strings: the default language under "", the others by tag. */
    private object Texts : PluginStrings(
        mapOf(
            "" to mapOf(
                "hello" to "Hello",
                "only_english" to "Only in English",
                "posts_by" to "Posts by %1\$s",
                "count#one" to "1 post",
                "count#other" to "%1\$d posts",
            ),
            "de" to mapOf("hello" to "Hallo", "posts_by" to "Beiträge von %1\$s", "count#one" to "1 Beitrag", "count#other" to "%1\$d Beiträge"),
            "pt-BR" to mapOf("hello" to "Olá do Brasil"),
            "pt" to mapOf("hello" to "Olá"),
        ),
    ) {
        val hello = text("hello")
        val postsBy = text("posts_by")
        val count = plural("count")
    }

    @Test
    fun aTextComesInTheReadersLanguage() {
        assertEquals("Hallo", Texts.hello.get("de"))
        assertEquals("Hello", Texts.hello.get("en"))
    }

    @Test
    fun aRegionFallsBackToItsLanguageThenToTheDefault() {
        assertEquals("Olá do Brasil", Texts.hello.get("pt-BR"))
        assertEquals("Olá", Texts.hello.get("pt-PT"))
        assertEquals("Hallo", Texts.hello.get("de-AT"))
        assertEquals("Hello", Texts.hello.get("fr"))
        assertEquals("Only in English", Texts.get("only_english", "de"))
    }

    @Test
    fun tagsAreReadWhateverTheirCaseOrSeparator() {
        assertEquals("Olá do Brasil", Texts.hello.get("pt_br"))
        assertEquals("Hallo", Texts.hello.get("DE"))
    }

    @Test
    fun placeholdersAreFilled() {
        assertEquals("Beiträge von Maya", Texts.postsBy.get("de", "Maya"))
        assertEquals("Posts by %1\$s", Texts.postsBy.get("en"))
    }

    @Test
    fun pluralsTakeOneForOneAndOtherForTheRest() {
        assertEquals("1 Beitrag", Texts.count.get(1, "de", 1))
        assertEquals("3 Beiträge", Texts.count.get(3, "de", 3))
        assertEquals("0 posts", Texts.count.get(0, "en", 0))
    }

    @Test
    fun aTextNobodyWroteIsItsName() {
        assertEquals("missing", Texts.get("missing", "de"))
    }
}
