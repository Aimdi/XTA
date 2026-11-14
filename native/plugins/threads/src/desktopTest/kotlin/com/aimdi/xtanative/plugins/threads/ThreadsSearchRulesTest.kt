package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsDiscovery
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/** What the search screens decide about people: where a search is kept, and which accounts are offered to try. */
class ThreadsSearchRulesTest {
    @Test
    fun aGuestsPeopleSearchIsKeptApartFromASessions() {
        assertEquals("threads-people-kiln", ThreadsSearchRules.peopleKey("kiln", session = true), "the name every session search shares, as before")
        assertNotEquals(ThreadsSearchRules.peopleKey("kiln", session = true), ThreadsSearchRules.peopleKey("kiln", session = false))
    }

    @Test
    fun withNoRecommendationTheAccountsToTryAreTheOnesShipped() {
        assertEquals(ThreadsDiscovery.STARTER_HANDLES, ThreadsSearchRules.accountsToTry(emptyList(), ThreadsDiscovery.STARTER_HANDLES))
    }

    @Test
    fun recommendedAccountsLeadEachOnceAndTheListStaysShort() {
        val starters = listOf("zuck", "mosseri", "threads")
        assertEquals(listOf("glaze", "Zuck", "mosseri", "threads"), ThreadsSearchRules.accountsToTry(listOf(" @glaze ", "Zuck", "", "GLAZE"), starters))
        val many = (1..40).map { "u$it" }
        val offered = ThreadsSearchRules.accountsToTry(many, starters)
        assertEquals(ThreadsSearchRules.ACCOUNTS_TO_TRY, offered.size)
        assertEquals(many.take(ThreadsSearchRules.ACCOUNTS_TO_TRY), offered)
        assertEquals(many, ThreadsSearchRules.accountsToTry(emptyList(), many), "what is shipped is never cut")
    }
}
