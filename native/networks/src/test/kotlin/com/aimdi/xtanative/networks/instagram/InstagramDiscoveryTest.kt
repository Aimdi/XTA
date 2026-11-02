package com.aimdi.xtanative.networks.instagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InstagramDiscoveryTest {
    private fun post(id: String, handle: String) =
        InstagramPost(id = id, shortcode = id, caption = id, createdAt = null, author = InstagramAuthor(username = handle, fullName = handle))

    @Test
    fun interleaveTakesARoundFromEachPageAndEachPostOnce() {
        val mixed = InstagramDiscovery.interleave(listOf(
            listOf(post("a1", "instagram"), post("a2", "instagram")),
            listOf(post("b1", "natgeo"), post("a1", "instagram")),
            listOf(post("c1", "nasa")),
        ))
        assertEquals(listOf("a1", "b1", "c1", "a2"), mixed.map { it.id })
    }

    @Test
    fun fixedFromXtaARoundOfOnlyRepeatsNoLongerEndsTheMix() {
        val mixed = InstagramDiscovery.interleave(listOf(
            listOf(post("x", "natgeo"), post("y", "natgeo"), post("z", "natgeo")),
            listOf(post("y", "nasa"), post("x", "nasa"), post("w", "nasa")),
        ))
        assertEquals(listOf("x", "y", "z", "w"), mixed.map { it.id }, "XTA stopped after round two, which held only repeats")
    }

    @Test
    fun aTypedUsernameOpensAsAProfileForAGuestOnly() {
        assertTrue(InstagramDiscovery.opensHandle(hasSession = false, query = "@NatGeo"))
        assertFalse(InstagramDiscovery.opensHandle(hasSession = true, query = "@NatGeo"))
        assertFalse(InstagramDiscovery.opensHandle(hasSession = false, query = "some person"))
    }

    @Test
    fun peopleToFollowSkipsAuthorsAlreadyFollowed() {
        val people = InstagramDiscovery.peopleToFollow(
            posts = listOf(post("1", "instagram"), post("2", "NatGeo"), post("3", "instagram"), post("4", "nasa"), post("5", "natgeo")),
            alreadyFollows = { it == "instagram" },
        )
        assertEquals(listOf("NatGeo", "nasa"), people.map { it.username })
    }

    @Test
    fun peopleToFollowStopsAtItsCap() {
        val posts = (1..20).map { post("$it", "person$it") }
        assertEquals(12, InstagramDiscovery.peopleToFollow(posts, alreadyFollows = { false }).size)
        assertEquals(3, InstagramDiscovery.peopleToFollow(posts, alreadyFollows = { false }, cap = 3).size)
    }

    @Test
    fun theGuestMixReadsThreePublicAccounts() {
        assertEquals(listOf("instagram", "natgeo", "nasa"), InstagramDiscovery.HANDLES)
    }
}
