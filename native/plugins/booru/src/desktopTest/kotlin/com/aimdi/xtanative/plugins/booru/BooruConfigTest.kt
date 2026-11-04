package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruEngine
import com.aimdi.xtanative.networks.booru.BooruRating
import com.aimdi.xtanative.networks.booru.BooruTagSort
import com.aimdi.xtanative.networks.booru.post
import com.aimdi.xtanative.store.settings.BooruPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import com.aimdi.xtanative.store.settings.BooruTagSort as StoredTagSort

class BooruConfigTest {
    private val danbooru = "https://danbooru.donmai.us"
    private val gelbooru = "https://gelbooru.com"

    private fun on(host: String, settings: Settings = Settings()): Settings = settings.with(BooruPrefs.host, host)

    private fun withLogin(settings: Settings, login: String, key: String): Settings =
        BooruConfig.withCredential(BooruConfig.withCredential(settings, BooruPrefs.login, login), BooruPrefs.apiKey, key)

    @Test
    fun aFreshAppReadsDanbooruUnderSafeSearch() {
        val client = BooruConfig.client(Settings())
        assertEquals(BooruEngine.DANBOORU, client.engine)
        assertEquals(danbooru, client.host)
        assertEquals(BooruRating.GENERAL, client.maxRating)
        assertEquals(emptySet(), client.muted)
        assertNull(client.credentials)
    }

    @Test
    fun aLoginGoesOnlyToTheHostItWasEnteredFor() {
        val saved = withLogin(Settings(), "maya", "secret")
        assertEquals(danbooru, BooruConfig.credentialsHost(saved))
        assertEquals("maya", BooruConfig.client(saved).site.login)
        assertNull(BooruConfig.credentialsElsewhere(saved))

        val elsewhere = on(gelbooru, saved)
        assertEquals("", BooruConfig.credential(elsewhere, BooruPrefs.login))
        assertEquals("", BooruConfig.client(elsewhere).site.login)
        assertEquals("", BooruConfig.client(elsewhere).site.apiKey)
        assertEquals(danbooru, BooruConfig.credentialsElsewhere(elsewhere))
    }

    @Test
    fun aKeyForAnotherHostReplacesThePairBeforeIt() {
        val next = BooruConfig.withCredential(on(gelbooru, withLogin(Settings(), "maya", "secret")), BooruPrefs.apiKey, "other")
        assertEquals("", next[BooruPrefs.login])
        assertEquals("other", next[BooruPrefs.apiKey])
        assertEquals(gelbooru, BooruConfig.credentialsHost(next))
    }

    @Test
    fun credentialsFromBeforeHostsWereNamedBelongToTheHostInUse() {
        val old = on(gelbooru).with(BooruPrefs.login, "1234").with(BooruPrefs.apiKey, "key")
        assertEquals(gelbooru, BooruConfig.credentialsHost(BooruConfig.withCredentialsHost(old)))
        assertEquals(Settings(), BooruConfig.withCredentialsHost(Settings()))
    }

    @Test
    fun theBlacklistKeepsEachEntryOnceInItsNormalForm() {
        val muted = BooruConfig.withMuted(BooruConfig.withMuted(Settings(), "  Blue_Eyes   Solo "), "blue_eyes solo")
        assertEquals(setOf("blue_eyes solo"), BooruConfig.muted(muted))
        val more = BooruConfig.withMuted(muted, "comic")
        assertEquals("[\"blue_eyes solo\",\"comic\"]", more[BooruPrefs.mutedTags])
        assertEquals(setOf("comic"), BooruConfig.muted(BooruConfig.withoutMuted(more, "Blue_Eyes Solo")))
        assertEquals(more, BooruConfig.withMuted(more, "   "))
    }

    @Test
    fun theStoredChoicesReadBack() {
        BooruRating.entries.forEach { assertEquals(it, BooruConfig.maxRating(Settings().with(BooruPrefs.maxRating, BooruConfig.stored(it)))) }
        BooruEngine.entries.forEach { assertEquals(it, BooruConfig.engine(Settings().with(BooruPrefs.engine, BooruConfig.stored(it)))) }
        assertEquals(BooruTagSort.COUNT, BooruConfig.tagSort(Settings().with(BooruPrefs.tagSort, StoredTagSort.COUNT)))
        assertEquals(BooruTagSort.NAME, BooruConfig.tagSort(Settings()))
    }

    @Test
    fun theGridFitsTheWidthUnlessColumnsWereChosen() {
        val fitting = BooruConfig.display(Settings())
        assertEquals(2, fitting.columnsFor(360f, largeText = false))
        assertEquals(1, fitting.columnsFor(360f, largeText = true))
        assertEquals(4, fitting.columnsFor(1200f, largeText = false))
        assertEquals(5, BooruConfig.display(Settings().with(BooruPrefs.gridColumns, 5)).columnsFor(360f, largeText = false))
    }

    @Test
    fun adultPostsAreBlurredOnlyWhenAsked() {
        val explicit = post(rating = BooruRating.EXPLICIT)
        assertFalse(BooruDisplay().blurs(explicit))
        assertTrue(BooruDisplay(blurExplicit = true).blurs(explicit))
        assertFalse(BooruDisplay(blurExplicit = true).blurs(post(rating = BooruRating.SENSITIVE)))
    }
}
