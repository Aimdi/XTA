package com.aimdi.xtanative.ui.plugins

import com.aimdi.xtanative.store.settings.BlueskyPrefs
import com.aimdi.xtanative.store.settings.GeneralPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.StocksPrefs
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

class PluginSettingsTest {
    private val start = Settings().with(GeneralPrefs.cleanLinks, true).with(ThreadsPrefs.enabled, true)

    @Test
    fun aPluginChangesItsOwnPreferencesAndNoOneElses(): Unit = runBlocking {
        val app = MutableStateFlow<Settings?>(start)
        val bluesky = PluginSettings("bluesky", app) { change -> app.value = change(app.value!!) }
        bluesky.update {
            it.with(BlueskyPrefs.instance, "https://bsky.example")
                .with(GeneralPrefs.cleanLinks, false)
                .with(ThreadsPrefs.enabled, false)
        }
        val now = app.value!!
        assertEquals("https://bsky.example", now[BlueskyPrefs.instance])
        assertEquals(true, now[GeneralPrefs.cleanLinks], "the app's own settings stay as they were")
        assertEquals(true, now[ThreadsPrefs.enabled], "another network's stay as they were")
    }

    @Test
    fun theKeysANetworkKeepsWhereXtaKeptThemAreItsOwnToo(): Unit = runBlocking {
        val app = MutableStateFlow<Settings?>(start)
        val stocks = PluginSettings("stocks", app) { change -> app.value = change(app.value!!) }
        stocks.update {
            it.with(StocksPrefs.tickerChart, false)
                .with(StocksPrefs.tickerInstruments, listOf("ETH=stock"))
                .with(GeneralPrefs.cleanLinks, false)
        }
        val now = app.value!!
        assertEquals(setOf("other.ticker_chart", "other.ticker_instruments"), PluginSettings.keysOf("stocks"))
        assertEquals(false, now[StocksPrefs.tickerChart])
        assertEquals(listOf("ETH=stock"), now[StocksPrefs.tickerInstruments])
        assertEquals(true, now[GeneralPrefs.cleanLinks], "the app's own settings stay as they were")
        assertEquals(emptySet(), PluginSettings.keysOf("bluesky"), "Bluesky keeps everything under its prefix")
    }

    @Test
    fun aPluginMayTakeItsOwnPreferenceAway() {
        val set = start.with(BlueskyPrefs.instance, "https://bsky.example")
        val after = PluginSettings.ownChanges(set, set.without(BlueskyPrefs.instance), PluginSettings.prefixOf("bluesky"))
        assertEquals(false, after.isSet(BlueskyPrefs.instance))
        assertEquals("plugin.bluesky.", PluginSettings.prefixOf("bluesky"))
    }
}
