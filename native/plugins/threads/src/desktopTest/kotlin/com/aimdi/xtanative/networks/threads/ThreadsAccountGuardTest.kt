package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield

/**
 * The account guard: the reader's Threads account is read only when a
 * screen needs it, one read at a time and a person's pace apart, never past
 * its budgets, rested longer after each warning Meta gives, never again
 * once Meta signs it out, and none of it forgotten by a restart.
 */
class ThreadsAccountGuardTest {
    private val time = FakeTime()
    private val minutes = 60 * 1000L
    private val hours = 60 * minutes
    private val cookies = ThreadsSession(COOKIES)

    /** A people list's pages: the cursor in `max_id`, the next one after it. */
    private fun peoplePage(asked: Asked): XResponse {
        val n = asked.url.queryParameter("max_id")?.removePrefix("c")?.toIntOrNull() ?: 0
        return ok("""{"users":[{"username":"u$n","pk":"${100 + n}"}],"next_max_id":"c${n + 1}"}""")
    }

    private fun sessionAsked(meta: FakeMeta) = meta.asked.filter { "Cookie" in it.headers || "Authorization" in it.headers }

    // ---- Budgets ----

    @Test
    fun fifteenReadsInFiveMinutesThenTheAccountRestsAndNothingIsSent() = runBlocking {
        val meta = FakeMeta(::peoplePage)
        val client = direct(meta, cookies, time = time)
        val start = time.now
        (0 until 15).forEach { client.people(ThreadsPeopleList.FOLLOWERS, "42", "c$it") }
        val resting = assertFailsWith<ThreadsException> { client.people(ThreadsPeopleList.FOLLOWERS, "42", "c15") }
        assertEquals(ThreadsFailure.ACCOUNT_RESTING, resting.failure)
        assertEquals(Instant.ofEpochMilli(start + 5 * minutes), resting.retryAt, "the first read ages out of the window then")
        assertEquals(ThreadsRetry.MANUAL, resting.retry, "a spent budget is never waited out and retried on its own")
        assertEquals(15, meta.asked.size, "the sixteenth was not sent")
        assertEquals(ThreadsAccountState.RESTING, client.guard.refresh().state)

        time.now = start + 5 * minutes
        client.people(ThreadsPeopleList.FOLLOWERS, "42", "c15")
        assertEquals(16, meta.asked.size)
    }

    @Test
    fun theHourAndTheDayHaveBudgetsOfTheirOwn() {
        val memory = ThreadsMemory.InMemory()
        val guard = ThreadsAccountGuard(memory, time.clock)
        val start = time.now
        repeat(60) {
            assertNull(guard.refusal(), "read $it")
            guard.departed()
            time.now += 30_000
        }
        val hour = guard.refusal()
        assertEquals(ThreadsFailure.ACCOUNT_RESTING, hour?.failure)
        assertEquals(Instant.ofEpochMilli(start + hours), hour?.retryAt)

        // A day of reads spread so no shorter window fills: 300 is the day's.
        val day = ThreadsAccountGuard(ThreadsMemory.InMemory(), time.clock)
        val dayStart = time.now
        repeat(300) {
            assertNull(day.refusal(), "read $it")
            day.departed()
            time.now += 4 * minutes
        }
        time.now = dayStart + 23 * hours
        val spent = day.refusal()
        assertEquals(ThreadsFailure.ACCOUNT_RESTING, spent?.failure)
        assertEquals(Instant.ofEpochMilli(dayStart + 24 * hours), spent?.retryAt)
        assertEquals(300, day.refresh().usedToday)
        assertEquals(300, day.refresh().dailyBudget)
    }

    // ---- Pace ----

    @Test
    fun everyFifthReadInARowIsFollowedByALongPauseAndAListsNextPagesGoSlower() = runBlocking {
        val departures = CopyOnWriteArrayList<Long>()
        val meta = FakeMeta { departures += time.now; peoplePage(it) }
        val pacing = ThreadsPacing(3_000, 0, 0, 0, sleep = time.sleep, longPauseMillis = 12_000L..12_000L, pagingExtraMillis = 2_000L..2_000L)
        val client = direct(meta, cookies, time = time, pacing = pacing)
        listOf("a", "b", "c", "d", "e", "f", "g").forEach { client.searchUsers(it) }
        val gaps = departures.zipWithNext { a, b -> b - a }
        assertEquals(listOf(3_000L, 3_000L, 3_000L, 3_000L, 12_000L, 3_000L), gaps, "a person stops to read after a few")

        departures.clear()
        time.now += 10 * minutes
        client.people(ThreadsPeopleList.FOLLOWERS, "42")
        client.people(ThreadsPeopleList.FOLLOWERS, "42", "c1")
        assertEquals(5_000L, departures[1] - departures[0], "a page after the first: the gap and a scroll's more")
    }

