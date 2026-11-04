package com.aimdi.xtanative.ui.home

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail
import org.w3c.dom.Element

/** The home and group strings, an X post's footer and sheets', and the app's shared ones, in every language of XTA (SCREENS.md's table). */
class HomeStringsTest {
    private val folders = listOf(
        "ar", "be", "ca", "cs", "de", "eo", "es", "et", "eu", "fr", "hi", "id", "it", "ja", "ko", "nl", "pl", "pt", "ro", "ru",
        "tr", "uk", "vi", "nb-rNO", "pt-rBR", "zh-rCN", "zh-rTW", "be-rXL",
    )

    private val resources: File by lazy {
        listOf("src/commonMain/composeResources", "native/ui/src/commonMain/composeResources", "ui/src/commonMain/composeResources")
            .map(::File).firstOrNull { File(it, "values/strings_home.xml").isFile }
            ?: fail("composeResources not found from ${File(".").absolutePath}")
    }

    private val placeholder = Regex("%\\d\\$[sd]")

    private fun placeholders(text: String): Set<String> = placeholder.findAll(text).map { it.value }.toSet()

    /** A string's text, or a plural's text by quantity. */
    private fun entries(file: File): Map<String, Map<String, String>> {
        val nodes = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement.childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.associate { element ->
            val items = element.getElementsByTagName("item")
            element.getAttribute("name") to when (element.tagName) {
                "plurals" -> (0 until items.length).map { items.item(it) as Element }.associate { it.getAttribute("quantity") to it.textContent }
                else -> mapOf("" to element.textContent)
            }
        }
    }

    private fun check(name: String, skipped: Set<String> = emptySet()) {
        val english = entries(File(resources, "values/$name")) - skipped
        folders.forEach { folder ->
            val file = File(resources, "values-$folder/$name")
            assertTrue(file.isFile, "values-$folder/$name is missing")
            val translated = entries(file)
            assertEquals(english.keys, translated.keys, "values-$folder/$name")
            english.forEach { (key, texts) -> checkEntry("values-$folder $key", texts, translated.getValue(key)) }
        }
    }

    private fun checkEntry(where: String, english: Map<String, String>, translated: Map<String, String>) {
        if (english.keys == setOf("")) {
            assertEquals(setOf(""), translated.keys, "$where is a string")
            assertEquals(placeholders(english.getValue("")), placeholders(translated.getValue("")), where)
            return
        }
        assertTrue("other" in translated, "$where has no other")
        val wanted = placeholders(english.getValue("other"))
        translated.forEach { (quantity, text) ->
            // "One vote" or Arabic's dual may say the number counted in words; every other number stays.
            val found = placeholders(text)
            val inWords = quantity in setOf("zero", "one", "two") && wanted.containsAll(found) && wanted.size - found.size <= 1
            assertTrue(found == wanted || inWords, "$where/$quantity: $text")
        }
    }

    @Test
    fun theHomeStringsAreInEveryLanguage() {
        check("strings_home.xml")
    }

    @Test
    fun theSharedStringsAreInEveryLanguage() {
        check("strings.xml", skipped = setOf("app_name"))
    }

    @Test
    fun theGroupStringsAreInEveryLanguage() {
        check("strings_groups.xml")
    }

    @Test
    fun anXPostsFooterSheetsAndPollStringsAreInEveryLanguage() {
        check("strings_post_actions.xml")
    }

    @Test
    fun theSignInReadFailureAndAccountStringsAreInEveryLanguage() {
        listOf("strings_common.xml", "strings_signin.xml", "strings_accounts.xml").forEach { check(it) }
    }
}
