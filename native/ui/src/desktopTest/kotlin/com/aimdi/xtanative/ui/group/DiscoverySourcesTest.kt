package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.feed.ChunkState
import com.aimdi.xtanative.core.feed.FeedMember
import com.aimdi.xtanative.core.feed.FeedState
import com.aimdi.xtanative.core.feed.GroupFeedPlan
import com.aimdi.xtanative.core.feed.SavedFeed
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.ui.plugins.DiscoverySignal
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginDiscovered
import com.aimdi.xtanative.ui.subscriptions.SubscriptionRow
import java.io.IOException
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** Where Discover's candidates come from (group_discovery_feed.dart, discovery_rotation.dart, group_discovery_sources.dart) and how they are held. */
class DiscoverySourcesTest {
    private val members = listOf(
        FollowedAccount("a", "ma", "Member a", null, false, true, 0),
        FollowedAccount("b", "mb", "Member b", null, false, true, 0),
    ).associateBy { it.id }

    private fun user(id: String, handle: String) = XUser(id, handle, "Name $handle")

    private fun post(id: String, author: XUser, text: String = "", at: Long = 1_000, vararg mentions: String) = XPost(
        id = id,
        author = author,
        createdAt = Instant.ofEpochMilli(at),
        text = text,
        entities = mentions.map { XEntity.Mention(0, 0, it) },
    )

    private val memberPost = post("p1", user("a", "ma"), "look at this", 5_000, "ww", "mb", "xx").copy(
        reposted = post("r1", user("10", "xx"), "original", 4_000),
        quoted = post("q1", user("20", "yy"), "quoted words", 3_000),
        replyToScreenName = "zz",
    )

    @Test
    fun aMembersPostPointsAtWhatItRepostsQuotesAnswersAndMentionsEachOnceAndNeverAtAMember() {
        val found = XDiscovery.fromPosts(listOf(XThread("p1", listOf(memberPost))), members, includeReplies = true)
        assertEquals(listOf("xx" to DiscoverySignal.REPOSTED, "yy" to DiscoverySignal.QUOTED, "zz" to DiscoverySignal.REPLIED, "ww" to DiscoverySignal.MENTIONED), found.map { it.handle to it.supporters.single().signal })
        val repost = found.first()
        assertEquals("10", repost.id)
        assertEquals("original", repost.text)
        assertEquals("https://x.com/i/status/r1", repost.postUrl)
        assertEquals(4_000L, repost.date)
        assertEquals(Supporter("a", "ma", "Name ma", DiscoverySignal.REPOSTED, 5_000), repost.supporters.single())
        val mention = found.last()
        assertEquals("", mention.id, "a mention names only its handle")
        assertEquals("https://x.com/i/status/p1", mention.postUrl, "the member's own post shows why")
        assertIs<ShownPost.X>(mention.post)
    }

    @Test
    fun repliesCountOnlyWhereTheFeedTakesThemAndOthersPostsAreNotRead() {
        val noReplies = XDiscovery.fromPosts(listOf(XThread("p1", listOf(memberPost))), members, includeReplies = false)
        assertTrue(noReplies.none { it.handle == "zz" })
        val stranger = post("s1", user("99", "stranger"), "hi", 1, "ww")
        assertEquals(emptyList(), XDiscovery.fromPosts(listOf(XThread("s1", listOf(stranger))), members, includeReplies = true))
    }

    @Test
    fun theKeptFeedCoversTheMembersOfEachSearchItKeptAndWhoeverPostedInIt() {
        val plan = GroupFeedPlan("g", listOf(FeedMember("a", "ma"), FeedMember("b", "mb")))
        val searched = plan.chunks.map { ChunkState(plan.queryOf(it), null, true, null, emptyList()) }
        assertEquals(setOf("a", "b"), XDiscovery.coveredByFeed(SavedFeed(emptyList(), FeedState(searched)), plan, setOf("a", "b")))
        val posted = SavedFeed(listOf(XThread("p1", listOf(memberPost))), state = null)
        assertEquals(setOf("a"), XDiscovery.coveredByFeed(posted, plan, setOf("a", "b")))
        assertEquals(emptySet(), XDiscovery.coveredByFeed(null, plan, setOf("a")))
        val held = SavedFeed(emptyList(), FeedState(listOf(ChunkState("other", null, false, null, listOf(XThread("p1", listOf(memberPost)))))))
        assertEquals(listOf("p1"), XDiscovery.feedThreads(held).map { it.id }, "what a search held back is read too")
    }

