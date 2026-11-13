package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.networks.pixiv.PixivCredentialStore
import com.aimdi.xtanative.networks.pixiv.PixivCredentials
import com.aimdi.xtanative.networks.pixiv.PixivFilters
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Pref
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * The plugin's settings: XTA's Pixiv preferences ([PixivPrefs], every key
 * `plugin.pixiv.*`), kept in the app's settings ([stored], changed through
 * [write]), so XTA's backup restores them and the app's backup carries them,
 * secrets left out. A change shows here at once and reaches the app behind
 * it, in the order made, each as what all of Pixiv's keys then hold; one made
 * before the app's settings were read is applied over what they hold. What
 * the app's settings change otherwise (a backup restored) shows here once no
 * change of the plugin's is on its way.
 */
internal class PixivSettingsStore(
    private val stored: StateFlow<Settings?>,
    private val write: suspend ((Settings) -> Settings) -> Unit,
    private val scope: CoroutineScope,
) {
    private val mutable = MutableStateFlow(stored.value ?: Settings())
    val state: StateFlow<Settings> = mutable.asStateFlow()

    private val early = ArrayList<(Settings) -> Settings>()
    private val writes = Channel<Settings>(Channel.UNLIMITED)
    private val inFlight = AtomicInteger(0)

    @Volatile
    var loaded: Boolean = false
        private set

    init {
        scope.launch { for (next in writes) writeOwn(next) }
        scope.launch { stored.filterNotNull().collect(::adopt) }
    }

    /** The settings now. */
    val now: Settings get() = mutable.value

    operator fun <T> get(pref: Pref<T>): T = mutable.value[pref]

    /** Waits for the app's settings, with the changes made before they were read applied over them. */
    suspend fun load(): Settings {
        adopt(stored.filterNotNull().first())
        return mutable.value
    }

    fun <T> set(pref: Pref<T>, value: T) = update { it.with(pref, value) }

    fun update(edit: (Settings) -> Settings) {
        synchronized(early) { changeLocked(edit) }
    }

    private fun changeLocked(edit: (Settings) -> Settings) {
        val next = mutable.updateAndGet(edit)
        if (loaded) send(next) else early.add(edit)
    }

    /** The app's settings as they are now: taken while no change of the plugin's is on its way, the first time with the early changes over them. */
    private fun adopt(app: Settings) {
        synchronized(early) { adoptLocked(app) }
    }

    private fun adoptLocked(app: Settings) {
        if (loaded) {
            if (inFlight.get() == 0) mutable.value = app
            return
        }
        mutable.value = early.fold(app) { settings, edit -> edit(settings) }
        loaded = true
        if (early.isNotEmpty()) send(mutable.value)
        early.clear()
    }

    /** Hands [next] to the writer, in the order the changes were made (always under the lock). */
    private fun send(next: Settings) {
        inFlight.incrementAndGet()
        writes.trySend(next)
    }

    private suspend fun writeOwn(next: Settings) {
        try {
            write { next }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The app keeps what it had; the plugin shows the change until it is read again.
        } finally {
            inFlight.decrementAndGet()
        }
    }
}

/** The reader's Show R-18 and Hide AI choices, as each page is read. */
internal fun PixivSettingsStore.filters(): PixivFilters = PixivFilters(showR18 = this[PixivPrefs.showR18], hideAi = this[PixivPrefs.hideAi])

/**
 * The account the client reads as, in the preferences XTA keeps it in: the
 * refresh and access tokens, when the access token stops being sent (an
 * ISO-8601 time, as XTA writes it), whose they are and whether Premium. All
 * of them are secrets, never backed up.
 */
internal class PixivSettingsCredentials(private val settings: PixivSettingsStore) : PixivCredentialStore {
    override var credentials: PixivCredentials
        get() {
            val now = settings.now
            return PixivCredentials(
                refreshToken = now[PixivPrefs.refreshToken].trim(),
                accessToken = now[PixivPrefs.accessToken].trim(),
                accessExpiresAt = expiryMillis(now[PixivPrefs.accessExpiresAt]),
                userId = now[PixivPrefs.userId],
                isPremium = now[PixivPrefs.isPremium],
            )
        }
        set(value) = settings.update { stored ->
            stored.with(PixivPrefs.refreshToken, value.refreshToken)
                .with(PixivPrefs.accessToken, value.accessToken)
                .with(PixivPrefs.accessExpiresAt, expiryText(value.accessExpiresAt))
                .with(PixivPrefs.userId, value.userId)
                .with(PixivPrefs.isPremium, value.isPremium)
        }

    companion object {
        /** XTA writes a local time without a zone; an instant reads as one too. Nothing readable is expired. */
        fun expiryMillis(text: String): Long {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return 0
            return runCatching { Instant.parse(trimmed).toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(trimmed).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli() }.getOrNull()
                ?: 0
        }

        fun expiryText(millis: Long): String = if (millis <= 0) "" else Instant.ofEpochMilli(millis).toString()
    }
}
