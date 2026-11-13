package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.PixivSection
import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

class PixivSettingsStoreTest {
    /** The app's settings as a plugin's host gives them: a change keeps only what it does to Pixiv's own keys. */
    private class App(initial: Settings?) {
        val state = MutableStateFlow(initial)
        var writes = 0

        suspend fun update(change: (Settings) -> Settings) {
            writes++
            val before = state.value ?: Settings()
            val after = change(before)
            state.value = (before.values.keys + after.values.keys).filter { it.startsWith("plugin.pixiv.") }
                .fold(before) { settings, key -> settings.with(key, after.values[key]) }
        }
    }

    private suspend fun App.holds(check: (Settings) -> Boolean) = withTimeout(5_000) { state.first { it != null && check(it) } }

    /** Runs [block] with a store over [app], stopping what the store started once it is done. */
    private fun withStore(app: App, block: suspend (PixivSettingsStore) -> Unit): Unit = runBlocking {
        val scope = CoroutineScope(coroutineContext + Job())
        try {
            block(PixivSettingsStore(app.state, app::update, scope))
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aChangeShowsAtOnceAndReachesTheAppsSettings() {
        val app = App(Settings().with("theme", PrefValue.Text("dark")))
        withStore(app) { store ->
            store.load()
            store.set(PixivPrefs.showR18, true)
            assertTrue(store[PixivPrefs.showR18])
            app.holds { it[PixivPrefs.showR18] }
            assertEquals(PrefValue.Text("dark"), app.state.value?.values?.get("theme"))
        }
    }

    @Test
    fun changesMadeBeforeTheSettingsArrivedAreKeptOverThem() {
        val app = App(null)
        withStore(app) { store ->
            store.set(PixivPrefs.hideAi, true)
            app.state.value = Settings().with(PixivPrefs.showR18, true)
            store.load()
            assertTrue(store[PixivPrefs.hideAi])
            assertTrue(store[PixivPrefs.showR18])
            app.holds { it[PixivPrefs.hideAi] && it[PixivPrefs.showR18] }
        }
    }

    @Test
    fun aBackupRestoredIntoTheAppShowsHere() {
        val app = App(Settings())
        withStore(app) { store ->
            store.load()
            app.state.value = Settings().with(PixivPrefs.startSection, PixivSection.SEARCH)
            withTimeout(5_000) { store.state.first { it[PixivPrefs.startSection] == PixivSection.SEARCH } }
            assertEquals(0, app.writes)
        }
    }

    @Test
    fun changesReachTheAppInTheOrderMade() {
        val app = App(Settings())
        withStore(app) { store ->
            store.load()
            (1..20).forEach { n -> store.update { it.with(PixivPrefs.searchHistory, it[PixivPrefs.searchHistory] + "q$n") } }
            app.holds { it[PixivPrefs.searchHistory].size == 20 }
            assertEquals((1..20).map { "q$it" }, store[PixivPrefs.searchHistory])
            assertFalse(store[PixivPrefs.showR18])
        }
    }

    @Test
    fun anAccessTokensExpiryReadsAsXtaWritesItOrAsAnInstant() {
        val local = LocalDateTime.of(2026, 3, 4, 5, 6, 7)
        assertEquals(local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(), PixivSettingsCredentials.expiryMillis("2026-03-04T05:06:07"))
        assertEquals(Instant.parse("2026-03-04T05:06:07Z").toEpochMilli(), PixivSettingsCredentials.expiryMillis("2026-03-04T05:06:07Z"))
        assertEquals(0, PixivSettingsCredentials.expiryMillis("soon"))
        assertEquals("", PixivSettingsCredentials.expiryText(0))
    }
}