    @Test
    fun aRotatingReadAsksTheNeverAskedFirstAndKeepsAnswersTenMinutes(): Unit = runBlocking {
        var now = 0L
        val reads = RotatingReads<String>(ttlMillis = 100, clock = { now })
        val asked = mutableListOf<String>()
        val fetch: suspend (String) -> List<String> = { key -> synchronized(asked) { asked += key }; listOf("$key-$now") }
        val first = reads.read(listOf("a", "b", "c"), maxFetches = 2, budgetMillis = 1_000, fetch = fetch)
        assertEquals(setOf("a", "b"), asked.toSet())
        assertEquals(2, first.read)
        now = 10
        val second = reads.read(listOf("a", "b", "c"), maxFetches = 2, budgetMillis = 1_000, fetch = fetch)
        assertEquals(listOf("c"), asked.drop(2), "the answered within the time are not asked again")
        assertEquals(setOf("a", "b", "c"), second.fresh)
        now = 500
        val failing = reads.read(listOf("a", "b", "c"), maxFetches = 1, budgetMillis = 1_000, fetch = { throw IOException("offline") })
        assertEquals(listOf("a-0"), failing.rows["a"], "a failure keeps what was said before")
        assertIs<IOException>(failing.error)
        assertFalse(failing.failed)
        assertEquals(0, failing.read)
    }

    @Test
    fun aRotatingReadThatReadsNothingIsAFailure(): Unit = runBlocking {
        val read = RotatingReads<String>().read(listOf("a"), maxFetches = 4, budgetMillis = 1_000, fetch = { throw IOException("offline") })
        assertTrue(read.failed)
        val partial = mutableListOf<Int>()
        RotatingReads<String>().read(listOf("a", "b"), maxFetches = 4, budgetMillis = 1_000, fetch = { listOf(it) }) { synchronized(partial) { partial += it.read } }
        assertEquals(listOf(1, 2), partial.sorted())
    }

    private fun candidate(id: String, source: String = Discovery.X) = Candidate(id, "h$id", "N$id", source = source, supporters = listOf(Supporter("a", "ma", "A", DiscoverySignal.FOLLOWED)))

    @Test
    fun theBoardKeepsEachSourcesLatestAnswerAndNeverReadsLess() {
        val board = DiscoveryBoard()
            .absorbed(Discovery.X, DiscoveryBatch(listOf(candidate("1")), read = 3))
            .absorbed(Discovery.X, DiscoveryBatch(listOf(candidate("2")), read = 1))
            .absorbed("bluesky", DiscoveryBatch(listOf(candidate("b", "bluesky")), read = 2, error = IOException("down")))
        assertEquals(listOf("2", "b"), board.found.map { it.id })
        assertEquals(DiscoveryCoverage(5, 9), board.coverage(mapOf(Discovery.X to 5, "bluesky" to 4)))
        assertEquals(setOf("bluesky"), board.failures.keys)
        val more = board.begun(keep = true, ranked = listOf(candidate("kept")))
        assertEquals(listOf("kept"), more.found.map { it.id })
        assertEquals(DiscoveryCoverage(5, 9), more.coverage(mapOf(Discovery.X to 5, "bluesky" to 4)))
        assertTrue(more.failures.isEmpty())
        assertEquals(DiscoveryCoverage(0, 5), board.begun(keep = false, ranked = emptyList()).coverage(mapOf(Discovery.X to 5)))
        assertEquals(DiscoveryCoverage(3, 5), board.coverage(mapOf(Discovery.X to 5)), "a source no longer asked counts no more")
    }

    @Test
    fun theAiOrderLeadsOnceGivenWithTheReadersFeedbackOnTop() {
        val ranked = listOf(candidate("1"), candidate("2"), candidate("3"))
        val board = DiscoveryBoard().aiRanked(listOf("x:3", "x:1", "x:2"), failed = false)
        assertEquals(listOf("3", "1", "2"), board.visible(ranked, emptySet(), emptyMap()).map { it.id })
        val less = mapOf("x:3" to Judgement(Judgement.LESS, emptyList()))
        assertEquals(listOf("1", "2", "3"), board.visible(ranked, emptySet(), less).map { it.id })
        assertEquals(listOf("3", "2"), board.visible(ranked, setOf("x:1"), emptyMap()).map { it.id }, "the followed leave")
        assertEquals(listOf("1", "2", "3"), DiscoveryBoard().visible(ranked, emptySet(), emptyMap()).map { it.id })
    }

