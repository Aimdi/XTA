package com.aimdi.xtanative.plugins.ehviewer

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.settings.EhViewerPrefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The account's pasted cookies (`ipb_member_id`, `ipb_pass_hash`, `igneous`),
 * the sign-in XTA keeps under [EhViewerPrefs.cookies], here in the plugin's
 * own folder rather than the app's settings: no backup carries them, and
 * uninstalling erases them. [now] is what the client reads with: empty until
 * [load] has read the file.
 */
internal class EhCookies(private val file: StoreFile) {
    private val mutable = MutableStateFlow<String?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<String?> = mutable.asStateFlow()

    val now: String get() = mutable.value.orEmpty()

    private val mutex = Mutex()

    suspend fun load(): String = mutex.withLock { loaded() }

    /** [cookies], trimmed, as XTA keeps what was typed; empty signs out. */
    suspend fun set(cookies: String) = mutex.withLock {
        val value = cookies.trim()
        file.write(encode(value))
        mutable.value = value
    }

    private suspend fun loaded(): String = mutable.value ?: decode(file.read().orEmpty()).also { mutable.value = it }

    companion object {
        private val KEY = EhViewerPrefs.cookies.key

        fun encode(cookies: String): String = JsonObject(mapOf(KEY to JsonPrimitive(cookies))).toString()

        fun decode(text: String): String = J.parse(text)[KEY].string.orEmpty().trim()
    }
}
