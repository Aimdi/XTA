package com.aimdi.xtanative.data.plugins

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * What the app keeps of the plugin store itself. Where a plugin sits on
 * Home is in the app's settings (Settings' `home.feed_strip_plugins`), with
 * the rest of Home's strip.
 */
data class PluginPrefsState(
    /** Whether the store offers private plugins. */
    val showPrivate: Boolean = false,
)

/** [PluginPrefsState] in one file of the app's store. */
class PluginPrefs(private val file: StoreFile) {
    private val mutable = MutableStateFlow(PluginPrefsState())
    val state: StateFlow<PluginPrefsState> = mutable.asStateFlow()

    private val mutex = Mutex()
    private var loaded = false

    suspend fun load() = mutex.withLock { loadOnce() }

    suspend fun update(edit: (PluginPrefsState) -> PluginPrefsState) = mutex.withLock {
        loadOnce()
        val next = edit(mutable.value)
        mutable.value = next
        file.write(encode(next))
    }

    private suspend fun loadOnce() {
        if (loaded) return
        mutable.value = decode(file.read().orEmpty())
        loaded = true
    }

    private companion object {
        fun encode(state: PluginPrefsState): String = buildJsonObject {
            put("v", 1)
            put("showPrivate", state.showPrivate)
        }.toString()

        fun decode(text: String): PluginPrefsState = PluginPrefsState(showPrivate = J.parse(text)["showPrivate"].boolean ?: false)
    }
}