    @Test
    fun theSpacingStaysWithinItsBounds() {
        val pacing = ThreadsPacing(random = kotlin.random.Random(3))
        val plain = (1..200).map { pacing.sessionSpacing(streak = 1, paging = false) }
        val paging = (1..200).map { pacing.sessionSpacing(streak = 1, paging = true) }
        val resting = (1..200).map { pacing.sessionSpacing(streak = 5, paging = false) }
        assertTrue(plain.all { it in 3_000 until 3_750 }, "$plain")
        assertTrue(paging.all { it in 5_000..7_750 }, "$paging")
        assertTrue(resting.all { it in 8_000..20_000 } && resting.toSet().size > 1, "$resting")
        assertEquals(0, ThreadsPacing.NONE.sessionSpacing(5, paging = true))
    }

    @Test
    fun sessionReadsLeaveOneAtATimeUntilEachIsAnswered() = runBlocking {
        var inFlight = 0
        var most = 0
        val meta = FakeMeta {
            inFlight++
            most = maxOf(most, inFlight)
            delay(20)
            inFlight--
            peoplePage(it)
        }
        val client = direct(meta, cookies, time = time)
        coroutineScope { (0 until 4).map { n -> async { client.people(ThreadsPeopleList.FOLLOWING, "42", "c$n") } }.awaitAll() }
        assertEquals(1, most)
    }

    // ---- Warnings and rests ----

    @Test
    fun eachWarningOfTheDayRestsLongerAndADayLaterTheLadderStartsAgain() = runBlocking {
        val meta = FakeMeta { XResponse(429, "{}") }
        val client = direct(meta, cookies, time = time)
        val rests = (1..4).map {
            val start = time.now
            val throttled = assertFailsWith<ThreadsException> { client.currentUser() }
            assertEquals(ThreadsFailure.THROTTLED, throttled.failure)
            val rest = throttled.retryAt!!.toEpochMilli() - start
            assertFailsWith<ThreadsException> { client.currentUser() }.also { assertEquals(ThreadsFailure.SESSION_SUSPENDED, it.failure) }
            time.now = throttled.retryAt!!.toEpochMilli()
            rest
        }
        assertEquals(listOf(30 * minutes, 6 * hours, 24 * hours, 24 * hours), rests)
        assertEquals(4, meta.asked.size, "nothing was sent while resting")

        time.now += 25 * hours
        val fresh = assertFailsWith<ThreadsException> { client.currentUser() }
        assertEquals(30 * minutes, fresh.retryAt!!.toEpochMilli() - time.now, "a day without warnings starts at the first rung")
    }

    @Test
    fun aRestEndsOnItsOwnAndReadingGoesOn() = runBlocking {
        var throttle = true
        val meta = FakeMeta { if (throttle) XResponse(429, "") else ok("""{"user":{"username":"me","pk":"1"}}""") }
        val client = direct(meta, cookies, time = time)
        val until = assertFailsWith<ThreadsException> { client.currentUser() }.retryAt!!
        assertEquals(ThreadsAccountState.RESTING, client.guard.refresh().state)
        assertEquals(until, client.guard.refresh().until)
        throttle = false
        time.now = until.toEpochMilli()
        assertEquals("me", client.currentUser().username)
        assertEquals(ThreadsAccountState.READY, client.guard.refresh().state)
    }

