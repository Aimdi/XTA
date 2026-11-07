package com.aimdi.xtanative.core.feed

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's account_posts_test and the rotation group Discover samples members with, for the native port. */
class RotationTest {
    private data class Post(val from: String, val at: Instant?)

    private var now = 1_000_000L

    private fun cache(perMember: Int = 10, ttlMillis: Long = MemberCache.TTL_MILLIS, concurrency: Int = 2) =
        MemberCache<Post>({ it.at }, perMember, concurrency, ttlMillis, clock = { now })

    private fun day(day: Int) = Instant.parse("2026-01-%02dT00:00:00Z".format(day))

    private fun answering(asked: MutableList<String> = ArrayList()): suspend (String) -> List<Post> = { key ->
        asked += key
        listOf(Post(key, day(1)))
    }

    @Test
    fun aTimestampInTheFutureIsNotFresh() = runBlocking {
        val cache = cache()
        cache.seed("a", listOf(Post("a", day(1))), at = now + 60_000)
        assertEquals(1, cache.pendingCount(listOf("a")))
    }

    @Test
    fun membersMergeNewestFirstAPageEachAndUndatedLast() = runBlocking {
        assertTrue(cache().merge(emptyList(), fetch = answering()).isEmpty())
        val merged = cache().merge(listOf("a", "b")) { key -> listOf(Post(key, if (key == "a") day(1) else day(5))) }
        assertEquals(listOf("b", "a"), merged.map { it.from })
        assertEquals(2, cache(perMember = 2).merge(listOf("a")) { listOf(Post("3", day(3)), Post("2", day(2)), Post("1", day(1))) }.size)
        val undated = cache().merge(listOf("a")) { listOf(Post("undated", null), Post("dated", day(1))) }
        assertEquals(listOf("dated", "undated"), undated.map { it.from })
    }

    @Test
    fun oneMemberFailingKeepsTheOthersAndTheFailureShowsOnlyWhenNothingWasRead(): Unit = runBlocking {
        val merged = cache().merge(listOf("good", "bad")) { key -> if (key == "bad") throw IllegalStateException("down") else listOf(Post(key, day(1))) }
        assertEquals(listOf("good"), merged.map { it.from })
        assertFailsWith<IllegalStateException> { cache().merge(listOf("bad")) { throw IllegalStateException("down") } }
    }

    @Test
    fun aMemberIsAskedAgainOnlyOnARefreshOrOnceItsAnswerIsOld() = runBlocking {
        val asked = ArrayList<String>()
        val members = cache(ttlMillis = 1_000)
        members.merge(listOf("a"), fetch = answering(asked))
        members.merge(listOf("a"), fetch = answering(asked))
        assertEquals(1, asked.size)
        members.merge(listOf("a"), fetch = answering(asked), forceRefresh = true)
        assertEquals(2, asked.size)
        now += 1_001
        members.merge(listOf("a"), fetch = answering(asked))
        assertEquals(3, asked.size)
        members.clear()
        members.merge(listOf("a"), fetch = answering(asked))
        assertEquals(4, asked.size)
    }

    @Test
    fun onlySoManyMembersAreAskedPerCallAndTheRestOnTheNext() = runBlocking {
        val asked = ArrayList<String>()
        val members = cache()
        assertEquals(2, members.merge(listOf("a", "b", "c", "d"), fetch = answering(asked), maxFetches = 2).size)
        assertEquals(2, asked.size)
        val second = members.merge(listOf("a", "b", "c", "d"), fetch = answering(asked), maxFetches = 2)
        assertEquals(setOf("a", "b", "c", "d"), asked.toSet())
        assertEquals(4, second.size, "the first two came from the cache")
        assertEquals(0, members.pendingCount(listOf("a", "b", "c", "d")))
        assertEquals(1, members.pendingCount(listOf("a", "e")))
    }

    @Test
    fun aCachedMemberIsFreeAndARefreshPastTheBudgetKeepsWhatIsHeld() = runBlocking {
        val asked = ArrayList<String>()
        val members = cache()
        members.merge(listOf("a"), fetch = answering(asked))
        asked.clear()
        members.merge(listOf("a", "b"), fetch = answering(asked), maxFetches = 1)
        assertEquals(listOf("b"), asked)

        val refreshed = members.merge(listOf("a", "b"), fetch = answering(asked), forceRefresh = true, maxFetches = 1)
        assertEquals(setOf("a", "b"), refreshed.map { it.from }.toSet(), "a pull to refresh never collapses to the first batch")
    }

