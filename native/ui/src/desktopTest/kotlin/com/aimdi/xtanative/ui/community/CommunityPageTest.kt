package com.aimdi.xtanative.ui.community

import com.aimdi.xtanative.core.x.model.XCommunity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CommunityPageTest {
    private val community = XCommunity(
        id = "7",
        name = "Rocketry",
        description = "",
        rules = listOf("Be kind", "No spam"),
        memberAvatarUrls = (1..8).map { "https://pbs.twimg.com/$it.jpg" },
    )

    @Test
    fun theBannerIsAThirdOfTheWidthWithinXtasBounds() {
        assertEquals(130f, CommunityPage.bannerHeight(390f))
        assertEquals(CommunityPage.BANNER_MIN_DP, CommunityPage.bannerHeight(200f))
        assertEquals(CommunityPage.BANNER_MAX_DP, CommunityPage.bannerHeight(1200f))
    }

    @Test
    fun theHeaderShowsFiveFacesAndTheAboutTabNumbersTheRules() {
        assertEquals(5, CommunityPage.faces(community).size)
        assertEquals(listOf(1 to "Be kind", 2 to "No spam"), CommunityPage.numberedRules(community))
        assertNull(CommunityPage.description(community))
        assertEquals("Hi", CommunityPage.description(community.copy(description = "Hi")))
    }

    @Test
    fun anAboutLinkOpensOnAbout() {
        assertEquals(CommunityTab.ABOUT, CommunityPage.openingTab(about = true))
        assertEquals(CommunityTab.TOP, CommunityPage.openingTab(about = false))
    }
}
