package com.aimdi.xtanative.ui.subscriptions

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.data.plugins.PluginKept
import com.aimdi.xtanative.data.plugins.PluginUndo
import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Unsubscribing with XTA's undo (lib/subscriptions/subscription_unfollow.dart, lib/utils/local_undo.dart). */
class UnfollowingTest {
    private fun account(id: String) = FollowedAccount(id, "h$id", "Name $id", null, false, inFeed = id != "2", followedAt = id.toLong())

    private val session = Session(
        following = listOf(account("1"), account("2"), account("3")),
        searches = listOf(SavedSearch("cats", 1), SavedSearch("dogs", 2)),
        groups = listOf(FollowGroup("g1", "One", listOf("2", "cats")), FollowGroup("g2", "Two", listOf("3", "2"))),
        postCaps = mapOf("2" to 3),
    )

    @Test
    fun anUnsubscribedAccountComesBackWhereItWasWithItsGroupsAndItsCap() {
        val (gone, taken) = Unfollows.accounts(session, listOf("2"))
        assertEquals(listOf("1", "3"), gone.following.map { it.id })
        assertTrue(gone.groups.none { "2" in it.memberIds })
        assertEquals(emptyMap(), gone.postCaps)
        val back = Unfollows.restored(gone, taken)!!
        assertEquals(session.following, back.following, "at its place, with its switches")
        assertEquals(setOf("g1", "g2"), back.groups.filter { "2" in it.memberIds }.map { it.id }.toSet())
        assertEquals(mapOf("2" to 3), back.postCaps)
    }

    @Test
    fun anUndoNeverWritesOverALaterFollowAndAGroupDeletedSinceStaysDeleted() {
        val (gone, taken) = Unfollows.accounts(session, listOf("1", "2"))
        assertNull(Unfollows.restored(gone.follow(com.aimdi.xtanative.core.x.model.XUser("2", "h2", "Again"), at = 9), taken))
        val back = Unfollows.restored(gone.withoutGroup("g2"), taken)!!
        assertEquals(listOf("g1"), back.groups.filter { "2" in it.memberIds }.map { it.id })
        assertEquals(listOf("1", "2", "3"), back.following.map { it.id })
    }

    @Test
    fun movingAMemberBetweenGroupsIsUndoneWithoutTouchingOtherMembers() {
        val previous = MembershipUndo.groupsOf(session, "3")
        assertEquals(setOf("g2"), previous)
        val moved = session.withMemberships("3", setOf("g1")).addingTo("1", listOf("g2"))
        val back = MembershipUndo.restored(moved, "3", previous)
        assertEquals(setOf("g2"), MembershipUndo.groupsOf(back, "3"))
        assertEquals(setOf("g2"), MembershipUndo.groupsOf(back, "1"), "a member added since stays")
        assertEquals(setOf("g1", "g2"), MembershipUndo.groupsOf(back, "2"))
        val deleted = MembershipUndo.restored(moved.withoutGroup("g2"), "3", previous)
        assertTrue(deleted.groups.none { it.id == "g2" }, "a group deleted since stays deleted")
        assertTrue(MembershipUndo.groupsOf(deleted, "3").isEmpty())
    }

    @Test
    fun anUnsubscribedSearchComesBackToo() {
        val (gone, taken) = Unfollows.search(session, " cats ")
        assertEquals(listOf("dogs"), gone.searches.map { it.query })
        assertTrue(gone.groups.none { "cats" in it.memberIds })
        val back = Unfollows.restored(gone, taken)!!
        assertEquals(session.searches, back.searches)
        assertTrue("cats" in back.groups.single { it.id == "g1" }.memberIds)
        assertTrue(Unfollows.search(session, "nothing").second.isEmpty)
    }

    @Test
    fun aPluginsOwnSettingsAreItsPrefixAndNothingElse() {
        val settings = Settings(
            mapOf(
                "plugin.pixiv.follows" to PrefValue.Text("[1]"),
                "plugin.pixiv.r18" to PrefValue.Bool(true),
                "plugin.reddit.follows" to PrefValue.Text("[r]"),
                "theme" to PrefValue.Text("dark"),
            ),
        )
        assertEquals(setOf("plugin.pixiv.follows", "plugin.pixiv.r18"), PluginUndo.ownSettings(settings, "pixiv").keys)
        val after = settings.with("plugin.pixiv.follows", null).with("plugin.pixiv.added", PrefValue.Bool(true)).with("theme", PrefValue.Text("light"))
        val back = PluginUndo.withOwn(after, "pixiv", PluginUndo.ownSettings(settings, "pixiv"))
        assertEquals(PluginUndo.ownSettings(settings, "pixiv"), PluginUndo.ownSettings(back, "pixiv"))
        assertEquals(PrefValue.Text("light"), back.values["theme"], "another's settings stay as they are now")
    }

    @Test
    fun onlyTheSectionsTheUnfollowChangedAreGivenBack() {
        val before = PluginKept(mapOf("follows" to "[a,b]", "likes" to "[x]", "old" to null), emptyMap())
        val after = PluginKept(mapOf("follows" to "[a]", "likes" to "[x]", "old" to null), emptyMap())
        assertEquals(mapOf("follows" to "[a,b]"), PluginUndo.changedSections(before, after))
    }
}
