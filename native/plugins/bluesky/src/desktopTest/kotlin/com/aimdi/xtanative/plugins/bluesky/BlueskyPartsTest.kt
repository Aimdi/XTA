package com.aimdi.xtanative.plugins.bluesky

import com.aimdi.xtanative.data.Problem
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.ui.plugins.PagedState
import com.aimdi.xtanative.ui.plugins.problemOf
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class BlueskyPartsTest {
    @Test
    fun aFailedReadIsToldInBlueskysOwnWordsOnEveryScreen() {
        assertEquals(Strings.error_not_found, errorText(Problem.Refused(404)))
        assertEquals(Strings.error_rate_limited, errorText(Problem.RateLimited))
        assertEquals(Strings.error_response, errorText(Problem.Refused(500)))
        assertEquals(Strings.error_response, errorText(Problem.Other))
        assertEquals(Strings.error_network, errorText(Problem.Offline))
    }

    @Test
    fun whatAListKeepsOfAFailureSaysTheSameAsTheFailureItself() {
        val failures = listOf(
            NetworkRequestException("bluesky", 404, "missing"),
            NetworkRequestException("bluesky", 429, "slow down"),
            NetworkRequestException("bluesky", 502, "bad gateway"),
            IOException("no route"),
        )
        failures.forEach { failure -> assertEquals(errorText(failure), errorText(problemOf(failure)), failure.message) }
    }

    @Test
    fun aSearchOffersItsNextPageThenSaysThereIsNoMore() {
        val results = PagedState(items = listOf("a"), next = "2", loaded = true)
        assertEquals(SearchFoot.MORE, SearchFoot.of(results))
        assertEquals(SearchFoot.END, SearchFoot.of(results.copy(next = null)))
        assertEquals(SearchFoot.NONE, SearchFoot.of(results.copy(loadingMore = true)), "while the next page is read")
        assertEquals(SearchFoot.NONE, SearchFoot.of(results.copy(moreProblem = Problem.Offline)), "the failed page has its own retry")
        assertEquals(SearchFoot.NONE, SearchFoot.of(PagedState<String>(loaded = true)), "nothing found says so itself")
    }
}