    @Test
    fun metasRetryAfterIsHonouredWhenItIsLongerThanTheRest() = runBlocking {
        val seconds = direct(FakeMeta { XResponse(429, "", mapOf("Retry-After" to listOf("7200"))) }, cookies, time = time)
        assertEquals(Instant.ofEpochMilli(time.now + 2 * hours), assertFailsWith<ThreadsException> { seconds.currentUser() }.retryAt)

        val later = FakeTime(time.now)
        val date = ZonedDateTime.ofInstant(Instant.ofEpochMilli(later.now + 3 * hours), ZoneOffset.UTC).format(DateTimeFormatter.RFC_1123_DATE_TIME)
        val dated = direct(FakeMeta { XResponse(429, "", mapOf("Retry-After" to listOf(date))) }, cookies, time = later)
        assertEquals(Instant.ofEpochMilli(later.now + 3 * hours), assertFailsWith<ThreadsException> { dated.currentUser() }.retryAt)

        val short = direct(FakeMeta { XResponse(429, "", mapOf("Retry-After" to listOf("60"))) }, cookies, time = FakeTime(time.now))
        val rest = assertFailsWith<ThreadsException> { short.currentUser() }
        assertEquals(30 * minutes, rest.retryAt!!.toEpochMilli() - time.now, "a shorter Retry-After never shortens the rest")
    }

    @Test
    fun aChallengeACheckpointOrASuspensionParksTheSessionAtOnce() = runBlocking {
        val answers = listOf(
            XResponse(400, """{"message":"checkpoint_required","checkpoint_url":"https://www.instagram.com/challenge/1/","status":"fail"}"""),
            XResponse(400, """{"message":"challenge_required","challenge":{"url":"https://i.instagram.com/challenge/2/"}}"""),
            XResponse(403, """{"message":"consent_required","status":"fail"}"""),
            XResponse(302, "", mapOf("Location" to listOf("https://www.threads.com/challenge/?next=/api/v1/"))),
            XResponse(302, "", mapOf("Location" to listOf("https://www.instagram.com/accounts/suspended/"))),
            XResponse(302, "", mapOf("Location" to listOf("/checkpoint/1234/"))),
        )
        answers.forEach { answer ->
            val meta = FakeMeta { answer }
            val client = direct(meta, cookies, time = FakeTime(time.now))
            val parked = assertFailsWith<ThreadsException> { client.currentUser() }
            assertEquals(ThreadsFailure.SESSION_SUSPENDED, parked.failure, answer.body)
            assertTrue(client.isSessionParked, answer.body)
            assertTrue(parked.retryAt != null)
            assertFailsWith<ThreadsException> { client.currentUser() }
            assertEquals(1, meta.asked.size, "a redirect to a challenge is not followed, and nothing more is sent")
        }
    }

    @Test
    fun feedbackRequiredAndMetasSpamFlagAreThrottles() = runBlocking {
        listOf(
            XResponse(400, """{"message":"feedback_required","spam":true,"feedback_title":"Try again later"}"""),
            ok("""{"spam":true,"status":"fail"}"""),
        ).forEach { answer ->
            val client = direct(FakeMeta { answer }, cookies, time = FakeTime(time.now))
            assertEquals(ThreadsFailure.THROTTLED, assertFailsWith<ThreadsException> { client.currentUser() }.failure)
            assertTrue(client.isSessionParked)
        }
    }

    @Test
    fun aPostThatMentionsAWarningIsNoWarning() = runBlocking {
        val client = direct(FakeMeta { ok("""{"users":[{"username":"spam","pk":"1","biography":"checkpoint_required challenge_required spam"}]}""") }, cookies, time = time)
        assertEquals(listOf("spam"), client.searchUsers("spam").items.map { it.username })
        assertFalse(client.isSessionParked)
    }

    @Test
    fun aSessionMetaSaysIsGoneIsSignedOutAndNotUsedUntilTheReaderSignsInAgain() = runBlocking {
        listOf(
            XResponse(401, "{}"),
            XResponse(403, """{"require_login":true,"status":"fail"}"""),
            XResponse(302, "", mapOf("Location" to listOf("https://www.threads.com/login/?next=%2F"))),
        ).forEach { answer ->
            var gone = true
            val meta = FakeMeta { if (gone) answer else ok("""{"user":{"username":"me","pk":"1"}}""") }
            val client = direct(meta, cookies, time = FakeTime(time.now))
            val refused = assertFailsWith<ThreadsException> { client.currentUser() }
            assertEquals(ThreadsFailure.UNAUTHORIZED, refused.failure)
            assertEquals(ThreadsRetry.MANUAL, refused.retry)
            assertFalse(client.isSessionParked, "it is not resting: it waits for the reader")
            assertEquals(ThreadsAccountState.SIGNED_OUT, client.guard.refresh().state)
            assertFailsWith<ThreadsException> { client.currentUser() }
            assertEquals(1, meta.asked.size, "a signed-out session is not tried again")

            gone = false
            client.sessionChanged()
            assertEquals("me", client.currentUser().username)
            assertEquals(2, meta.asked.size)
        }
    }

