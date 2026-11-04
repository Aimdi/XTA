package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.store.settings.SubscriptionsOrder
import com.aimdi.xtanative.ui.plugins.PluginSubscription
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    private val threads = SubscriptionList.pluginRow("threads", PluginSubscription("aria", "Aria", avatarUrl = "https://t/a.jpg", route = "profile?handle=aria", followedAt = 25) {})
    private val reddit = SubscriptionList.pluginRow("reddit", PluginSubscription("dartlang", "DartLang", subtitle = "r/DartLang") {})
    private val bluesky = SubscriptionList.pluginRow("bluesky", PluginSubscription("did:plc:maya", "Maya", handle = "maya.bsky.social", backupId = "maya.bsky.social") {})
    private val tiktok = SubscriptionList.pluginRow("tiktok", PluginSubscription("dancer", "Dancer", followedAt = 5) {})

    @Test
    fun aPluginsAccountIsItsGroupMemberWithWhatItsPluginSaysOfIt() {
        assertEquals("threads:aria", threads.id)
        assertEquals("threads", threads.plugin)
        assertEquals("@aria", threads.subtitle, "a handle under the name, as XTA's subscriptions say it")
        assertEquals("r/DartLang", reddit.subtitle)
        assertEquals("profile?handle=aria", threads.route)
        assertEquals("https://t/a.jpg", threads.avatarUrl)
        assertEquals("maya.bsky.social", bluesky.screenName)
        assertEquals("@maya.bsky.social", bluesky.subtitle)
        assertFalse(threads.isX || threads.search)
        assertTrue(rows.first().isX)
    }

    @Test
    fun thePluginsAccountsComeAfterXsAndAreOrderedAndFoundWithThem() {
        val all = SubscriptionList.rows(session, listOf(threads, reddit, bluesky, tiktok))
        assertEquals(listOf("1", "2", "3", "from:nasa moon", "threads:aria", "reddit:dartlang", "bluesky:did:plc:maya", "tiktok:dancer"), ids(all))
        assertEquals(
            listOf("3", "threads:aria", "tiktok:dancer", "reddit:dartlang", "2", "from:nasa moon", "bluesky:did:plc:maya", "1"),
            ids(SubscriptionList.ordered(all, SubscriptionsOrder.NAME, true, "")),
        )
        assertEquals(
            listOf("reddit:dartlang", "bluesky:did:plc:maya", "tiktok:dancer", "2", "3", "threads:aria", "1", "from:nasa moon"),
            ids(SubscriptionList.ordered(all, SubscriptionsOrder.CREATED_AT, true, "")),
            "one followed at a time not known first",
        )
        assertEquals(listOf("bluesky:did:plc:maya"), ids(SubscriptionList.filtered(all, "maya.bsky")), "by its handle")
        assertEquals(listOf("reddit:dartlang"), ids(SubscriptionList.filtered(all, "dartlang")))
    }

    @Test
    fun theReadersOwnOrderKeepsAPluginsAccountAsXtasListDoes() {
        val all = SubscriptionList.rows(session, listOf(threads, bluesky, tiktok))
        val ordered = SubscriptionList.ordered(all, SubscriptionsOrder.NAME, true, "tiktok:dancer,maya.bsky.social,aria,3")
        assertEquals(listOf("tiktok:dancer", "bluesky:did:plc:maya", "threads:aria", "3"), ids(ordered).take(4), "XTA's ids: a bare network's account by its subscription's")
        assertEquals("tiktok:dancer,maya.bsky.social,aria,3,1,2,from:nasa moon", SubscriptionList.customOrder(ordered))
    }

    @Test
    fun aPublicationOrAFeedIsNamedByItsSiteUnderItsName() {
        assertEquals("astral.substack.com", PluginSubscription.siteOf("https://astral.substack.com"))
        assertEquals("example.org", PluginSubscription.siteOf("https://example.org/feed.xml"))
        assertEquals("feed", PluginSubscription.siteOf("feed"), "an address naming no site is shown as it is")
    }

    @Test
    fun aSearchAndAnAccountSharingAnIdAreBothKeptInTheReadersOrder() {
        val both = listOf(SubscriptionRow("42", "42", "42", search = true), SubscriptionRow("42", "Answer", "answer"))
        assertEquals(2, SubscriptionList.ordered(both, SubscriptionsOrder.NAME, true, "42").size)
    }

    @Test
    fun onlyTheCapsXtaOffersArePicked() {
        assertEquals(5, SubscriptionList.capChoice(5))
        assertNull(SubscriptionList.capChoice(7))
        assertNull(SubscriptionList.capChoice(null))
    }
}
