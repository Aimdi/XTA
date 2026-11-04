package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.store.follows.KnownFollow
import com.aimdi.xtanative.ui.plugins.DiscoverySignal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** A group's Discover (lib/group/group_discovery.dart, group_discovery_x.dart, group_discovery_follows.dart) and its title's menu. */
class DiscoveryTest {
    private fun member(id: String) = FollowedAccount(id, "m$id", "Member $id", null, false, true, 0)

    private fun follow(id: String, bio: String? = null, name: String = "Name $id") = KnownFollow(id, "h$id", name, "https://pbs/$id.jpg", bio)

    private val members = listOf(member("a"), member("b"), member("c")).associateBy { it.id }

    private fun pointed(id: String, member: String, signal: DiscoverySignal, at: Long? = null, source: String = Discovery.X, text: String = "") =
        Candidate(id, "h$id", "Name $id", supporters = listOf(Supporter(member, "m$member", "Member $member", signal, at)), source = source, text = text)

    @Test
    fun theAccountsMembersFollowAreCandidatesButNotTheMembersThemselves() {
        val found = XDiscovery.fromFollows(mapOf("a" to listOf(follow("x"), follow("b")), "gone" to listOf(follow("y"))), members)
        assertEquals(listOf("x"), found.map { it.id })
        assertEquals(listOf(Supporter("a", "ma", "Member a", DiscoverySignal.FOLLOWED)), found.single().supporters)
    }

    @Test
    fun anAccountPointedAtByManyMembersIsOneCandidateEachMemberOnceForEachSignal() {
        val found = XDiscovery.fromFollows(
            mapOf("a" to listOf(follow("x")), "b" to listOf(follow("x", bio = "about x")), "c" to listOf(follow("y"))),
            members,
        ) + XDiscovery.fromFollows(mapOf("a" to listOf(follow("x"))), members) + pointed("x", "a", DiscoverySignal.REPOSTED, at = 5)
        val merged = Discovery.merged(found)
        assertEquals(listOf("x", "y"), merged.map { it.id })
        val x = merged.first()
        assertEquals(2, x.memberCount)
        assertEquals(3, x.supporters.size, "a followed and reposted it, b followed it")
        assertEquals("about x", x.bio, "a mention that has one gives the bio")
        assertEquals(DiscoverySignal.REPOSTED, x.supporters.first().signal, "the newest support first")
    }

    @Test
    fun mostMembersComeFirstThenTheStrongestSignalsThenFeedbackThenTheNewestThenTheGroupsWords() {
        val found = listOf(
            pointed("mention", "a", DiscoverySignal.MENTIONED),
            pointed("repost", "a", DiscoverySignal.REPOSTED),
            pointed("two", "a", DiscoverySignal.MENTIONED),
            pointed("two", "b", DiscoverySignal.MENTIONED),
            pointed("old", "c", DiscoverySignal.QUOTED, at = 1),
            pointed("new", "c", DiscoverySignal.QUOTED, at = 9),
            pointed("cats", "b", DiscoverySignal.MENTIONED, text = "cat pictures"),
        )
        val ranked = Discovery.ranked(found, emptySet(), "Cat pictures", emptyMap())
        assertEquals(listOf("two", "new", "old", "repost", "cats", "mention"), ranked.map { it.id })
        val liked = mapOf(Discovery.identity("mention") to Judgement(Judgement.MORE, emptyList()))
        assertEquals(listOf("two", "new", "old", "repost", "mention", "cats"), Discovery.ranked(found, emptySet(), "", liked).map { it.id })
    }

    @Test
    fun theReadersJudgementsWeighTheAccountMostThenTheWordsItShares() {
        val candidate = Candidate("1", "h1", "Rust news", bio = "systems programming daily")
        val about = mapOf(Discovery.identity("other") to Judgement(Judgement.LESS, listOf("programming", "systems")))
        assertEquals(-2, Discovery.feedbackScore(candidate, about))
        assertEquals(8, Discovery.feedbackScore(candidate, mapOf(candidate.key to Judgement(Judgement.MORE, emptyList()))))
        assertEquals(0, Discovery.feedbackScore(candidate, mapOf(candidate.key to Judgement(Judgement.HIDE, listOf("rust")))))
        assertEquals(listOf("systems", "programming", "daily"), Discovery.judged(candidate, Judgement.LESS).terms)
        assertEquals("post text", candidate.copy(text = "post text").snippet, "what shows it comes before the bio")
    }

    @Test
    fun feedbackOrdersOnTopOfAnotherOrderKeepingItsTies() {
        val list = listOf(Candidate("1", "h1", "a"), Candidate("2", "h2", "b"), Candidate("3", "h3", "c"))
        val less = mapOf(list[0].key to Judgement(Judgement.LESS, emptyList()))
        assertEquals(listOf("2", "3", "1"), Discovery.feedbackOrdered(list, less).map { it.id })
    }

