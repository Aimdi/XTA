package com.aimdi.xtanative.networks.instagram

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InstagramLinkTest {
    @Test
    fun aProfileLinkOpensTheProfile() {
        assertEquals(InstagramLink("nasa"), InstagramLink.parse("https://www.instagram.com/nasa/"))
        assertEquals(InstagramLink("nasa"), InstagramLink.parse("instagram.com/NASA?igsh=abc"))
        assertEquals(InstagramLink("nat.geo_1"), InstagramLink.parse(" http://instagram.com/nat.geo_1 "))
    }

    @Test
    fun postsReelsAndInstagramsOwnPagesAreNotProfiles() {
        listOf(
            "https://www.instagram.com/p/AbC123/",
            "https://www.instagram.com/reel/AbC123/",
            "https://www.instagram.com/explore/",
            "https://www.instagram.com/accounts/login/",
            "https://www.instagram.com/stories/nasa/123/",
            "https://www.instagram.com/",
        ).forEach { assertNull(InstagramLink.parse(it), it) }
    }

    @Test
    fun otherSitesAndSchemesAreNotInstagram() {
        listOf(
            "https://example.com/nasa",
            "https://m.instagram.com/nasa",
            "ftp://instagram.com/nasa",
            "instagram://user?username=nasa",
            "",
        ).forEach { assertNull(InstagramLink.parse(it), it) }
    }
}
