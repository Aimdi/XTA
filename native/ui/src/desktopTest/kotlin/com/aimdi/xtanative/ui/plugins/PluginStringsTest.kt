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
            "ru" to mapOf("count#one" to "%1\$d пост", "count#few" to "%1\$d поста", "count#many" to "%1\$d постов", "count#other" to "%1\$d поста"),
            "ja" to mapOf("count#other" to "%1\$d件"),
            "ar" to mapOf("count#zero" to "لا منشورات", "count#two" to "منشوران", "count#other" to "%1\$d منشور"),
            "fr" to mapOf("count#one" to "%1\$d publication", "count#other" to "%1\$d publications"),
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
    fun pluralsTakeEachLanguagesOwnCategories() {
        assertEquals(listOf("1 пост", "3 поста", "5 постов", "11 постов", "21 пост", "22 поста"), listOf(1, 3, 5, 11, 21, 22).map { Texts.count.get(it, "ru", it) })
        assertEquals(listOf("لا منشورات", "منشوران"), listOf(0, 2).map { Texts.count.get(it, "ar", it) })
        assertEquals(listOf("few", "many", "other"), listOf(7, 11, 100).map { pluralCategory(it.toLong(), "ar") })
        assertEquals("0 publication", Texts.count.get(0, "fr", 0))
    }

    @Test
    fun aLanguageWithOneFormNeverBorrowsTheDefaultsOne() {
        assertEquals("1件", Texts.count.get(1, "ja", 1))
    }

    @Test
    fun aLanguageWithoutThePluralReadsTheDefaultByItsRule() {
        assertEquals("1 post", Texts.count.get(1, "it", 1))
        assertEquals("2 posts", Texts.count.get(2, "pl", 2))
    }

    @Test
    fun theCategoriesFollowCldrForWholeNumbers() {
        assertEquals(listOf("one", "few", "many", "many", "few"), listOf(1, 2, 5, 12, 22).map { pluralCategory(it.toLong(), "pl") })
        assertEquals(listOf("one", "few", "other"), listOf(1, 3, 5).map { pluralCategory(it.toLong(), "cs") })
        assertEquals(listOf("one", "one", "other"), listOf(0, 1, 2).map { pluralCategory(it.toLong(), "pt-BR") })
        assertEquals(listOf("other", "one"), listOf(0, 1).map { pluralCategory(it.toLong(), "pt-PT") })
        assertEquals(listOf("one", "few", "other"), listOf(1, 19, 20).map { pluralCategory(it.toLong(), "ro") })
        assertEquals("many", pluralCategory(1_000_000, "es"))
        assertEquals("other", pluralCategory(1, "zh-TW"))
    }

    @Test
    fun aTextNobodyWroteIsItsName() {
        assertEquals("missing", Texts.get("missing", "de"))
    }

    private object Plurals : PluginStrings(
        mapOf(
            "" to mapOf("min#one" to "%1\$d min", "min#other" to "%1\$d mins"),
            "ru" to mapOf("min#one" to "%1\$d минута", "min#few" to "%1\$d минуты", "min#many" to "%1\$d минут", "min#other" to "%1\$d минуты"),
            "ar" to mapOf("min#zero" to "لا دقائق", "min#one" to "دقيقة", "min#two" to "دقيقتان", "min#few" to "%1\$d دقائق", "min#many" to "%1\$d دقيقة", "min#other" to "%1\$d دقيقة"),
            "pl" to mapOf("min#one" to "%1\$d minuta", "min#few" to "%1\$d minuty", "min#many" to "%1\$d minut"),
            "fr" to mapOf("min#one" to "%1\$d minute", "min#other" to "%1\$d minutes"),
            "ja" to mapOf("min#other" to "%1\$d分"),
        ),
    ) {
        val min = plural("min")
    }

    @Test
    fun pluralsTakeTheFormTheirLanguageUsesForTheCount() {
        assertEquals(listOf("1 минута", "3 минуты", "5 минут", "11 минут", "21 минута", "22 минуты"), listOf(1, 3, 5, 11, 21, 22).map { Plurals.min.get(it, "ru", it) })
        assertEquals(listOf("لا دقائق", "دقيقة", "دقيقتان"), listOf(0, 1, 2).map { Plurals.min.get(it, "ar", it) })
        assertEquals(listOf("zero", "one", "two", "few", "many", "other"), listOf(0, 1, 2, 3, 11, 100).map { pluralCategory(it.toLong(), "ar") })
        assertEquals(listOf("2 minuty", "5 minut", "12 minut", "22 minuty"), listOf(2, 5, 12, 22).map { Plurals.min.get(it, "pl", it) })
        assertEquals(listOf("0 minute", "1 minute", "2 minutes"), listOf(0, 1, 2).map { Plurals.min.get(it, "fr", it) }, "French counts 0 as one")
        assertEquals("1分", Plurals.min.get(1, "ja", 1))
    }

    @Test
    fun aPluralALanguageLacksComesFromTheDefaultInItsOwnForms() {
        assertEquals(listOf("0 mins", "1 min", "4 mins"), listOf(0, 1, 4).map { Plurals.min.get(it, "uk", it) })
        assertEquals("1 min", Plurals.min.get(1, "pt-BR", 1))
    }

    @Test
    fun theCategoriesFollowCldrForTheAppsLanguages() {
        assertEquals(listOf("one", "few", "other", "other"), listOf(1, 3, 5, 0).map { pluralCategory(it.toLong(), "cs") })
        assertEquals(listOf("one", "few", "few", "other"), listOf(1, 0, 19, 20).map { pluralCategory(it.toLong(), "ro") })
        assertEquals(listOf("one", "one", "other"), listOf(0, 1, 2).map { pluralCategory(it.toLong(), "pt-BR") })
        assertEquals(listOf("other", "one"), listOf(0, 1).map { pluralCategory(it.toLong(), "pt_PT") })
        assertEquals(listOf("one", "other"), listOf(1, 0).map { pluralCategory(it.toLong(), "nb-NO") })
        assertEquals("other", pluralCategory(1, "zh-TW"))
    }
}
