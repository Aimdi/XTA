package com.aimdi.xtanative.ui.group

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.store.follows.Judgement
import com.aimdi.xtanative.store.follows.KnownFollow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A group's Discover (lib/group/group_discovery.dart, group_discovery_x.dart, group_discovery_follows.dart) and its title's menu. */
class DiscoveryTest {
    private fun member(id: String) = FollowedAccount(id, "m$id", "Member $id", null, false, true, 0)

    private fun follow(id: String, bio: String? = null, name: String = "Name $id") = KnownFollow(id, "h$id", name, "https://pbs/$id.jpg", bio)

    private val members = listOf(member("a"), member("b"), member("c")).associateBy { it.id }

    @Test
    fun theAccountsMembersFollowAreCandidatesButNotTheMembersThemselves() {
        val found = Discovery.candidates(mapOf("a" to listOf(follow("x"), follow("b")), "gone" to listOf(follow("y"))), members)
        assertEquals(listOf("x"), found.map { it.id })
        assertEquals(listOf(Supporter("a", "ma", "Member a")), found.single().supporters)
    }

    @Test
    fun anAccountFollowedByManyMembersIsOneCandidateEachMemberOnce() {
        val found = Discovery.candidates(
            mapOf("a" to listOf(follow("x")), "b" to listOf(follow("x", bio = "about x")), "c" to listOf(follow("y"))),
            members,
        ) + Discovery.candidates(mapOf("a" to listOf(follow("x"))), members)
        val merged = Discovery.merged(found)
        assertEquals(listOf("x", "y"), merged.map { it.id })
        assertEquals(2, merged.first().memberCount)
        assertEquals("about x", merged.first().bio, "a mention that has one gives the bio")
    }

    @Test
    fun theMostFollowedComeFirstThenTheReadersFeedbackThenTheGroupsWords() {
        val byMember = mapOf(
            "a" to listOf(follow("one"), follow("cats", name = "Cat facts"), follow("two"), follow("dogs")),
            "b" to listOf(follow("two")),
            "c" to listOf(follow("two"), follow("dogs")),
        )
        val ranked = Discovery.ranked(Discovery.candidates(byMember, members), emptySet(), "Cat lovers", emptyMap())
        assertEquals(listOf("two", "dogs", "cats", "one"), ranked.map { it.id })
        val liked = mapOf(Discovery.identity("one") to Judgement(Judgement.MORE, emptyList()))
        assertEquals(listOf("two", "dogs", "one", "cats"), Discovery.ranked(Discovery.candidates(byMember, members), emptySet(), "Cat lovers", liked).map { it.id })
    }

    @Test
    fun theReadersJudgementsWeighTheAccountMostThenTheWordsItShares() {
        val candidate = Candidate("1", "h1", "Rust news", bio = "systems programming daily")
        val about = mapOf(Discovery.identity("other") to Judgement(Judgement.LESS, listOf("programming", "systems")))
        assertEquals(-2, Discovery.feedbackScore(candidate, about))
        assertEquals(8, Discovery.feedbackScore(candidate, mapOf(candidate.key to Judgement(Judgement.MORE, emptyList()))))
        assertEquals(0, Discovery.feedbackScore(candidate, mapOf(candidate.key to Judgement(Judgement.HIDE, listOf("rust")))))
        assertEquals(listOf("systems", "programming", "daily"), Discovery.judged(candidate, Judgement.LESS).terms)
    }

    @Test
    fun whoTheReaderFollowsAndWhatTheyHidIsLeftOutButWhatTheyJustAddedStays() {
        val found = Discovery.merged(Discovery.candidates(mapOf("a" to listOf(follow("x"), follow("y"), follow("z"))), members))
        val followed = Discovery.followedIdentities(listOf(member("a"), FollowedAccount("y", "hY", "Y", null, false, true, 0), FollowedAccount("q", "hz", "Z", null, false, true, 0)))
        assertTrue(Discovery.follows(followed, found.first { it.id == "y" }), "by id")
        assertTrue(Discovery.follows(followed, found.first { it.id == "z" }), "by handle, whatever the case")
        val hidden = mapOf(Discovery.identity("x") to Judgement(Judgement.HIDE, emptyList()))
        assertEquals(emptyList(), Discovery.visible(found, hidden, followed).map { it.id })
        val sparing = Discovery.followedExcept(followed, setOf(Discovery.identity("y")), found)
        assertEquals(listOf("y"), Discovery.visible(Discovery.ranked(found, sparing, "", hidden), hidden, sparing).map { it.id })
    }

    @Test
    fun anAccountIsToldApartByIdOrHandleWithoutItsAt() {
        assertEquals("x:alice", Discovery.identity(" @Alice "))
        assertEquals(Discovery.identity("123"), Candidate("123", "h", "n").key)
        assertEquals(setOf("cats", "dogs", "42"), Discovery.tokens("Cats & DOGS, a 42!"))
    }

    @Test
    fun theHeaderCountsTheMembersWhoseListsAreRead() {
        assertEquals(2, Discovery.covered(mapOf("a" to emptyList(), "c" to listOf(follow("x")), "gone" to emptyList()), members.keys))
    }

    @Test
    fun eachListMayTakeItsShareOfAMinute() {
        assertEquals(4, Discovery.fetches(more = false))
        assertEquals(8, Discovery.fetches(more = true))
        assertEquals(24_000L, Discovery.memberBudgetMillis(4))
        assertEquals(12_000L, Discovery.memberBudgetMillis(8))
        assertEquals(25_000L, Discovery.memberBudgetMillis(1))
        assertEquals(5_000L, Discovery.memberBudgetMillis(40))
    }

    @Test
    fun theTitlesMenuOffersAFewPinnedGroupsButTheOneOpen() {
        val groups = (1..8).map { FollowGroup("g$it", "G$it", listOf("1"), pinned = it != 2) }
        assertEquals(listOf("g3", "g4", "g5", "g6", "g7"), FeedSwitcher.pinned(groups, currentId = "g1").map { it.id })
        assertEquals(listOf("g1", "g3", "g4", "g5", "g6"), FeedSwitcher.pinned(groups, currentId = null).map { it.id })
    }
}
