package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ThreadsSearchSuggestionsTest {
    private val accounts = listOf(
        ThreadsSuggestion.Account("zuck", "Mark Zuckerberg", null),
        ThreadsSuggestion.Account("nasa", "NASA", null),
        ThreadsSuggestion.Account("cat_kilnworks", "Cat", null),
    )
    private val recent = listOf("kiln firing", "#Ceramics", "zuck")
    private val topics = listOf("Kilns", "Space")

    private fun texts(typed: String) = ThreadsSuggestions.of(typed, recent, accounts, topics).map { it.text }

    @Test
    fun wordsThatStartWhatWasTypedComeBeforeThoseThatOnlyHoldIt() {
        assertEquals(listOf("kiln firing", "@cat_kilnworks", "#Kilns"), texts("kil"))
        assertEquals(listOf("zuck", "@zuck"), texts("zu"))
        assertEquals(listOf("@cat_kilnworks"), texts("works"), "inside a word comes last, and still comes")
    }

    @Test
    fun anAtAsksForAccountsAndAHashForTopics() {
        assertEquals(listOf("@zuck"), texts("@zu"))
        assertEquals(listOf("#Kilns"), texts("#ki"))
        assertEquals(listOf("#Ceramics"), texts("#cer"), "a tag searched before is a topic too")
    }

    @Test
    fun nothingForABlankFieldOrForWhatIsTypedAlready() {
        assertTrue(texts("  ").isEmpty())
        assertTrue(texts("@").isEmpty())
        assertEquals(listOf("@zuck"), texts("zuck"), "the recent zuck is what is typed")
    }

    @Test
    fun theListStaysShortAndNamesEachOnce() {
        val many = (1..20).map { "kiln $it" }
        val shown = ThreadsSuggestions.of("kiln", many + "KILN 1", accounts, topics)
        assertEquals(ThreadsSuggestions.LIMIT, shown.size)
        assertEquals(shown.map { it.text.lowercase() }.distinct().size, shown.size)
    }
}
