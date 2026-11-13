package com.aimdi.xtanative.store.search

import com.aimdi.xtanative.store.StoreDir
import com.aimdi.xtanative.store.settings.SearchPrefs
import com.aimdi.xtanative.store.settings.SettingsStore
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive

class RecentSearchesTest {
    private val dir = StoreDir(Files.createTempDirectory("searches").toFile())

    @Test
    fun theNewestTenOfEachNetworkAreKeptAndATypedAgainSearchMovesFirst() = runBlocking {
        // XTA's discover_reading_test.
        val searches = RecentSearches(SettingsStore(dir))
        repeat(12) { searches.remember("mastodon", "query-$it") }
        searches.remember("reddit", "gardening")
        searches.remember("mastodon", "QUERY-11")
        val history = searches.history.first()
        assertEquals(10, history["mastodon"].size)
        assertEquals("QUERY-11", history["mastodon"].first())
        assertEquals(listOf("QUERY-11", "query-10", "query-9"), history["mastodon"].take(3), "the old capitals are gone")
        assertEquals(listOf("gardening"), history["reddit"])
        searches.remove("mastodon", "QUERY-11")
        searches.clear("mastodon")
        val reopened = RecentSearches(SettingsStore(dir))
        assertEquals(emptyList(), reopened.of("mastodon").first())
        assertEquals(listOf("gardening"), reopened.of("reddit").first())
    }

    @Test
    fun anEmptyOrOverlongSearchIsNotRemembered() {
        val history = RecentSearchHistory().remember("x", "   ").remember("x", "a".repeat(201)).remember("x", "  dart  ")
        assertEquals(listOf("dart"), history["x"])
    }

    @Test
    fun theHistoryIsReadAsXtaReadsIt() {
        // XTA's search_chrome_test stores exactly this.
        val stored = RecentSearchHistory.parse("""{"x":["flutter"],"reddit":["dart"]}""")
        assertEquals(listOf("flutter"), stored["x"])
        assertEquals(listOf("dart"), stored["reddit"])
        val messy = RecentSearchHistory.parse("""{"x":["", 3, "ok", "${"a".repeat(201)}"],"bad":"text"}""")
        assertEquals(mapOf("x" to listOf("ok")), messy.byScope)
        val many = RecentSearchHistory.parse((1..30).joinToString(",", "{", "}") { "\"s$it\":[\"q\"]" })
        assertEquals(24, many.byScope.size)
        assertTrue(RecentSearchHistory.parse("[1]").byScope.isEmpty())
        assertTrue(RecentSearchHistory.parse("""{"x":["${"a".repeat(100_001)}"]}""").byScope.isEmpty(), "too long to read at all")
    }

    @Test
    fun theyAreKeptInTheSettingsSoTheyTravelInABackup() = runBlocking {
        val settings = SettingsStore(dir)
        RecentSearches(settings).remember("x", "flutter")
        assertEquals("""{"x":["flutter"]}""", settings.get(SearchPrefs.recent))
        assertEquals(JsonPrimitive("""{"x":["flutter"]}"""), settings.exported()!!["search.recent_by_network.v1"])
    }
}