    @Test
    fun whoTheReaderFollowsAndWhatTheyHidIsLeftOutButWhatTheyJustAddedStays() {
        val found = Discovery.merged(XDiscovery.fromFollows(mapOf("a" to listOf(follow("x"), follow("y"), follow("z"))), members))
        val followed = Discovery.followedIdentities(listOf(member("a"), FollowedAccount("y", "hY", "Y", null, false, true, 0), FollowedAccount("q", "hz", "Z", null, false, true, 0)))
        assertTrue(Discovery.follows(followed, found.first { it.id == "y" }), "by id")
        assertTrue(Discovery.follows(followed, found.first { it.id == "z" }), "by handle, whatever the case")
        val hidden = mapOf(Discovery.identity("x") to Judgement(Judgement.HIDE, emptyList()))
        assertEquals(emptyList(), Discovery.visible(found, hidden, followed).map { it.id })
        val sparing = Discovery.followedExcept(followed, setOf(Discovery.identity("y")), found)
        assertEquals(listOf("y"), Discovery.visible(Discovery.ranked(found, sparing, "", hidden), hidden, sparing).map { it.id })
    }

    @Test
    fun anAccountIsToldApartByItsNetworkAndIdOrHandleWithoutItsAt() {
        assertEquals("x:alice", Discovery.identity(" @Alice "))
        assertEquals("bluesky:did:plc:1", Discovery.identity("bluesky", "did:plc:1"))
        assertEquals(Discovery.identity("123"), Candidate("123", "h", "n").key)
        assertEquals("mastodon:a@b.social", Candidate("a@b.social", "a@b.social", "A", source = "mastodon").key)
        assertEquals("x:named", Candidate("", "Named", "Named").key, "an account met only by name goes by its handle")
        assertEquals(setOf("cats", "dogs", "42"), Discovery.tokens("Cats & DOGS, a 42!"))
    }

    @Test
    fun anAccountMetOnlyByNameTakesTheIdAnotherMentionOfItHas() {
        val named = Candidate("", "HX", "HX", supporters = listOf(Supporter("b", "mb", "Member b", DiscoverySignal.MENTIONED)))
        val merged = Discovery.merged(listOf(named, pointed("x", "a", DiscoverySignal.REPOSTED)))
        assertEquals(listOf("x"), merged.map { it.id })
        assertEquals("Name x", merged.single().name, "a name that is only the handle gives way")
        assertEquals(2, merged.single().memberCount)
        assertEquals(listOf("", "y"), Discovery.resolved(listOf(named.copy(handle = "nobody"), pointed("y", "a", DiscoverySignal.QUOTED))).map { it.id })
    }

    @Test
    fun eachListAndEachPageMayTakeItsShareOfAMinute() {
        assertEquals(4, Discovery.fetches(more = false))
        assertEquals(8, Discovery.fetches(more = true))
        assertEquals(6, Discovery.rotating(Discovery.AUTHORS_PER_LOAD, more = false))
        assertEquals(12, Discovery.rotating(Discovery.AUTHORS_PER_LOAD, more = true))
        assertEquals(24_000L, Discovery.memberBudgetMillis(4))
        assertEquals(12_000L, Discovery.memberBudgetMillis(8))
        assertEquals(25_000L, Discovery.memberBudgetMillis(1))
        assertEquals(5_000L, Discovery.memberBudgetMillis(40))
    }

    @Test
    fun aRowSaysWhyFromTheSignalMostOfItsSupportersShare() {
        val reposted = Candidate(
            "x", "hx", "X",
            supporters = listOf(
                Supporter("a", "ma", "A", DiscoverySignal.REPOSTED),
                Supporter("b", "", "Bee", DiscoverySignal.REPOSTED),
                Supporter("c", "mc", "C", DiscoverySignal.REPOSTED),
                Supporter("d", "md", "D", DiscoverySignal.FOLLOWED),
            ),
        )
        assertEquals(DiscoveryReason(DiscoverySignal.REPOSTED, listOf("@ma", "Bee"), 3), DiscoveryReason.of(reposted))
        assertEquals(1, DiscoveryReason.of(reposted)?.more)
        val followed = XDiscovery.fromFollows(mapOf("a" to listOf(follow("x")), "b" to listOf(follow("x"))), members)
        assertEquals(DiscoveryReason(DiscoverySignal.FOLLOWED, emptyList(), 2), DiscoveryReason.of(Discovery.merged(followed).single()))
        assertNull(DiscoveryReason.of(Candidate("x", "hx", "X")))
    }

    @Test
    fun theTitlesMenuOffersAFewPinnedGroupsButTheOneOpen() {
        val groups = (1..8).map { FollowGroup("g$it", "G$it", listOf("1"), pinned = it != 2) }
        assertEquals(listOf("g3", "g4", "g5", "g6", "g7"), FeedSwitcher.pinned(groups, currentId = "g1").map { it.id })
        assertEquals(listOf("g1", "g3", "g4", "g5", "g6"), FeedSwitcher.pinned(groups, currentId = null).map { it.id })
    }
}
