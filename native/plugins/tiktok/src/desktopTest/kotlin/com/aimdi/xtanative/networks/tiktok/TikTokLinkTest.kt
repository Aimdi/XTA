package com.aimdi.xtanative.networks.tiktok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TikTokLinkTest {
    @Test
    fun aProfileAndAVideoOpenInTheApp() {
        assertEquals(TikTokLink.Profile("nasa"), TikTokLink.parse("https://www.tiktok.com/@nasa"))
        assertEquals(TikTokLink.Profile("nasa"), TikTokLink.parse("tiktok.com/@NASA?lang=en"))
        assertEquals(TikTokLink.Profile("nasa"), TikTokLink.parse("https://m.tiktok.com/@nasa/live"))
        assertEquals(TikTokLink.Video(id = "1234567890", handle = "nasa"), TikTokLink.parse("https://www.tiktok.com/@nasa/video/1234567890"))
        assertEquals(TikTokLink.Profile("nasa"), TikTokLink.parse("https://www.tiktok.com/@nasa/video/abc"), "a video id is digits")
    }

    @Test
    fun shortLinksOpenOnTheSite() {
        assertNull(TikTokLink.parse("https://vm.tiktok.com/ZMabc/"))
        assertNull(TikTokLink.parse("https://vt.tiktok.com/ZSabc/"))
    }

    @Test
    fun fixedFromXtaAPathWithoutAnAtNamesNoAccount() {
        listOf(
            "https://www.tiktok.com/t/ZT8abcdef/",
            "https://www.tiktok.com/embed/v3/1234567890",
            "https://www.tiktok.com/nasa",
            "https://www.tiktok.com/foryou",
            "https://www.tiktok.com/tag/cats",
        ).forEach { assertNull(TikTokLink.parse(it), "$it named an account in XTA") }
    }

    @Test
    fun otherSitesAndSchemesAreNotTikTok() {
        listOf(
            "https://example.com/@nasa",
            "https://nottiktok.com/@nasa",
            "snssdk1233://user/profile/1",
            "https://www.tiktok.com/",
            "",
        ).forEach { assertNull(TikTokLink.parse(it), it) }
    }
}