    @Test
    fun theAiIsAskedAboutTheCandidatesAsDataAndItsAnswerReadSafely() {
        val one = candidate("1").copy(supporters = listOf(Supporter("a", "ma", "A", DiscoverySignal.REPOSTED), Supporter("b", "mb", "B", DiscoverySignal.REPOSTED)), text = "x".repeat(600))
        val prompt = DiscoveryAi.prompt("Cats", listOf(one, candidate("2")))
        assertTrue(prompt.startsWith("Rank only these real account ids"))
        assertTrue("\"id\":\"x:1\"" in prompt)
        assertTrue("\"signals\":{\"reposted\":2}" in prompt)
        assertTrue("\"members\":2" in prompt)
        assertFalse("x".repeat(501) in prompt, "a sample is cut at 500")
        val candidates = listOf(candidate("1"), candidate("2"), candidate("3"))
        assertEquals(listOf("x:2", "x:1", "x:3"), DiscoveryAi.order("Here: {\"ids\":[\"x:2\",\"x:9\",\"x:2\",\"x:1\"]} done", candidates))
        assertNull(DiscoveryAi.order("no answer", candidates))
        assertNull(DiscoveryAi.order("{\"ids\":[\"x:9\"]}", candidates))
        assertNull(DiscoveryAi.order("{\"ids\":\"x:1\"}", candidates))
    }

    private val bluesky = DiscoveryPlan(perScan = 6, scope = "s")

    @Test
    fun aPluginsFindsAreItsCandidatesWithTheMemberTheyWereFoundAroundVouching() {
        val rows = listOf(SubscriptionRow("bluesky:did:1", "Alice", "alice.bsky.social", plugin = "bluesky"))
        val named = PluginFinds.members("bluesky", listOf("did:1", "did:2"), rows)
        assertEquals(PluginMember("did:1", "alice.bsky.social", "Alice"), named["did:1"])
        assertEquals(PluginMember("did:2", "did:2", "did:2"), named["did:2"])
        val post = NetworkPost("at://p", "did:9", "Nine", "nine.bsky.social")
        val found = PluginDiscovered("did:9", "nine.bsky.social", "Nine", DiscoverySignal.REPOSTED, text = "hello", postUrl = "https://bsky.app/p", postedAt = Instant.ofEpochMilli(2), pointedAt = Instant.ofEpochMilli(7), post = post)
        val answers = mapOf(PluginFinds.keyOf(bluesky, "did:1") to listOf(found, PluginDiscovered("", "", "", DiscoverySignal.QUOTED)))
        val byAccount = PluginFinds.byAccount(answers, bluesky, listOf("did:1", "did:2"))
        assertEquals(setOf("did:1"), byAccount.keys)
        val candidates = PluginFinds.candidates("bluesky", byAccount, named)
        assertEquals(1, candidates.size, "an account without an id or a handle is left out")
        val nine = candidates.single()
        assertEquals("bluesky:did:9", nine.key)
        assertEquals(Supporter("did:1", "alice.bsky.social", "Alice", DiscoverySignal.REPOSTED, 7), nine.supporters.single())
        assertEquals(2L, nine.date)
        assertEquals(ShownPost.Network(post), nine.post)
    }

    @Test
    fun whatThePluginsFollowAndTheGroupsOwnAccountsAreNoCandidates() {
        val rows = listOf(SubscriptionRow("bluesky:did:1", "Alice", "Alice.bsky.social", plugin = "bluesky"), SubscriptionRow("1", "X one", "one"))
        assertEquals(setOf("bluesky:did:1", "bluesky:alice.bsky.social"), PluginFinds.followed(rows))
        assertEquals(setOf("pixiv:42", "mastodon:a@b.social"), PluginFinds.memberIdentities(mapOf("pixiv" to listOf("42"), "mastodon" to listOf("A@b.social"))))
    }
}
