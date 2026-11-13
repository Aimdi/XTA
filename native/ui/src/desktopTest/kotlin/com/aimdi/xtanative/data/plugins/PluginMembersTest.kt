package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.core.session.SavedSearch
import com.aimdi.xtanative.core.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginMembersTest {
    private val session = Session(
        groups = listOf(
            FollowGroup("art", "Art", listOf("12", "bluesky:did:plc:maya", "reddit:kotlin", "from:someone", "blueskyfan")),
            FollowGroup("inner", "Inner", listOf("bluesky:alice.bsky.social", "bluesky:did:plc:maya")),
            FollowGroup("other", "Other", listOf("reddit:android")),
        ),
        searches = listOf(SavedSearch("from:someone", 0)),
        nesting = mapOf("inner" to "art"),
    )

    @Test
    fun aGroupsPluginMembersAreItsAndItsNestedGroupsByPluginEachOnce() {
        val members = PluginMembers.of(session, "art", emptySet(), setOf("bluesky", "reddit"))
        assertEquals(mapOf("bluesky" to listOf("did:plc:maya", "alice.bsky.social"), "reddit" to listOf("kotlin")), members)
    }

    @Test
    fun aSearchWithAColonStaysASearchAndAPluginNotRunningIsLeftOut() {
        val members = PluginMembers.of(session, "art", emptySet(), setOf("bluesky", "from"))
        assertEquals(setOf("bluesky"), members.keys, "\"from:someone\" is a search followed here, reddit is not running")
    }

    @Test
    fun theGroupsReadAlongsideBringTheirMembersToo() {
        val members = PluginMembers.of(session, "inner", setOf("other"), setOf("reddit", "bluesky"))
        assertEquals(listOf("android"), members["reddit"])
        assertEquals(listOf("alice.bsky.social", "did:plc:maya"), members["bluesky"])
    }

    @Test
    fun aMembersPageIsAskedOfThePluginWhoseAccountItIs() {
        assertEquals("websources" to "lobsters/hottest", PluginMembers.owner("websources:lobsters/hottest", setOf("websources", "rss")))
        assertEquals("rss" to "https://blog.example/feed", PluginMembers.owner("rss:https://blog.example/feed", setOf("rss")), "an account holding a colon keeps it")
        assertEquals(null, PluginMembers.owner("12", setOf("rss")), "an X account is X's")
        assertEquals(null, PluginMembers.owner("bluesky:did:plc:maya", setOf("rss")), "a plugin that is not running opens nothing")
        assertEquals(null, PluginMembers.owner("from:someone", setOf("from"), searches = setOf("from:someone")), "a search followed here stays a search")
    }

    @Test
    fun anAccountItsPluginUnfollowedLeavesEveryGroupAndTheOthersStay() {
        val left = PluginMembers.unfollowed(session, "bluesky", "did:plc:maya")
        assertEquals(listOf("12", "reddit:kotlin", "from:someone", "blueskyfan"), left.groups.first { it.id == "art" }.memberIds)
        assertEquals(listOf("bluesky:alice.bsky.social"), left.groups.first { it.id == "inner" }.memberIds)
        assertEquals(session.groups.last(), left.groups.last())
        assertEquals(session, PluginMembers.unfollowed(session, "from", "someone"), "a saved search spelled so stays the search it is")
    }

    @Test
    fun aMemberIsThePluginsIdAndItsAccount() {
        assertEquals("bluesky:did:plc:maya", PluginMembers.memberId("bluesky", "did:plc:maya"))
        assertTrue(PluginMembers.accounts(listOf("bluesky:", ":x", "12"), setOf("bluesky", "")).isEmpty(), "no account, or no plugin, is no member")
    }
}
