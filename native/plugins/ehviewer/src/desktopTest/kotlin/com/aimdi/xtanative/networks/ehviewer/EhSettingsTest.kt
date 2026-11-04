package com.aimdi.xtanative.networks.ehviewer

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The cookies and site choice XTA reads with: XTA's ehRequestCookies tests, and the consent beside them. */
class EhSettingsTest {
    @Test
    fun a2400pxUconfigIsAddedWhenNoneIsThereAndTheConsentGoesAsTheSitesOwnNwCookieToo() {
        assertEquals("uconfig=xr_2400-ts_l-nw_1; nw=1", EhCookies.forRequest(""))
        assertEquals(
            "ipb_member_id=1; ipb_pass_hash=abc; uconfig=xr_2400-ts_l-nw_1; nw=1",
            EhCookies.forRequest("ipb_member_id=1; ipb_pass_hash=abc"),
        )
    }

    @Test
    fun aMobileSizedUconfigIsUpgradedWithoutDroppingOtherFlags() {
        assertEquals("dm_t-xr_2400-uh_y-ts_l-nw_1", EhCookies.uconfig("dm_t-xr_780-uh_y"))
        assertEquals("uconfig=dm_t-xr_2400-uh_y-ts_l-nw_1; nw=1", EhCookies.forRequest("uconfig=dm_t-xr_780-uh_y"))
    }

    @Test
    fun withoutTheConsentNeitherAnswerToTheWarningIsSent() {
        assertEquals("uconfig=xr_2400-ts_l", EhCookies.forRequest("", consent = false))
        assertEquals("ipb_member_id=1; uconfig=xr_2400-ts_l", EhSettings(cookies = "ipb_member_id=1", adultConsent = false).requestCookies)
    }

    @Test
    fun cookiesAreCleanedUpQuotesRepeatsAndJunkAlike() {
        assertEquals("a=3; b=2; uconfig=xr_2400-ts_l-nw_1; nw=1", EhCookies.forRequest(" a=\"1\"; ; b=2; =x; a=3; junk"))
    }

    @Test
    fun exhentaiIsReadOnlySignedIn() {
        assertEquals(EhSettings.EHENTAI, EhSettings(useExhentai = true).host)
        assertEquals(EhSettings.EHENTAI, EhSettings(cookies = "   ", useExhentai = true).host)
        assertEquals(EhSettings.EXHENTAI, EhSettings(cookies = "ipb_member_id=1", useExhentai = true).host)
        assertEquals(EhSettings.EHENTAI, EhSettings(cookies = "ipb_member_id=1").host)
        assertFalse(EhSettings(cookies = "  ").hasCookies)
    }

    @Test
    fun imageHeadersCarryTheAccountOnlyToTheSitesOwnHosts() {
        val settings = EhSettings(cookies = "ipb_member_id=1; ipb_pass_hash=abc")
        val original = settings.imageHeaders("https://e-hentai.org/fullimg/4116360/1/g8l82w4amxz/0001.png")
        assertTrue(original["Cookie"]!!.contains("xr_2400"))
        assertTrue(original["Cookie"]!!.contains("ipb_member_id=1"))
        assertEquals("https://e-hentai.org/", original["Referer"])
        assertEquals(EhClient.USER_AGENT, original["User-Agent"])
        assertTrue(settings.imageHeaders("https://s.exhentai.org/t/ab/cd/x.webp").containsKey("Cookie"), "ExHentai's thumbnails need the account")
        val volunteer = settings.imageHeaders("https://abc.hath.network:2333/h/abc/keystamp=1/page.webp")
        assertNull(volunteer["Cookie"], "an H@H server never sees the account")
        assertEquals("https://e-hentai.org/", volunteer["Referer"])
    }

    @Test
    fun theSitesHostsAreTheOnlyOnes() {
        assertTrue(EhSettings.isSiteUrl("https://e-hentai.org/?next=1"))
        assertTrue(EhSettings.isSiteUrl("https://api.e-hentai.org/api.php"))
        assertTrue(EhSettings.isSiteUrl("https://ehgt.org/a.webp"))
        assertFalse(EhSettings.isSiteUrl("https://e-hentai.org.example/"))
        assertFalse(EhSettings.isSiteUrl("https://notexhentai.org/"))
        assertFalse(EhSettings.isSiteUrl("not a url"))
    }

    @Test
    fun theCategoriesXtaStoresReadAsItWroteThem() {
        assertEquals(setOf(EhCategory.DOUJINSHI, EhCategory.ARTIST_CG), EhSettings.categoriesFrom("""["doujinshi","artistCg",3]"""))
        assertEquals(EhCategory.entries.toSet(), EhSettings.categoriesFrom(""))
        assertEquals(EhCategory.entries.toSet(), EhSettings.categoriesFrom(null))
        assertEquals(EhCategory.entries.toSet(), EhSettings.categoriesFrom("{broken"))
        assertEquals(emptySet(), EhSettings.categoriesFrom("[]"))
    }
}
