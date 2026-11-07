package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruPreset
import com.aimdi.xtanative.networks.booru.BooruPresets
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BooruSitesTest {
    private val board = BooruPreset("custom:board.example", "Board", BooruEngine.DANBOORU, "https://board.example")

    private fun preset(id: String): BooruPreset = BooruPresets.ALL.first { it.id == id }

    private fun chosen(settings: Settings, id: String): Settings = BooruSites.choose(settings, preset(id)).first

    @Test
    fun eachSiteKeepsItsOwnCapAndBlacklist() {
        val danbooru = BooruConfig.withMuted(BooruSites.withCap(Settings(), BooruRating.SENSITIVE), "comic")
        val yandere = chosen(danbooru, "yandere")
        assertEquals("https://yande.re", BooruConfig.host(yandere))
        assertEquals(BooruEngine.MOEBOORU, BooruConfig.engine(yandere))
        assertEquals(BooruRating.GENERAL, BooruConfig.maxRating(yandere))
        assertEquals(setOf("comic"), BooruConfig.muted(yandere))

        val back = chosen(BooruConfig.withoutMuted(yandere, "comic"), "danbooru")
        assertEquals(BooruRating.SENSITIVE, BooruConfig.maxRating(back))
        assertEquals(setOf("comic"), BooruConfig.muted(back))
        assertEquals(emptySet(), BooruConfig.muted(chosen(back, "yandere")))
    }

    @Test
    fun aMostlyExplicitSiteAsksBeforeItShowsAdultPosts() {
        val (settings, cap) = BooruSites.choose(Settings(), preset("rule34"))
        assertEquals(SiteCap.AskAdult("Rule34"), cap)
        assertEquals(BooruRating.GENERAL, BooruConfig.maxRating(settings))
    }

    @Test
    fun aReaderWhoAllowedAdultPostsGetsThemOnAMostlyExplicitSite() {
        val (settings, cap) = BooruSites.choose(BooruSites.withCap(Settings(), BooruRating.QUESTIONABLE), preset("rule34"))
        assertEquals(SiteCap.Settled, cap)
        assertEquals(BooruRating.EXPLICIT, BooruConfig.maxRating(settings))
    }

    @Test
    fun eachSiteKeepsItsOwnLoginAndSendsItNowhereElse() {
        val danbooru = BooruConfig.withCredential(BooruConfig.withCredential(Settings(), BooruPrefs.login, "maya"), BooruPrefs.apiKey, "secret")
        val gelbooru = chosen(danbooru, "gelbooru")
        assertEquals("", BooruConfig.client(gelbooru).site.login)
        assertEquals("", BooruConfig.client(gelbooru).site.apiKey)

        val back = chosen(gelbooru, "danbooru")
        assertEquals("maya", BooruConfig.client(back).site.login)
        assertEquals("secret", BooruConfig.client(back).site.apiKey)
    }

    @Test
    fun anAddedSiteIsKeptAsXtaKeepsItAndItsLinksAreKnown() {
        val added = BooruSites.withCustom(Settings(), BooruPreset("", "My board", BooruEngine.MOEBOORU, "https://board.example"))
        val site = BooruSites.custom(added).single()
        assertEquals("custom:board.example", site.id)
        assertEquals("My board", site.name)
        assertTrue(site in BooruSites.known(added))

        val renamed = BooruSites.withCustom(added, BooruPreset("", "Board", BooruEngine.MOEBOORU, "https://board.example/"))
        assertEquals(listOf("Board"), BooruSites.custom(renamed).map { it.name })
    }

    @Test
    fun anAddedSiteNamingNoEngineOrHostIsSkipped() {
        val rows = """[{"id":"a","name":"A","engine":"nope","host":"https://a.example"},{"name":"B","engine":"danbooru","host":""},{"engine":"danbooru","host":"b.example"}]"""
        val sites = BooruSites.custom(Settings().with(BooruPrefs.customSites, rows))
        assertEquals(listOf(BooruPreset("custom:b.example", "b.example", BooruEngine.DANBOORU, "https://b.example")), sites)
    }

    @Test
    fun removingTheSiteInUseGoesBackToTheFirstBuiltInOne() {
        val using = BooruSites.choose(BooruSites.withCustom(Settings(), board), board).first
        assertEquals("https://board.example", BooruConfig.host(using))

        val removed = BooruSites.withoutCustom(using, board)
        assertEquals(emptyList(), BooruSites.custom(removed))
        assertEquals(BooruPresets.DEFAULT.host, BooruConfig.host(removed))
        assertEquals(BooruPresets.DEFAULT.id, removed[BooruPrefs.preset])
        assertFalse("board.example" in removed[BooruPrefs.siteSettings])
    }

    @Test
    fun stayingOnASiteSwapsNothing() {
        val settings = BooruConfig.withMuted(Settings(), "gore")
        val host = BooruConfig.host(settings)
        assertEquals(settings to false, BooruSites.swap(settings, from = host, to = host))
    }

    @Test
    fun aRemovedSiteTakesItsLoginWithIt() {
        val withLogin = BooruConfig.withCredential(BooruConfig.withCredential(Settings(), BooruPrefs.login, "reader"), BooruPrefs.apiKey, "secret-key")
        val away = chosen(withLogin, "e621")
        assertTrue("secret-key" in away[BooruPrefs.siteCredentials])
        val forgotten = BooruSites.forget(away, BooruConfig.host(withLogin))
        assertFalse("secret-key" in forgotten[BooruPrefs.siteCredentials])
        assertFalse(BooruSites.swap(forgotten, from = BooruConfig.host(forgotten), to = BooruConfig.host(withLogin)).second)
    }
}
