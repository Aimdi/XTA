package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsAccountState
import com.aimdi.xtanative.networks.threads.ThreadsBackground
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsForYouSource
import com.aimdi.xtanative.networks.threads.ThreadsForYouState
import com.aimdi.xtanative.networks.threads.ThreadsPerson
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsSession
import com.aimdi.xtanative.networks.threads.ThreadsSuggested
import com.aimdi.xtanative.networks.threads.ThreadsSuggestedRoutes
import com.aimdi.xtanative.networks.threads.ThreadsSuggestedAccount
import com.aimdi.xtanative.store.StoreFile
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * For you's "Suggested for you": read with the account only once the reader
 * reaches the row, once a visit, kept six hours, never from the background;
 * resting, signed out or without a route the guest strip stands in. Follow
 * and × stay on this device.
 */
class ThreadsSuggestedPeopleTest {
    private val folder = Files.createTempDirectory("threads-suggested").toFile().apply { deleteOnExit() }
    private var now = 1_700_000_000_000L
    private val hours = 60 * 60 * 1000L
    private var reads = 0
    private var usable = true
    private var answer: () -> List<ThreadsSuggestedAccount> = { listOf(person("ceramics"), person("glaze")) }
    private var timeline: Pair<Long, List<ThreadsSuggestedAccount>>? = null

    private fun person(handle: String) = ThreadsSuggestedAccount(handle, handle.uppercase(), null, "1${handle.length}")

    private fun suggestions(file: String = "hidden.json") = ThreadsSuggestedPeople(
        file = StoreFile(folder.resolve(file)),
        read = { reads++; answer() },
        usable = { usable },
        timeline = { timeline },
        clock = { now },
    )

    @Test
    fun nothingIsReadUntilTheReaderReachesTheRowThenOnceAVisit() = runBlocking {
        val row = suggestions()
        row.visit()
        assertEquals(0, reads, "opening For you reads nothing")
        row.reached()
        assertEquals(1, reads)
        assertEquals(listOf("ceramics", "glaze"), row.state.value.people.map { it.handle })
        row.reached()
        row.reached()
        assertEquals(1, reads, "at most once a visit")
    }

    @Test
    fun whatWasReadIsKeptSixHoursAcrossVisits() = runBlocking {
        val row = suggestions()
        row.visit(); row.reached()
        now += 5 * hours
        row.visit(); row.reached()
        assertEquals(1, reads, "a visit within six hours shows what was read")
        assertTrue(ThreadsSuggestedRules.fresh(row.state.value, now))
        now += 2 * hours
        assertFalse(ThreadsSuggestedRules.fresh(row.state.value, now), "past six hours it is no longer shown")
        row.visit(); row.reached()
        assertEquals(2, reads)
    }

    @Test
    fun aRestingOrSignedOutAccountOrNoRouteReadsNothingAndLeavesTheGuestStrip() = runBlocking {
        usable = ThreadsSuggested.usesAccount(ThreadsSession(COOKIE), ThreadsAccountState.RESTING, ThreadsSuggestedRoutes(path = true, query = true))
        val row = suggestions()
        row.visit(); row.reached()
        usable = ThreadsSuggested.usesAccount(ThreadsSession(COOKIE), ThreadsAccountState.SIGNED_OUT, ThreadsSuggestedRoutes(path = true, query = true))
        row.visit(); row.reached()
        usable = ThreadsSuggested.usesAccount(ThreadsSession(COOKIE), ThreadsAccountState.READY)
        row.visit(); row.reached()
        assertEquals(0, reads)
        assertTrue(row.state.value.people.isEmpty())
        assertFalse(row.mayAsk(), "the people row is the guest's, as today")
    }

    @Test
    fun aReadThatFailsShowsNothingOfItsOwnAndIsNotRetriedThisVisit() = runBlocking {
        answer = { throw ThreadsException(ThreadsFailure.ACCOUNT_RESTING, "resting") }
        val row = suggestions()
        row.visit(); row.reached(); row.reached()
        assertEquals(1, reads)
        assertTrue(row.state.value.people.isEmpty())
    }

    @Test
    fun theBackgroundNeverReads() = runBlocking {
        val row = suggestions()
        row.visit()
        withContext(ThreadsBackground) { row.reached() }
        assertEquals(0, reads)
        row.reached()
        assertEquals(1, reads, "the background's attempt did not use up the visit")
    }

    @Test
    fun theSuggestionsABearersForYouPageCarriedAreShownWithNoRead() = runBlocking {
        usable = false
        timeline = now - hours to listOf(person("between"))
        val row = suggestions()
        assertTrue(row.mayAsk())
        row.visit(); row.reached()
        assertEquals(0, reads)
        assertEquals(listOf("between"), row.state.value.people.map { it.handle })
    }

    @Test
    fun aHiddenSuggestionStaysHiddenOnThisDevice() = runBlocking {
        val row = suggestions()
        row.visit(); row.reached()
        row.dismiss("@Ceramics")
        assertEquals(listOf("ceramics"), row.dismissed.value)
        assertEquals(listOf("glaze"), ThreadsSuggestedRules.shown(row.state.value.people, row.dismissed.value.orEmpty().toSet()).map { it.handle })
        val again = suggestions()
        again.visit(); again.reached()
        assertEquals(listOf("ceramics"), again.dismissed.value, "remembered after a restart")
        assertEquals(2, reads, "a new start reads once again on reaching the row")
    }

    @Test
    fun hiddenHandlesAreKeptOnceEachTheOldestForgotten() {
        assertEquals(listOf("a"), ThreadsSuggestedRules.dismissing(ThreadsSuggestedRules.dismissing(emptyList(), "A"), " @a "))
        assertEquals(emptyList(), ThreadsSuggestedRules.dismissing(emptyList(), " "))
        val full = (1..ThreadsSuggestedRules.DISMISSED_CAP).map { "u$it" }
        assertEquals(listOf("u2"), ThreadsSuggestedRules.dismissing(full, "new").take(1))
        assertEquals(listOf("x", "y"), ThreadsSuggestedRules.decode(ThreadsSuggestedRules.encode(listOf("x", "y"))))
        assertEquals(emptyList(), ThreadsSuggestedRules.decode("not json"))
    }

    @Test
    fun thePeopleRowStandsWhereItWouldWhenSuggestionsMayComeEvenWithNoOneNewInTheFeed() {
        val posts = (1..5).map { ThreadsPost(id = "$it", handle = "a", authorName = "A", text = "t") }
        val state = ThreadsForYouState(posts = posts, source = ThreadsForYouSource.GUEST, loaded = true)
        assertTrue(ThreadsForYouRow.of(state, emptyList()).none { it is ThreadsForYouRow.People })
        val rows = ThreadsForYouRow.of(state, emptyList(), suggests = true)
        assertEquals(ThreadsForYouRow.PEOPLE_AFTER + 1, rows.indexOfFirst { it is ThreadsForYouRow.People }, "after the public note and the first posts")
        val guest = listOf(ThreadsPerson("b", "B", null, fromRepost = false))
        assertEquals(1, ThreadsForYouRow.of(state, guest).count { it is ThreadsForYouRow.People }, "the guest's strip as before")
    }

    private companion object {
        const val COOKIE = "sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g"
    }
}
