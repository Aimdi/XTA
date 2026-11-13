package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** XTA's substack_follow_identity_test: what following and a publication page's refresh do to the follows. */
class SubstackFollowIdentityTest {
    private fun follows(vararg publications: SubstackPublication): List<SubstackFollow> =
        publications.fold(emptyList()) { follows, publication -> SubstackFollowing.followed(follows, publication, "t") }

    @Test
    fun aPublicationPageNeverRewritesAnotherFollowThatSharesItsGuessedId() {
        val theNewsletter = SubstackPublication("newsletter", "https://newsletter.substack.com", "The Newsletter")
        val page = SubstackPost("9", "Shipping", "shipping", "https://newsletter.pragmaticengineer.com", "The Pragmatic Engineer").publication
        val fresh = page.copy(subdomain = "pragmaticengineer", logoUrl = "https://example.com/logo.png")
        val (after, left) = SubstackFollowing.refreshed(follows(theNewsletter), fresh, "t2")
        assertEquals(listOf(theNewsletter.baseUrl), after.map { it.publication.baseUrl })
        assertNull(left)
        assertNull(SubstackFollows.followOf(after.map { it.publication }, page), "the page shows Follow, not Unfollow")
    }

    @Test
    fun aPublicationPageRefreshesItsOwnFollowUnderThatFollowsId() {
        val followed = SubstackPublication("pragmaticengineer", "https://newsletter.pragmaticengineer.com", "pragmaticengineer")
        val fresh = SubstackPublication("pragmaticengineer", "https://newsletter.pragmaticengineer.com", "The Pragmatic Engineer", "Big Tech and startups, from the inside.", "https://example.com/logo.png")
        val (after, left) = SubstackFollowing.refreshed(follows(followed), fresh, "t2")
        val follow = after.single().publication
        assertEquals("pragmaticengineer", follow.id)
        assertEquals("https://example.com/logo.png", follow.logoUrl)
        assertNull(left)
    }

    @Test
    fun followingTheSameSiteAgainUpdatesItsFollowInPlace() {
        val after = follows(
            SubstackPublication("newsletter", "https://newsletter.alpha.example", "Alpha"),
            SubstackPublication("alpha", "https://www.newsletter.alpha.example", "Alpha 2"),
        )
        assertEquals(listOf("newsletter" to "Alpha 2"), after.map { it.publication.id to it.publication.name })
    }

    @Test
    fun aPublicationFoundUnderItsOwnSubdomainOnAnotherHostIsTheSameFollow() {
        val after = follows(
            SubstackPublication("platformer", "https://platformer.substack.com", "Platformer"),
            SubstackPublication("platformer", "https://www.platformer.news", "Platformer"),
        )
        assertEquals(listOf("https://www.platformer.news"), after.map { it.publication.baseUrl })
    }
}
