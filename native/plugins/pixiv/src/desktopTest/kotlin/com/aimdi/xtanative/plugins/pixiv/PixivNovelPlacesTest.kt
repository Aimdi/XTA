package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow

/** The novel reader's places, as XTA keeps them: by passage, gated by the position and history switches, newest only. */
class PixivNovelPlacesTest {
    private fun store(initial: Settings = Settings()): Pair<PixivSettingsStore, CoroutineScope> {
        val scope = CoroutineScope(Dispatchers.Default + Job())
        return PixivSettingsStore(MutableStateFlow(initial), { _ -> }, scope) to scope
    }

    @Test
    fun aPlaceIsKeptByXtasNovelKey() {
        assertEquals("pixiv-novel:42", PixivNovelPlaces.key(42))
        val point = ArticleReadPoint(fraction = 0.4, paragraph = 7, leading = -12.0, updatedAt = 100)
        val kept = PixivNovelPlaces.kept(Settings(), 42, point)
        assertEquals(point, PixivNovelPlaces.placesIn(kept)["pixiv-novel:42"])
    }

    @Test
    fun noPlaceIsKeptOrGivenBackWhenPositionsAreOff() {
        val (settings, scope) = store()
        try {
            val places = PixivNovelPlaces(settings) { false }
            places.put(7, ArticleReadPoint(updatedAt = 1))
            assertNull(places.point(7))
            assertEquals(0, PixivNovelPlaces.placesIn(settings.now).size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun noPlaceIsKeptOrGivenBackWhileTheHistoryIsPaused() {
        val (settings, scope) = store(Settings().with(PixivPrefs.historyPaused, true))
        try {
            val places = PixivNovelPlaces(settings) { true }
            places.put(7, ArticleReadPoint(updatedAt = 1))
            assertNull(places.point(7))
            assertEquals(0, PixivNovelPlaces.placesIn(settings.now).size)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aPlaceComesBackAfterARestart() {
        val (settings, scope) = store()
        try {
            val point = ArticleReadPoint(fraction = 0.25, paragraph = 3, leading = 8.0, updatedAt = 5)
            PixivNovelPlaces(settings) { true }.put(7, point)
            assertEquals(point, PixivNovelPlaces(settings) { true }.point(7))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun onlyTheNewestPlacesStay() {
        var kept = Settings()
        repeat(ReadingJournal.LIMIT + 2) { i ->
            kept = PixivNovelPlaces.kept(kept, i.toLong(), ArticleReadPoint(updatedAt = i.toLong()))
        }
        val places = PixivNovelPlaces.placesIn(kept)
        assertEquals(ReadingJournal.LIMIT, places.size)
        assertNull(places["pixiv-novel:0"])
        assertNull(places["pixiv-novel:1"])
        assertEquals(true, places["pixiv-novel:2"] != null)
    }
}
