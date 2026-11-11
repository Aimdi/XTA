package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.tiktok.TikTokProfile
import com.aimdi.xtanative.networks.tiktok.TikTokSession
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.TikTokSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.TikTokPrefs
import com.aimdi.xtanative.ui.plugins.PluginSearchHistory
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The accounts followed on this device (XTA's TikTokFollowsStore, its
 * `tiktok_subscription` rows): one per handle, lowercased, the newest first.
 * TikTok is never told. Kept in the plugin's folder in the rows XTA's backup
 * writes for them (`tiktokSubscriptions`).
 */
internal class TikTokFollows(private val file: StoreFile, private val clock: () -> Instant = Instant::now) {
    private val mutable = MutableStateFlow<List<TikTokSubscriptionRow>?>(null)

    /** Null until the file has been read. */
    val state: StateFlow<List<TikTokSubscriptionRow>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<TikTokSubscriptionRow> = mutex.withLock { loaded() }

    /** The file read again, as a pull on Accounts reads XTA's rows again (TikTokFollowsStore.load). */
    suspend fun reload(): List<TikTokSubscriptionRow> = mutex.withLock { decode(file.read().orEmpty()).also { mutable.value = it } }

    fun isFollowed(handle: String): Boolean = mutable.value.orEmpty().any { it.id == handle.lowercase() }

    /** [profile] followed from now, in place of any older row of it. */
    suspend fun follow(profile: TikTokProfile) = change { rows ->
        val row = TikTokSubscriptionRow(
            id = profile.uniqueId.lowercase(),
            secUid = profile.secUid,
            name = profile.displayName,
            avatarUrl = profile.avatarUrl,
            signature = profile.signature,
            createdAt = XtaTime.iso(clock()),
        )
        listOf(row) + rows.filterNot { it.id == row.id }
    }

    suspend fun unfollow(handle: String) = change { rows -> rows.filterNot { it.id == handle.lowercase() } }

    /** The rows as XTA's backup writes them. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    /** A backup's rows in place of these. */
    suspend fun import(text: String) = mutex.withLock { save(decode(text)) }

    private suspend fun change(edit: (List<TikTokSubscriptionRow>) -> List<TikTokSubscriptionRow>) = mutex.withLock { save(edit(loaded())) }

    private suspend fun loaded(): List<TikTokSubscriptionRow> = mutable.value ?: decode(file.read().orEmpty()).also { mutable.value = it }

    private suspend fun save(rows: List<TikTokSubscriptionRow>) {
        val next = ordered(rows)
        file.write(encode(next))
        mutable.value = next
    }

    companion object {
        /** As XTA lists them, `ORDER BY created_at DESC`, each handle once. */
        fun ordered(rows: List<TikTokSubscriptionRow>): List<TikTokSubscriptionRow> =
            rows.distinctBy { it.id }.sortedByDescending { XtaTime.parse(it.createdAt) ?: Instant.EPOCH }

        fun encode(rows: List<TikTokSubscriptionRow>): String = JsonArray(rows.map { it.toJson() }).toString()

        fun decode(text: String): List<TikTokSubscriptionRow> = ordered(J.parse(text).list.mapNotNull { TikTokSubscriptionRow.from(it) })
    }
}

/**
 * The guest session TikTok hands out (its cookies and the made-up device id
 * the web API is asked with), which XTA keeps under [TikTokPrefs.cookies] and
 * [TikTokPrefs.deviceId]: here in the plugin's own folder, as a sign-in's
 * cookies are kept, so no backup carries them and uninstalling erases them.
 */
internal class TikTokSessionFile(private val file: StoreFile) {
    suspend fun load(): TikTokSession = decode(file.read().orEmpty())

    suspend fun save(session: TikTokSession) = file.write(encode(session))

    companion object {
        fun encode(session: TikTokSession): String = JsonObject(
            mapOf(TikTokPrefs.cookies.key to JsonPrimitive(session.cookies), TikTokPrefs.deviceId.key to JsonPrimitive(session.deviceId)),
        ).toString()

        fun decode(text: String): TikTokSession {
            val root = J.parse(text)
            return TikTokSession(root[TikTokPrefs.cookies.key].string.orEmpty(), root[TikTokPrefs.deviceId.key].string.orEmpty())
        }
    }
}

/** The hearts and searches kept on the device, as XTA keeps them in its TikTok preferences. */
internal object TikTokMarks {
    /** XTA keeps the newest two hundred hearts. */
    const val LIKES_KEPT = 200

    fun likes(settings: Settings): List<String> = J.parse(settings[TikTokPrefs.likedPosts]).list.mapNotNull { it.string }

    /** [id] hearted, or no longer: nothing is sent to TikTok. */
    fun withLikeToggled(settings: Settings, id: String): Settings {
        val now = likes(settings)
        val next = if (id in now) now - id else (now + id).takeLast(LIKES_KEPT)
        return settings.with(TikTokPrefs.likedPosts, JsonArray(next.map(::JsonPrimitive)).toString())
    }

    /** A search or a profile opened, first among the recent ones: a handle without its "@", lowercased, as XTA keeps them. */
    fun withSearch(settings: Settings, query: String): Settings {
        val key = query.trim().removePrefix("@").lowercase()
        if (key.isEmpty()) return settings
        return settings.with(TikTokPrefs.searchHistory, PluginSearchHistory.remembered(settings[TikTokPrefs.searchHistory], key))
    }
}