    @Test
    fun aPlainForbiddenListIsNoSignOut() = runBlocking {
        val client = direct(FakeMeta { XResponse(403, """{"message":"Not authorized to view user","status":"fail"}""") }, cookies, time = time)
        assertEquals(ThreadsFailure.UNAUTHORIZED, assertFailsWith<ThreadsException> { client.people(ThreadsPeopleList.FOLLOWERS, "42") }.failure)
        assertEquals(ThreadsAccountState.READY, client.guard.refresh().state, "a private account's list does not sign the reader out")
    }

    @Test
    fun aSessionReadIsNeverTriedAgainOnItsOwn() = runBlocking {
        listOf(XResponse(500, "down"), XResponse(429, ""), XResponse(404, "")).forEach { answer ->
            val meta = FakeMeta { answer }
            val client = direct(meta, cookies, time = FakeTime(time.now))
            runCatching { ThreadsRetryBudget.retrying(online = { true }, wait = {}) { client.people(ThreadsPeopleList.LIKERS, "42") } }
            assertEquals(1, sessionAsked(meta).size, "${answer.code}: asked again on its own")
        }
    }

    // ---- Cache ----

    @Test
    fun aPageReadInTheLastHalfHourIsThatPageAgainAndTwoAskingAtOnceShareOneRead() = runBlocking {
        val meta = FakeMeta { yield(); peoplePage(it) }
        val client = direct(meta, cookies, time = time)
        client.people(ThreadsPeopleList.FOLLOWERS, "42")
        client.people(ThreadsPeopleList.FOLLOWERS, "42")
        client.people(ThreadsPeopleList.FOLLOWERS, "42", "c1")
        client.people(ThreadsPeopleList.FOLLOWERS, "42", "c1")
        assertEquals(2, meta.asked.size, "back and forth costs nothing")

        coroutineScope { (1..3).map { async { client.people(ThreadsPeopleList.FOLLOWING, "42") } }.awaitAll() }
        assertEquals(3, meta.asked.size, "one read shared while in flight")

        client.people(ThreadsPeopleList.FOLLOWERS, "42", fresh = true)
        assertEquals(4, meta.asked.size, "pulling to refresh reads again")

        time.now += 31 * minutes
        client.people(ThreadsPeopleList.FOLLOWERS, "42", "c1")
        assertEquals(5, meta.asked.size, "past half an hour the page is read again")
    }

    @Test
    fun aFailureIsNotKept() = runBlocking {
        var fail = true
        val meta = FakeMeta { if (fail) XResponse(500, "") else peoplePage(it) }
        val client = direct(meta, cookies, time = time)
        assertFailsWith<ThreadsException> { client.people(ThreadsPeopleList.FOLLOWERS, "42") }
        fail = false
        assertEquals(listOf("u0"), client.people(ThreadsPeopleList.FOLLOWERS, "42").items.map { it.username })
    }

    // ---- Page cap ----

    @Test
    fun aSessionListReadsAFewPagesOnItsOwnThenWaitsForLoadMore() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val asked = CopyOnWriteArrayList<String>()
        val paging = ThreadsPaging<String>(scope, { it }, autoPages = ThreadsGuardLimits.AUTO_PAGES) { cursor ->
            asked += cursor
            val n = cursor.removePrefix("c").toInt()
            ThreadsMore(listOf("p$n"), "c${n + 1}")
        }
        paging.start(listOf("p0"), "c1")
        withTimeout(5_000) {
            while (!paging.state.value.held) {
                paging.more()
                delay(5)
            }
        }
        repeat(5) {
            paging.more()
            delay(5)
        }
        assertEquals(listOf("c1", "c2", "c3", "c4"), asked, "scrolling alone reads four pages after the first")
        assertTrue(paging.state.value.held)
        assertEquals("c5", paging.state.value.next)

