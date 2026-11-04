package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.services.ai.GroupingAccount
import com.aimdi.xtanative.services.ai.GroupingPlan
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UngroupedPlansTest {
    private fun account(id: String, screenName: String, name: String) = FollowedAccount(id, screenName, name, null, false, true, 0)

    private val session = Session(
        following = listOf(
            account("1", "nasa", "NASA"),
            account("2", "spacex", "SpaceX"),
            account("3", "nasa_jpl", "NASA JPL"),
            account("4", "catpics", "Daily cats"),
            account("5", "catfacts", "More cats daily"),
            account("6", "zzz", "qq"),
        ),
        groups = listOf(FollowGroup("space", "Space", listOf("1", "2"))),
    )

    @Test
    fun onlyTheAccountsInNoGroupAreSorted() {
        assertEquals(listOf("3", "4", "5", "6"), UngroupedPlans.ungrouped(session).map { it.id })
        val group = UngroupedPlans.groups(session).single()
        assertEquals(listOf("nasa", "spacex"), group.members.map { it.handle })
    }

    @Test
    fun thePluginsAccountsAreSortedBesideXsAndNamedInTheirGroups() {
        val plugins = listOf(
            SubscriptionRow("bluesky:did:1", "Space Daily", "spacedaily.bsky.social", plugin = "bluesky"),
            SubscriptionRow("reddit:space", "r/space", "space", plugin = "reddit"),
        )
        val grouped = session.withMembership("space", "reddit:space", member = true)
        assertEquals(listOf("3", "4", "5", "6", "bluesky:did:1"), UngroupedPlans.ungrouped(grouped, plugins).map { it.id })
        assertEquals(listOf("nasa", "spacex", "space"), UngroupedPlans.groups(grouped, plugins).single().members.map { it.handle })
        val plan = UngroupedPlans.planByNames(UngroupedPlans.ungrouped(grouped, plugins), UngroupedPlans.groups(grouped, plugins))
        assertTrue(plan.assign.any { it.accountId == "bluesky:did:1" && it.groupId == "space" }, "it shares Space's words")
        val (applied, placed) = UngroupedPlans.applied(grouped, plan, { "new" }, now = 1)
        assertTrue("bluesky:did:1" in applied.groups.single { it.id == "space" }.memberIds)
        assertTrue(placed >= 1)
    }

    @Test
    fun wordsAreTheLatinRunsOfThreeLettersOrMore() {
        assertEquals(setOf("nasa", "jpl"), UngroupedPlans.nameTokens("NASA_JPL"))
        assertEquals(setOf("cats", "daily"), UngroupedPlans.nameTokens("cats & a daily"))
        assertTrue(UngroupedPlans.nameTokens("イラスト").isEmpty())
    }

    @Test
    fun anAccountGoesWhereItSharesMostWordsAndTheRestClusterOrStay() {
        val plan = UngroupedPlans.planByNames(UngroupedPlans.ungrouped(session), UngroupedPlans.groups(session))
        assertEquals(listOf(GroupingPlan.Assignment("3", "space")), plan.assign)
        assertEquals(
            listOf(GroupingPlan.Suggestion("Daily", listOf("4", "5"))),
            plan.suggest,
            "named after the long word most of them share, the first of a tie",
        )
        assertEquals(listOf("6"), plan.leftoverIds)
        assertEquals(3, UngroupedPlans.moveCount(plan))
    }

    @Test
    fun aClusterNeedsAWordOfFourLettersOrMore() {
        val short = listOf(GroupingAccount("a", "abc_one", "x"), GroupingAccount("b", "abc_two", "y"))
        assertTrue(UngroupedPlans.suggestClusters(short).isEmpty())
        val chained = listOf(GroupingAccount("a", "moon_one", ""), GroupingAccount("b", "moon_star", ""), GroupingAccount("c", "star_dust", ""))
        assertEquals(listOf(GroupingPlan.Suggestion("Moon", listOf("a", "b", "c"))), UngroupedPlans.suggestClusters(chained))
    }

    @Test
    fun applyingMakesTheSuggestedGroupsAndAddsTheAssignedWithoutMovingAnyone() {
        val plan = GroupingPlan(
            assign = listOf(GroupingPlan.Assignment("3", "space"), GroupingPlan.Assignment("6", "gone")),
            suggest = listOf(GroupingPlan.Suggestion("Cats", listOf("4", "5")), GroupingPlan.Suggestion("Alone", listOf("6"))),
        )
        val (next, placed) = UngroupedPlans.applied(session, plan, newId = { "new" }, now = 7)
        assertEquals(3, placed, "a group of one and a group that is gone place no one")
        assertEquals(listOf("1", "2", "3"), next.groups.single { it.id == "space" }.memberIds)
        val cats = next.groups.single { it.id == "new" }
        assertEquals("Cats", cats.name)
        assertEquals(listOf("4", "5"), cats.memberIds)
        assertEquals(7, cats.createdAt)
    }
}
