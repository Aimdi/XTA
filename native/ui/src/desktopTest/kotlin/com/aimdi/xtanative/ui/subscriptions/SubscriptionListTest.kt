package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.SubscriptionsOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SubscriptionListTest {
    private fun account(id: String, screenName: String, name: String, followedAt: Long, inFeed: Boolean = true) =
        FollowedAccount(id, screenName, name, null, verified = false, inFeed = inFeed, followedAt = followedAt)

    private val session = Session(
        following = listOf(account("1", "nasa", "NASA", 30), account("2", "esa", "esa", 10, inFeed = false), account("3", "Zed", "alpha", 20)),
        searches = listOf(SavedSearch("from:nasa moon", 40)),
        postCaps = mapOf("1" to 3),
    )
    private val rows = SubscriptionList.rows(session)

    private fun ids(rows: List<SubscriptionRow>) = rows.map { it.id }

    @Test
    fun theListHoldsTheAccountsFollowedThenTheSavedSearches() {
        assertEquals(listOf("1", "2", "3", "from:nasa moon"), ids(rows))
        val search = rows.last()
        assertTrue(search.search)
        assertEquals("from:nasa moon", search.name)
        assertEquals(3, rows.first().postCap)
        assertEquals(false, rows[1].inFeed)
    }

    @Test
    fun itSortsByNameHandleOrDateEitherWayWhateverTheCase() {
        assertEquals(listOf("3", "2", "from:nasa moon", "1"), ids(SubscriptionList.ordered(rows, SubscriptionsOrder.NAME, true, "")))
        assertEquals(listOf("1", "from:nasa moon", "2", "3"), ids(SubscriptionList.ordered(rows, SubscriptionsOrder.NAME, false, "")))
        assertEquals(listOf("2", "from:nasa moon", "1", "3"), ids(SubscriptionList.ordered(rows, SubscriptionsOrder.SCREEN_NAME, true, "")))
        assertEquals(listOf("2", "3", "1", "from:nasa moon"), ids(SubscriptionList.ordered(rows, SubscriptionsOrder.CREATED_AT, true, "")))
    }

    @Test
    fun theReadersOwnOrderWinsAndKeepsNewFollowsAtTheEnd() {
        val custom = "3,nasa,gone"
        val ordered = SubscriptionList.ordered(rows, SubscriptionsOrder.NAME, true, custom)
        assertEquals(listOf("3", "1", "2", "from:nasa moon"), ids(ordered), "by id or by handle, as older builds kept it")
        assertEquals("3,1,2,from:nasa moon", SubscriptionList.customOrder(ordered))
    }

    @Test
    fun aSearchFindsTheNameOrTheHandle() {
        assertEquals(listOf("1", "from:nasa moon"), ids(SubscriptionList.filtered(rows, " NASA ")))
        assertEquals(listOf("3"), ids(SubscriptionList.filtered(rows, "zed")))
        assertEquals(rows, SubscriptionList.filtered(rows, "  "))
    }

    @Test
    fun aDraggedRowLandsWhereItWasDropped() {
        assertEquals(listOf("b", "c", "a"), SubscriptionList.moved(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), SubscriptionList.moved(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), SubscriptionList.moved(listOf("a", "b"), 5, 0))
    }

    @Test
    fun onlyTheCapsXtaOffersArePicked() {
        assertEquals(5, SubscriptionList.capChoice(5))
        assertNull(SubscriptionList.capChoice(7))
        assertNull(SubscriptionList.capChoice(null))
    }
}
