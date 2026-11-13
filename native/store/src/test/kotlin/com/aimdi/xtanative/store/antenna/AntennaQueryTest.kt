package com.aimdi.xtanative.store.antenna

import kotlin.test.Test
import kotlin.test.assertEquals

class AntennaQueryTest {
    private fun antenna(include: List<String>, exclude: List<String> = emptyList(), scope: AntennaScope = AntennaScope.SEARCH) =
        Antenna("1", "Tech", include, exclude, scope, createdAt = 0)

    @Test
    fun theIncludeTermsAreOredAndTheExcludeTermsNegated() {
        // XTA's antenna_query_test.
        assertEquals("(flutter OR \"dart lang\") -job", AntennaQuery.search(antenna(listOf("flutter", "dart lang"), listOf("job"))))
    }

    @Test
    fun oneTermNeedsNoParenthesesAndNoTermsNoSearch() {
        assertEquals("flutter", AntennaQuery.search(antenna(listOf(" flutter "))))
        assertEquals("-job -\"day rate\"", AntennaQuery.search(antenna(emptyList(), listOf("job", "day rate"))))
        assertEquals("", AntennaQuery.search(antenna(emptyList())))
    }

    @Test
    fun aFollowingAntennaKeepsOnlyWhatFollowedAccountsPosted() {
        // XTA's antenna_query_test: chains by authors a and b, only a followed.
        val posts = listOf("1" to "a", "2" to "b", "3" to null)
        val following = antenna(listOf("x"), scope = AntennaScope.FOLLOWING)
        assertEquals(listOf("1"), AntennaQuery.kept(following, posts, setOf("a")) { it.second }.map { it.first })
        assertEquals(emptyList(), AntennaQuery.kept(following, posts, emptySet()) { it.second }, "nobody followed, nothing kept")
        assertEquals(posts, AntennaQuery.kept(antenna(listOf("x")), posts, emptySet()) { it.second }, "a search antenna keeps everything")
    }

    @Test
    fun theTermsTypedSplitAtCommasAndLineBreaks() {
        assertEquals(listOf("flutter", "dart lang", "kotlin"), Antenna.terms(" flutter, dart lang\nkotlin,, "))
        assertEquals(emptyList(), Antenna.terms(null))
        assertEquals("flutter, dart lang", Antenna.typed(listOf("flutter", "dart lang")))
        assertEquals(AntennaScope.FOLLOWING, AntennaScope.of("following"))
        assertEquals(AntennaScope.SEARCH, AntennaScope.of("anything else"))
    }
}
