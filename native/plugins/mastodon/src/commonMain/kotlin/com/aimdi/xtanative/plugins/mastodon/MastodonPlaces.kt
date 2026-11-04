package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.networks.mastodon.MastodonReadPoint
import com.aimdi.xtanative.networks.mastodon.MastodonReadingCodec
import com.aimdi.xtanative.networks.mastodon.MastodonReadingPlaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Where the reader left each timeline, on Home and in the client, as XTA's
 * MastodonReadingStore keeps it: in the settings (`reading.v1`), for the
 * reader's instances of the moment, while Remember reading position is on.
 * A timeline's place is restored once a session, the first time it shows
 * with nothing read yet; changes are written a moment after they settle.
 */
internal class MastodonPlaces(private val app: MastodonApp, private val scope: CoroutineScope) {
    private var loadedFor: String? = null
    private var places = MastodonReadingPlaces()
    private val restored = HashSet<String>()
    private val positioned = HashSet<String>()
    private var writing: Job? = null

    /** The places kept for the reader's instances now; nothing until the settings have been read. */
    @Synchronized
    fun current(): MastodonReadingPlaces {
        if (app.host.settings.state.value == null) return MastodonReadingPlaces()
        val view = app.prefs.value
        val scopeKey = MastodonReadingCodec.scope(view.configured)
        if (loadedFor != scopeKey) {
            loadedFor = scopeKey
            places = if (view.keepPlaces) MastodonReadingCodec.parse(view.places, scopeKey) else MastodonReadingPlaces()
            restored.clear()
            positioned.clear()
        }
        return places
    }

    /** [slot]'s place, the first time it is asked for this session. */
    @Synchronized
    fun takeRestore(slot: String): MastodonReadPoint? = current().points[slot]?.takeIf { restored.add(slot) }

    /** Where [slot]'s list opens, the first time it is drawn this session. */
    @Synchronized
    fun takePosition(slot: String): MastodonReadPoint? = current().points[slot]?.takeIf { positioned.add(slot) }

    fun select(tab: Int, people: Boolean) = update { it.copy(tab = tab, people = people) }

    fun remember(slot: String, point: MastodonReadPoint) {
        if (point.posts.isNotEmpty()) update { it.copy(points = it.points - slot + (slot to point)) }
    }

    private fun update(change: (MastodonReadingPlaces) -> MastodonReadingPlaces) {
        val scopeKey = synchronized(this) {
            val now = current()
            val next = change(now)
            if (next == now || loadedFor == null) return
            places = next
            loadedFor
        } ?: return
        writing?.cancel()
        writing = scope.launch {
            delay(WRITE_AFTER_MS)
            val text = if (app.prefs.value.keepPlaces) MastodonReadingCodec.encode(synchronized(this@MastodonPlaces) { places }, scopeKey) else ""
            app.host.settings.update { MastodonSettings.withPlaces(it, text) }
        }
    }

    private companion object {
        const val WRITE_AFTER_MS = 350L
    }
}
