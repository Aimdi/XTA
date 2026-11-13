package com.aimdi.xtanative.ui.settings

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Element

/**
 * XTA's Belarusian in Latin script: the language setting files it under a
 * region of its own, where the app and every plugin keep each of their
 * strings in Latin letters (native/tool/belarusian_latin.py fills in what is
 * missing).
 */
class LatinBelarusianTest {
    private val fill = "run python3 -I native/tool/belarusian_latin.py"

    private val folder: String by lazy {
        val (language, region) = SettingsChoices.localeOf("be_Latn") ?: fail("be_Latn reads no resources")
        "values-$language-r$region"
    }

    private fun found(vararg places: String): File =
        places.map(::File).firstOrNull { it.isDirectory } ?: fail("${places.first()} not found from ${File(".").absolutePath}")

    private val resources: File by lazy {
        found("src/commonMain/composeResources", "native/ui/src/commonMain/composeResources", "ui/src/commonMain/composeResources")
    }

    private val plugins: File by lazy { found("../plugins", "native/plugins", "plugins") }

    /** A strings file's texts by name, a plural's items as "name#quantity". */
    private fun texts(file: File): Map<String, String> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.flatMap { element ->
            val name = element.getAttribute("name")
            val items = element.getElementsByTagName("item")
            if (element.tagName == "plurals") {
                (0 until items.length).map { items.item(it) as Element }.map { "$name#${it.getAttribute("quantity")}" to it.textContent }
            } else {
                listOf(name to element.textContent)
            }
        }.toMap()
    }

    private fun names(texts: Map<String, String>): Set<String> = texts.keys.map { it.substringBefore('#') }.toSet()

    private val placeholder = Regex("%\\d\\$[sd]")

    private fun placeholders(text: String?): Set<String> = placeholder.findAll(text.orEmpty()).map { it.value }.toSet()

    /** A text's placeholders are its English's (a plural's, its other's); a zero, one or two may say one of them in words. */
    private fun placeholdersFit(name: String, text: String, english: Map<String, String>): Boolean {
        val wanted = placeholders(if ('#' in name) english[name.substringBefore('#') + "#other"] else english[name])
        val found = placeholders(text)
        val inWords = name.substringAfter('#', "") in setOf("zero", "one", "two") && wanted.containsAll(found) && wanted.size - found.size <= 1
        return found == wanted || inWords
    }

    /** Each strings file of [strings]' values/ has its Latin copy, with every name but [skipped], no Cyrillic letter and the English's placeholders. */
    private fun checkLatin(strings: File, skipped: Set<String> = emptySet()) {
        val english = strings.resolve("values").listFiles { f -> f.extension == "xml" }.orEmpty().sorted()
        assertTrue(english.isNotEmpty(), "$strings has no strings")
        english.forEach { file ->
            val latin = strings.resolve("$folder/${file.name}")
            assertTrue(latin.isFile, "$latin is missing: $fill")
            val source = texts(file)
            val written = texts(latin)
            assertEquals(names(source) - skipped, names(written), "$latin: $fill")
            written.forEach { (name, text) ->
                assertTrue(text.none { it in '\u0400'..'\u04ff' }, "$latin $name is not in Latin letters: $text")
                assertTrue(placeholdersFit(name, text, source), "$latin $name: $text")
            }
        }
    }

    @Test
    fun theLanguageSettingReadsAFolderOfTheApps() {
        assertEquals("values-be-rXL", folder)
        assertTrue(resources.resolve(folder).isDirectory, "$folder is not among the app's resources")
    }

    @Test
    fun everyStringOfTheAppIsInLatinBelarusian() {
        checkLatin(resources, skipped = setOf("app_name"))
    }

    @Test
    fun everyStringOfEveryPluginIsInLatinBelarusian() {
        val all = plugins.listFiles { f -> f.resolve("strings/values").isDirectory }.orEmpty().sorted()
        assertTrue(all.size >= 17, "plugins with strings: ${all.map { it.name }}")
        all.forEach { checkLatin(it.resolve("strings")) }
    }
}
