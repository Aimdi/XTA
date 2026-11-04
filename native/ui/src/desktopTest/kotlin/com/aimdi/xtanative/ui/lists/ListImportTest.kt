package com.aimdi.xtanative.ui.lists

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.session.Session
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ListImportTest {
    @Test
    fun aListIsNamedByItsIdOrItsLink() {
        assertEquals("1234567890", ListIds.of(" 1234567890 "))
        assertEquals("123", ListIds.of("https://x.com/i/lists/123"))
        assertEquals("123", ListIds.of("https://twitter.com/i/lists/123?s=20"))
        assertEquals("123", ListIds.of("x.com/i/lists/123"))
        assertNull(ListIds.of("https://x.com/i/lists/123/members"))
        assertNull(ListIds.of("https://x.com/maya"))
        assertNull(ListIds.of("lists 123"))
        assertNull(ListIds.of(""))
    }

    @Test
    fun eachPageAddsTheMembersNotMetBefore() {
        val seen = HashSet<String>()
        val first = ListImport.fresh(listOf(XUser("1", "a", "A"), XUser("2", "b", "B"), XUser("3", "", "No handle")), seen)
        assertEquals(listOf("1", "2"), first.map { it.id })
        val second = ListImport.fresh(listOf(XUser("2", "b", "B"), XUser("4", "d", "D")), seen)
        assertEquals(listOf("4"), second.map { it.id })
    }

    @Test
    fun theMembersAreInAtTheLastPage() {
        val someone = listOf(XUser("1", "a", "A"))
        assertFalse(ListImport.ended("c2", "c1", someone))
        assertTrue(ListImport.ended(null, "c1", someone))
        assertTrue(ListImport.ended("", "c1", someone))
        assertTrue(ListImport.ended("0", "c1", someone))
        assertTrue(ListImport.ended("c1", "c1", someone))
        assertTrue(ListImport.ended("c2", "c1", emptyList()))
    }

    @Test
    fun membersAreFollowedHereAndThoseFollowedAlreadyStayAsTheyWere() {
        val known = FollowedAccount("1", "a", "Old name", null, false, inFeed = false, followedAt = 2)
        val session = ListImport.followed(Session(following = listOf(known)), listOf(XUser("1", "a", "New name"), XUser("2", "b", "B")), at = 9)
        assertEquals(known, session.followed("1"))
        assertEquals(9, session.followed("2")?.followedAt)
        assertEquals(true, session.followed("2")?.inFeed)
    }

    @Test
    fun theGroupIsNamedAsTheListAndHoldsEveryMember() {
        val session = ListImport.grouped(Session(), "g1", "Space news", linkedSetOf("2", "1"))
        assertEquals("Space news", session.groups.single().name)
        assertEquals(listOf("2", "1"), session.groups.single().memberIds)
    }
}
