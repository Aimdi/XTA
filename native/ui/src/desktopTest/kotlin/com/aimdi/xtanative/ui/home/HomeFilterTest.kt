package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.feed.FeedSettings
import com.aimdi.xtanative.core.feed.FeedSort
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.XAccount
import com.aimdi.xtanative.store.settings.AccountPrefs
import com.aimdi.xtanative.store.settings.HomePrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's home feed accounts sheet (lib/home/home_filter_sheet.dart, home_account_filter.dart, home_group_filter.dart). */
class HomeFilterTest {
    private val accounts = listOf(XAccount("1", "one", emptyMap()), XAccount("2", "two", emptyMap()))
    private val groups = listOf(FollowGroup("g1", "One", listOf("10")), FollowGroup("g2", "Two", listOf("20")))

    @Test
    fun theDraftTogglesOnlyAndResets() {
        val draft = HomeFilterDraft().toggled(groups = false, "1", on = false).toggled(groups = true, "g2", on = false)
        assertEquals(setOf("1"), draft.accountsOff)
        assertEquals(setOf("g2"), draft.groupsOff)
        assertEquals(setOf("1"), draft.only(groups = false, "2", listOf("1", "2")).accountsOff)
        assertEquals(setOf("g1"), draft.only(groups = true, "g2", listOf("g1", "g2")).groupsOff)
        assertEquals(1, draft.active(groups = false, listOf("1", "2")))
        assertEquals(HomeFilterDraft(), HomeFilterDraft.NONE_OFF)
        assertEquals(emptySet(), draft.toggled(groups = false, "1", on = true).accountsOff)
    }

    @Test
    fun allAccountsOffCannotBeApplied() {
        val ids = accounts.map { it.id }
        assertTrue(HomeFilterDraft().keepsAnAccount(ids))
        assertFalse(HomeFilterDraft().selecting(groups = false, ids.toSet()).keepsAnAccount(ids))
        assertTrue(HomeFilterDraft(accountsOff = setOf("1")).keepsAnAccount(ids))
        assertTrue(HomeFilterDraft(accountsOff = setOf("1")).keepsAnAccount(emptyList()))
    }

    @Test
    fun theFilterIsKeptInTheSettingsAndTheSession() {
        val session = Session(accounts = accounts, groups = groups, offFollowing = setOf("g1"))
        val draft = HomeFilterDraft(accountsOff = setOf("2"), groupsOff = setOf("g2"))
        val settings = HomeFilter.applied(Settings(), draft)
        assertEquals(listOf("2"), settings[AccountPrefs.disabledAccountIds])
        assertEquals(listOf("g2"), settings[HomePrefs.followingHiddenGroupIds])
        assertEquals(setOf("g2"), HomeFilter.applied(session, draft).offFollowing)
        assertEquals(draft, HomeFilter.draftOf(HomeFilter.applied(session, draft), settings))
    }

    @Test
    fun theButtonCountsWhatIsOffAndStillThere() {
        val draft = HomeFilterDraft(accountsOff = setOf("2", "gone"), groupsOff = setOf("g1", "old"))
        assertEquals(2, HomeFilter.turnedOff(draft, accounts, groups))
        assertEquals(0, HomeFilter.turnedOff(HomeFilterDraft(), accounts, groups))
    }

    @Test
    fun onlyFeedsThatKeepAPositionLightUp() {
        val session = Session(
            groups = groups,
            feeds = mapOf("g1" to FeedSettings(sort = FeedSort.POPULAR), "g2" to FeedSettings(catchUp = true)),
        )
        assertTrue(HomeUnread.tracks(session, HomeTab.Following, readingPosition = true))
        assertFalse(HomeUnread.tracks(session, HomeTab.Following, readingPosition = false))
        assertFalse(HomeUnread.tracks(session, HomeTab.Group("g1", "One"), readingPosition = true))
        assertTrue(HomeUnread.tracks(session, HomeTab.Group("g2", "Two"), readingPosition = false))
        assertTrue(HomeUnread.tracks(session, HomeTab.ForYou, readingPosition = true), "For you keeps its place as XTA's does")
        assertFalse(HomeUnread.tracks(session, HomeTab.ForYou, readingPosition = false))
        assertFalse(HomeUnread.tracks(session, HomeTab.Network("reddit"), readingPosition = true))
        assertEquals(listOf("following", "for_you", "group:g1", "group:g2"), HomeUnread.feeds(session).map { it.key })
        assertEquals("for_you", HomeUnread.feedKey(HomeTab.ForYou))
    }
}
