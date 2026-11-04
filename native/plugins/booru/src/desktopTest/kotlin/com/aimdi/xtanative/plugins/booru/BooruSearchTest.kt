package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruPostPage
import com.aimdi.xtanative.networks.booru.BooruTagCategory
import com.aimdi.xtanative.networks.booru.BooruTagSuggestion
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class BooruSearchTest {
    /** A search whose host suggests with [answer], whose engine knows `rating:`, and which notes what it ran and remembered. */
    private class Fixture(scope: CoroutineScope, answer: suspend (String) -> List<BooruTagSuggestion> = { emptyList() }) {
        val asked = mutableListOf<String>()
        val ran = mutableListOf<String>()
        val remembered = mutableListOf<String>()
        val search = BooruSearch(
            scope = scope,
            suggest = { prefix ->
                asked += prefix
                answer(prefix)
            },
            metatags = { input -> if (input.startsWith("rat")) listOf(BooruTagSuggestion("rating:")) else emptyList() },
            remember = { remembered += it },
            results = { query, tags ->
                ran += query
                BooruResults(query, tags, BooruFeed(scope, { "site" }) { BooruPostPage(emptyList(), next = null) }, MutableStateFlow(null))
            },
        )
    }

    @Test
    fun typedTagsBecomeChipsAtEachSpace(): Unit = runBlocking {
        val search = Fixture(this).search
        assertEquals("sol", search.type("blue_eyes sol"))
        assertEquals(listOf("blue_eyes"), search.state.value.tags)
        assertEquals("blue_eyes sol", search.state.value.text)
        search.close()
    }

    @Test
    fun backspaceInAnEmptyFieldTakesTheLastChip(): Unit = runBlocking {
        val search = Fixture(this).search
        search.type("solo smile ")
        assertTrue(search.removeLast())
        assertEquals(listOf("solo"), search.state.value.tags)
        search.type("x")
        assertFalse(search.removeLast())
        search.close()
    }

    @Test
    fun aSuggestionTakesThePlaceOfTheTypedTextKeepingItsOperator(): Unit = runBlocking {
        val search = Fixture(this).search
        search.type("-blu")
        assertEquals("", search.pick(BooruTagSuggestion("blue_eyes", category = BooruTagCategory.GENERAL)))
        assertEquals(listOf("-blue_eyes"), search.state.value.tags)
        assertEquals(BooruTagCategory.GENERAL, search.state.value.categories["blue_eyes"])
        search.close()
    }

    @Test
    fun aMetatagStaysInTheFieldToBeGivenItsValue(): Unit = runBlocking {
        val search = Fixture(this).search
        search.type("rat")
        assertEquals(listOf("rating:"), search.state.value.suggestions.map { it.name })
        assertEquals("rating:", search.pick(BooruTagSuggestion("rating:")))
        assertEquals(emptyList(), search.state.value.tags)
        assertEquals("rating:", search.state.value.input)
        search.close()
    }

    @Test
    fun theHostsSuggestionsComeAfterAPauseForWhatIsStillTyped(): Unit = runBlocking {
        val fixture = Fixture(this) { prefix -> listOf(BooruTagSuggestion(prefix + "_found")) }
        fixture.search.type("blu")
        assertEquals(emptyList(), fixture.search.state.value.suggestions)
        delay(BooruSearch.DEBOUNCE_MILLIS / 2)
        fixture.search.type("blue")
        withTimeout(5_000) { fixture.search.state.first { it.suggestions.isNotEmpty() } }
        assertEquals(listOf("blue"), fixture.asked)
        assertEquals(listOf("blue_found"), fixture.search.state.value.suggestions.map { it.name })
        fixture.search.close()
    }

    @Test
    fun aSearchRunsTheTagsAndIsRemembered(): Unit = runBlocking {
        val fixture = Fixture(this)
        fixture.search.type("solo  smi")
        fixture.search.search()
        val state = fixture.search.state.value
        assertEquals("solo smi", state.query)
        assertTrue(state.showsResults)
        assertEquals(listOf("solo smi"), fixture.remembered)
        assertEquals(listOf("solo smi"), fixture.ran)
        assertEquals("solo smi", fixture.search.current.value?.query)
        fixture.search.close()
    }

    @Test
    fun aTagAddedFromThePostsRunsTheSearchAgain(): Unit = runBlocking {
        val fixture = Fixture(this)
        fixture.search.run("solo")
        fixture.search.refine("-comic")
        assertEquals(listOf("solo", "solo -comic"), fixture.ran)
        fixture.search.close()
    }

    @Test
    fun closingTheEditorGoesBackToTheLastSearch(): Unit = runBlocking {
        val search = Fixture(this).search
        assertFalse(search.closeEditor())
        search.run("solo smile")
        search.edit()
        search.type("comic ")
        assertEquals(listOf("solo", "smile", "comic"), search.state.value.tags)
        assertTrue(search.closeEditor())
        assertEquals(listOf("solo", "smile"), search.state.value.tags)
        assertFalse(search.state.value.editing)
        search.close()
    }

    @Test
    fun aChipCanBePutBackInTheFieldToBeChanged(): Unit = runBlocking {
        val search = Fixture(this).search
        search.type("solo smile ")
        assertEquals("solo", search.reopen("solo"))
        assertEquals(listOf("smile"), search.state.value.tags)
        assertEquals("solo", search.state.value.input)
        search.close()
    }
}