    @Test
    fun aRefreshPaintsWhatWasHeldAndAFailedMemberKeepsWhatItSaidLast(): Unit = runBlocking {
        val members = cache(concurrency = 1)
        members.merge(listOf("a", "b", "c"), fetch = answering())
        val painted = ArrayList<Set<String>>()
        members.merge(listOf("a", "b", "c"), forceRefresh = true, onPartial = { posts -> painted += posts.map { it.from }.toSet() }) { key -> listOf(Post(key, day(2))) }
        assertTrue(painted.isNotEmpty())
        assertTrue(painted.all { it.size == 3 }, "held members stay on screen while asked again")

        val kept = members.merge(listOf("a", "b"), forceRefresh = true) { key -> if (key == "b") throw IllegalStateException("throttled") else listOf(Post("a2", day(3))) }
        assertEquals(listOf("a2", "b"), kept.map { it.from })
        assertFailsWith<IllegalStateException> { members.merge(listOf("a", "b"), forceRefresh = true) { throw IllegalStateException("offline") } }
    }

    @Test
    fun aSeedAnswersWithinItsTimeAndNeverReplacesSomethingNewer() = runBlocking {
        val members = cache()
        members.seed("a", listOf(Post("restored", day(1))), now - 60_000)
        members.seed("b", listOf(Post("old", day(1))), now - 2 * 60 * 60_000)
        val asked = ArrayList<String>()
        val posts = members.merge(listOf("a", "b")) { key -> asked += key; listOf(Post("$key-new", day(2))) }
        assertEquals(listOf("b"), asked)
        assertEquals(listOf("b-new", "restored"), posts.map { it.from })
        assertEquals(setOf("a", "b"), members.answeredAt.keys)

        members.seed("b", listOf(Post("older", day(1))), now - 1)
        assertEquals(listOf("b-new"), members.merge(listOf("b")) { error("cached") }.map { it.from })
    }

    @Test
    fun theNeverAskedComeFirstThenTheLongestUnasked() = runBlocking {
        val members = cache()
        members.merge(listOf("a", "b", "c"), fetch = answering(), maxFetches = 2)
        assertEquals(listOf("c", "a", "b"), members.prioritize(listOf("a", "b", "c")))
        now += 1
        members.merge(members.prioritize(listOf("a", "b", "c")), fetch = answering(), maxFetches = 1)
        assertEquals(listOf("a", "b", "c"), members.prioritize(listOf("a", "b", "c")))
        members.clear()
        assertEquals(listOf("c", "a"), members.prioritize(listOf("c", "a")))
    }

    @Test
    fun aRotatingReadWalksThroughTheMembersAndAsksTwiceAsManyForMore() = runBlocking {
        val members = cache()
        val asked = ArrayList<String>()
        val budgets = HashSet<Long>()
        val fetch: suspend (String, Long) -> List<Post> = { key, budget -> asked += key; budgets += budget; listOf(Post(key, day(1))) }
        val keys = (1..10).map { "m$it" }

        val first = Rotation.read(members, keys, more = false, perLoad = 2, fetch = fetch)
        assertEquals(2, first.read)
        val second = Rotation.read(members, keys, more = true, perLoad = 2, fetch = fetch)
        assertEquals(6, second.read)
        assertEquals((1..6).map { "m$it" }, asked, "never the same members twice while others wait")
        assertEquals(setOf(25_000L, 24_000L), budgets, "two reads fit one wave, four take two")
    }

    @Test
    fun aMembersShareOfTheTimeStaysBetweenFiveAndTwentyFiveSeconds() {
        assertEquals(24_000L, Rotation.memberBudgetMillis(4))
        assertEquals(25_000L, Rotation.memberBudgetMillis(1))
        assertEquals(5_000L, Rotation.memberBudgetMillis(40))
        assertEquals(25_000L, Rotation.memberBudgetMillis(0))
    }
}
