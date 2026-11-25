package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.saved.ProfileFilters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The ⋯ menu's "Hide posts from @handle" and "Show posts from @handle". */
class HideAuthorTest {
    private fun post(id: String, by: String, reposted: XPost? = null, quoted: XPost? = null) =
        XPost(id, XUser("id-$by", by, by), null, "post $id", reposted = reposted, quoted = quoted)

    private val none = ProfileFilters()

    @Test
    fun aPostsEntryHidesItsAuthorAndOnceHiddenShowsThemAgain() {
        assertEquals(HideAuthor("id-Tommy", "Tommy", hidden = false), HideAuthor.of(post("1", "Tommy"), none))
        val hidden = ProfileFilters(hiddenAll = mapOf("id-Tommy" to "tommy"))
        assertEquals(HideAuthor("id-Tommy", "Tommy", hidden = true), HideAuthor.of(post("1", "Tommy"), hidden))
        assertEquals(false, HideAuthor.of(post("2", "maya"), hidden)?.hidden)
    }

    @Test
    fun aRepostsEntryIsAboutWhoWroteThePostNotWhoRepostedIt() {
        val repost = post("1", "maya", reposted = post("2", "tommy"))
        assertEquals("tommy", HideAuthor.of(repost, none)?.screenName)
        assertEquals("id-tommy", HideAuthor.of(repost, none)?.userId)
        assertEquals("maya", HideAuthor.of(post("3", "maya", quoted = post("4", "tommy")), none)?.screenName, "a quote is its author's own post")
    }

    @Test
    fun hidingRepostsOrRepliesAloneIsNotHidingTheAccount() {
        val filters = ProfileFilters(hiddenReposts = mapOf("id-tommy" to "tommy"), hiddenReplies = mapOf("id-tommy" to "tommy"))
        assertEquals(false, HideAuthor.of(post("1", "tommy"), filters)?.hidden)
    }

    @Test
    fun thereIsNoEntryBeforeTheFiltersAreReadNorForAnAuthorWithoutIdOrHandle() {
        assertNull(HideAuthor.of(post("1", "tommy"), null))
        assertNull(HideAuthor.of(XPost("1", XUser("", "tommy", "Tommy"), null, ""), none))
        assertNull(HideAuthor.of(XPost("1", XUser("7", "", "Tommy"), null, ""), none))
    }
}