        paging.loadMore()
        withTimeout(5_000) { paging.state.first { !it.loading && it.items.size == 5 } }
        paging.more()
        delay(50)
        assertEquals(listOf("c1", "c2", "c3", "c4", "c5"), asked, "one page a tap")
        assertTrue(paging.state.value.held)
        scope.cancel()
    }

    @Test
    fun aGuestListStillReadsOnAsTheReaderScrolls() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        var reads = 0
        val paging = ThreadsPaging<String>(scope, { it }) { cursor ->
            reads++
            val n = cursor.removePrefix("c").toInt()
            ThreadsMore(listOf("p$n"), "c${n + 1}")
        }
        paging.start(listOf("p0"), "c1")
        withTimeout(5_000) {
            while (reads < 8) {
                paging.more()
                delay(5)
            }
        }
        assertEquals(8, reads)
        assertFalse(paging.state.value.held)
        scope.cancel()
    }

    // ---- Never in the background ----

    @Test
    fun aSessionReadInTheBackgroundIsRefusedUnlessTheReaderOptedIn() = runBlocking {
        val meta = FakeMeta(::peoplePage)
        val refused = assertFailsWith<ThreadsException> { withContext(ThreadsBackground) { direct(meta, cookies, time = time).people(ThreadsPeopleList.FOLLOWERS, "42") } }
        assertEquals(ThreadsFailure.NOT_CONFIGURED, refused.failure)
        assertTrue(meta.asked.isEmpty())

        withContext(ThreadsBackground) { direct(meta, cookies.copy(useSessionApis = true), time = time).people(ThreadsPeopleList.FOLLOWERS, "42") }
        assertEquals(1, meta.asked.size, "opted in, Home may spend it")
    }

    @Test
    fun withASessionPresentEverythingAGuestCanReadIsReadAsAGuest() = runBlocking {
        val meta = FakeMeta { request ->
            when {
                request.url.host == "i.instagram.com" || request.path.startsWith("/api/v1/") -> ok("{}")
                request.path == "/@zuck" -> ok(fixture("profile_page.html"))
                request.path == "/api/graphql" -> ok(fixture("graphql_profile_threads.json"))
                request.path.startsWith("/@zuck/post/") -> ok(fixture("post_page.html"))
                request.path == "/search" -> ok(fixture("search_page.html"))
                request.path == "/" -> ok(page(listOf(listOf(postJson("9", user = "nasa")))))
                else -> XResponse(404, "")
            }
        }
        val session = ThreadsSession(COOKIES, bearer = "IGT:2:test")
        val client = direct(meta, session, time = time)
        val reader = ThreadsReader(client, ThreadsRssHub(meta), ThreadsXy(meta), clock = time.clock)
        val post = ThreadsPost(id = "1", handle = "zuck", authorName = "zuck", text = "post 1", url = "https://www.threads.com/@zuck/post/C1")
        val reads: List<suspend () -> Any?> = listOf(
            { reader.following(listOf("zuck")) },
            { reader.following(emptyList()) },
            { reader.following(listOf("zuck"), force = true) },
            { reader.extras(listOf("zuck"), listOf("kiln")) },
            { reader.heldExtras(listOf("zuck"), listOf("kiln")) },
            { reader.postsFor(listOf("zuck"), force = true) },
            { reader.profile("zuck", force = true) },
            { reader.retry("zuck") },
            { reader.conversation(post, force = true) },
            { ThreadsForYou(client).page() },
            { ThreadsForYou(client) { listOf("kiln") }.page("explore:1") },
            { client.profile("zuck") },
            { client.userThreads("zuck") },
            { client.userIdFor("zuck") },
            { client.guestReplies("zuck") },
            { client.guestReposts("zuck") },
            { client.searchFound("kiln") },
            { client.trends(force = true) },
            { client.postPage(post.url!!, force = true) },
            { client.recommendedPeople() },
            { client.guestPeople("kiln") },
        )
        reads.forEach { read -> runCatching { read() } }
        assertTrue(meta.asked.isNotEmpty())
        assertEquals(emptyList(), sessionAsked(meta).map { "${it.method} ${it.url}" }, "a guest read spent the account")
        assertEquals(emptyList(), meta.asked.filter { it.url.host == "i.instagram.com" || it.path.startsWith("/api/v1/") }.map { it.path })
        assertEquals(0, client.guard.refresh().usedToday)
    }

    @Test
    fun forYouAndHomeSpendTheBearerOnlyWhenTheReaderOptedIn() = runBlocking {
        val meta = FakeMeta { request -> if (request.url.host == "i.instagram.com") ok(fixture("app_timeline.json")) else ok(page(listOf(listOf(postJson("9", user = "nasa"))))) }
        val bearer = ThreadsSession(bearer = "IGT:2:test")
        assertEquals(ThreadsForYouSource.GUEST, ThreadsForYou(direct(meta, bearer, time = time)).page().source)
        assertEquals(emptyList(), ThreadsReader(direct(meta, bearer, time = time), ThreadsRssHub(meta), ThreadsXy(meta), clock = time.clock).following(emptyList()))
        assertEquals(emptyList(), sessionAsked(meta))

        assertEquals(ThreadsForYouSource.SESSION, ThreadsForYou(direct(meta, bearer.copy(useSessionApis = true), time = time)).page().source)
        assertEquals(1, sessionAsked(meta).size)
    }

    // ---- What is kept, and the headers ----

    @Test
    fun budgetsRestsAndASignOutOutliveARestart() = runBlocking {
        val memory = ThreadsMemory.InMemory()
        val meta = FakeMeta(::peoplePage)
        val first = direct(meta, cookies, memory, time)
        (0 until 15).forEach { first.people(ThreadsPeopleList.FOLLOWERS, "42", "c$it") }

        val restarted = direct(meta, cookies, memory, time)
        assertEquals(ThreadsFailure.ACCOUNT_RESTING, assertFailsWith<ThreadsException> { restarted.people(ThreadsPeopleList.FOLLOWERS, "42", "c99") }.failure)
        assertEquals(15, restarted.guard.refresh().usedToday)

        val decoded = ThreadsGuardKept.decode(memory.kept.guard.encode())
        assertEquals(memory.kept.guard, decoded)
        assertFalse("sessionid" in memory.kept.guard.encode() || "csrftoken" in memory.kept.guard.encode(), "no secret is kept")

        time.now += 10 * minutes
        direct(FakeMeta { XResponse(401, "") }, cookies, memory, time).let { runCatching { it.currentUser() } }
        val again = FakeMeta { ok("{}") }
        assertFailsWith<ThreadsException> { direct(again, cookies, memory, time).currentUser() }
        assertTrue(again.asked.isEmpty(), "signed out stays signed out past a restart")
    }

    @Test
    fun theWarningsOfTheDayOutliveARestartToo() = runBlocking {
        val memory = ThreadsMemory.InMemory()
        val first = assertFailsWith<ThreadsException> { direct(FakeMeta { XResponse(429, "") }, cookies, memory, time).currentUser() }
        time.now = first.retryAt!!.toEpochMilli()
        val second = assertFailsWith<ThreadsException> { direct(FakeMeta { XResponse(429, "") }, cookies, memory, time).currentUser() }
        assertEquals(6 * hours, second.retryAt!!.toEpochMilli() - time.now)
    }

    @Test
    fun aSessionIsSentTheSameHeadersForItsLifeAndANewOneTodays() = runBlocking {
        val meta = FakeMeta(::peoplePage)
        var repairs = ThreadsRepairs()
        val client = ThreadsDirectClient(meta, { cookies }, ThreadsMemory.InMemory(), { repairs }, ThreadsPacing.NONE, time.clock)
        client.people(ThreadsPeopleList.FOLLOWERS, "42")
        val first = meta.asked.last().headers
        repairs = repairs.copy(webUserAgent = "Mozilla/5.0 (published repair)", appId = "999")
        client.people(ThreadsPeopleList.FOLLOWERS, "42", "c1")
        assertEquals(first["User-Agent"], meta.asked.last().headers["User-Agent"])
        assertEquals(first["X-IG-App-ID"], meta.asked.last().headers["X-IG-App-ID"])

        client.sessionChanged()
        client.people(ThreadsPeopleList.FOLLOWERS, "42", "c2")
        assertEquals("Mozilla/5.0 (published repair)", meta.asked.last().headers["User-Agent"])
        assertEquals("999", meta.asked.last().headers["X-IG-App-ID"])
    }

    @Test
    fun theGuardsStateReadsWhatItCounts() {
        val guard = ThreadsAccountGuard(ThreadsMemory.InMemory(), time.clock)
        assertEquals(ThreadsAccountStatus(ThreadsAccountState.READY, null, 0, 300), guard.refresh())
        repeat(3) { guard.departed() }
        assertEquals(3, guard.status.value.usedToday)
        val until = guard.warned()
        assertEquals(ThreadsAccountStatus(ThreadsAccountState.RESTING, until, 3, 300), guard.status.value)
        guard.signedOut()
        assertEquals(ThreadsAccountState.SIGNED_OUT, guard.status.value.state)
        time.now += 25 * hours
        guard.sessionChanged()
        assertEquals(ThreadsAccountStatus(ThreadsAccountState.READY, null, 0, 300), guard.refresh())
    }

    // ---- Communities ----

    @Test
    fun aCommunityIsReadWithTheAccountOnlyOnceAPathIsKnownAndWithASession() {
        assertFalse(ThreadsCommunityFeed.PATH_KNOWN, "ships off until CI's probe sees a path answer")
        assertFalse(ThreadsCommunityFeed.usesAccount(cookies, ThreadsAccountState.READY))
        assertTrue(ThreadsCommunityFeed.usesAccount(cookies, ThreadsAccountState.READY, pathKnown = true))
        assertTrue(ThreadsCommunityFeed.usesAccount(ThreadsSession(bearer = "IGT:2:x"), ThreadsAccountState.RESTING, pathKnown = true), "the guard refuses a rest itself")
        assertFalse(ThreadsCommunityFeed.usesAccount(ThreadsSession(), ThreadsAccountState.READY, pathKnown = true))
        assertFalse(ThreadsCommunityFeed.usesAccount(cookies, ThreadsAccountState.SIGNED_OUT, pathKnown = true))
    }

    @Test
    fun aCommunitysFeedIsASessionReadThroughTheGuardTriedPathByPath() = runBlocking {
        val meta = FakeMeta { asked ->
            when (asked.path) {
                "/api/v1/text_feed/community_feed/" -> ok("""{"threads":[{"thread_items":[{"post":${json(postJson("7", user = "mo"))}}]}],"next_max_id":"n2"}""")
                else -> XResponse(404, "")
            }
        }
        val client = direct(meta, cookies, time = time)
        val page = client.communityFeed("Cosplayers of Threads")
        assertEquals(listOf("7"), page.items.map { it.id })
        assertEquals("n2", page.next)
        assertEquals(listOf("/api/v1/text_feed/tag_feed/", "/api/v1/text_feed/community_feed/"), meta.asked.map { it.path })
        assertTrue(meta.asked.all { it.method == "GET" && "Cookie" in it.headers })
        assertEquals("Cosplayers of Threads", meta.asked.last().url.queryParameter("tag_name"))
        client.communityFeed("Cosplayers of Threads")
        assertEquals(2, meta.asked.size, "kept for half an hour, and the path that answered is asked first")
        client.communityFeed("Cosplayers of Threads", "n2")
        assertEquals("n2", meta.asked.last().url.queryParameter("max_id"))
        assertEquals("cosplayersofthreads", ThreadsCommunityFeed.slug("#Cosplayers of Threads"))
    }

    @Test
    fun aRestingAccountSendsNoCommunityReadAndHomesCommunitiesAreGuestReads() = runBlocking {
        val parked = ThreadsMemory.InMemory(ThreadsKept(cooldownUntil = time.now + hours))
        val meta = FakeMeta { request -> if (request.path.startsWith("/api/v1/")) ok("{}") else ok(page(listOf(listOf(postJson("9", user = "nasa"))))) }
        val client = direct(meta, cookies, parked, time)
        assertFailsWith<ThreadsException> { client.communityFeed("Cosplayers of Threads") }
        assertTrue(meta.asked.isEmpty(), "the guard refused it before anything left")

        val fresh = direct(meta, cookies, time = time)
        val reader = ThreadsReader(fresh, ThreadsRssHub(meta), ThreadsXy(meta), clock = time.clock)
        runCatching { reader.extras(emptyList(), listOf("https://www.threads.com/communities/9", "Cosplayers of Threads")) }
        assertTrue(meta.asked.isNotEmpty())
        assertEquals(emptyList(), sessionAsked(meta).map { it.path }, "Home's followed communities never spend the account")
    }

    @Test
    fun aStreakEndsAtARest() {
        val guard = ThreadsAccountGuard(ThreadsMemory.InMemory(), time.clock)
        assertEquals(0, guard.streak())
        repeat(3) { guard.departed(); time.now += 4_000 }
        assertEquals(3, guard.streak())
        time.now += 3 * minutes
        assertEquals(0, guard.streak(), "a few minutes without reading is a rest")
        guard.departed()
        time.now += 4_000
        assertEquals(1, guard.streak())
    }
}
