package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** The app's settings once they have been read: nothing is taken for missing before then. */
internal suspend fun PluginSettings.loaded(): Settings = state.filterNotNull().first()

/** [pref]'s value, once the app's settings have been read. */
internal suspend fun <T> PluginSettings.get(pref: Pref<T>): T = loaded()[pref]

/** [pref] set to [value]: one of the plugin's own, `plugin.substack.…`. */
internal suspend fun <T> PluginSettings.set(pref: Pref<T>, value: T) = update { it.with(pref, value) }

/** [pref]'s value, then each time it changes. */
internal fun <T> PluginSettings.observe(pref: Pref<T>): Flow<T> = state.filterNotNull().map { it[pref] }.distinctUntilChanged()
