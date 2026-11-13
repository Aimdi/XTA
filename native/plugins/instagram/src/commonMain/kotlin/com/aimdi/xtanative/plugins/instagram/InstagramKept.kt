package com.aimdi.xtanative.plugins.instagram

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.instagram.InstagramAuthor
import com.aimdi.xtanative.networks.instagram.InstagramProfile
import com.aimdi.xtanative.store.StoreFile
import com.aimdi.xtanative.store.backup.InstagramSubscriptionRow
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.InstagramPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.time.Instant
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

/**
 * The accounts followed on this device (XTA's `instagram_subscription` rows,
 * its backup's `instagramSubscriptions`), in the plugin's folder: Instagram
 * is never told. The newest follow first, as XTA lists them; null until the
 * file has been read.
 */
internal class InstagramFollows(private val file: StoreFile, private val now: () -> Instant = Instant::now) {
    private val mutable = MutableStateFlow<List<InstagramSubscriptionRow>?>(null)
    val state: StateFlow<List<InstagramSubscriptionRow>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<InstagramSubscriptionRow> = mutex.withLock { loaded() }

    /** The file read again, as a pull on Accounts reads XTA's rows again (InstagramFollowsStore.load). */
    suspend fun reload(): List<InstagramSubscriptionRow> = mutex.withLock { newestFirst(decode(file.read().orEmpty())).also { mutable.value = it } }

    /** Follows the account [profile] describes, under its numeric id. */
    suspend fun follow(profile: InstagramProfile) =
        put(InstagramSubscriptionRow(profile.username.lowercase(), profile.id, profile.displayName, profile.avatarUrl, profile.biography, created()))

    /** Follows a post's author; without its numeric id, its username stands in, as XTA keeps it. */
    suspend fun follow(author: InstagramAuthor) {
        val handle = author.username.trim().lowercase()
        if (handle.isEmpty()) return
        put(InstagramSubscriptionRow(handle, author.pk.ifEmpty { handle }, author.displayName, author.avatarUrl, createdAt = created()))
    }

    suspend fun unfollow(handle: String) = mutex.withLock { save(loaded().filterNot { it.id == handle.lowercase() }) }

    /** The rows as XTA's backup writes `instagramSubscriptions`. */
    suspend fun export(): String = mutex.withLock { encode(loaded()) }

    suspend fun import(text: String) = mutex.withLock { save(newestFirst(decode(text))) }

    private suspend fun put(row: InstagramSubscriptionRow) = mutex.withLock { save(listOf(row) + loaded().filterNot { it.id == row.id }) }

    private fun created(): String = XtaTime.iso(now())

    private suspend fun loaded(): List<InstagramSubscriptionRow> = mutable.value ?: newestFirst(decode(file.read().orEmpty())).also { mutable.value = it }

    private suspend fun save(rows: List<InstagramSubscriptionRow>) {
        val next = rows.distinctBy { it.id }
        file.write(encode(next))
        mutable.value = next
    }

    companion object {
        fun follows(rows: List<InstagramSubscriptionRow>?, handle: String): Boolean = rows.orEmpty().any { it.id == handle.trim().lowercase() }

        /** As XTA's `ORDER BY created_at DESC`; a row without a time last. */
        fun newestFirst(rows: List<InstagramSubscriptionRow>): List<InstagramSubscriptionRow> =
            rows.sortedByDescending { XtaTime.parse(it.createdAt) ?: Instant.MIN }

        fun encode(rows: List<InstagramSubscriptionRow>): String = JsonArray(rows.map { it.toJson() }).toString()

        fun decode(text: String): List<InstagramSubscriptionRow> = J.parse(text).list.mapNotNull { InstagramSubscriptionRow.from(it) }
    }
}

/**
 * Likes kept on this device in XTA's `plugin.instagram.liked_posts`, by post
 * id: Instagram is never told. The newest like first, as many as XTA keeps.
 */
internal object InstagramLikes {
    const val CAP = 200

    fun ids(settings: Settings): List<String> = J.parse(settings[InstagramPrefs.likedPosts]).list.mapNotNull { it.string }

    /** [settings] with [id] liked, or no longer. */
    fun toggled(settings: Settings, id: String): Settings =
        settings.with(InstagramPrefs.likedPosts, JsonArray(toggled(ids(settings), id).map(::JsonPrimitive)).toString())

    fun toggled(liked: List<String>, id: String): List<String> =
        if (id in liked) liked - id else (listOf(id) + liked).take(CAP)
}

/** The handles and queries searched lately, as XTA's InstagramSearchHistoryStore keeps them: lower case, without an @, twenty. */
internal object InstagramSearchHistory {
    const val CAP = 20

    fun remembered(history: List<String>, query: String): List<String> {
        val key = query.trim().removePrefix("@").lowercase()
        if (key.isEmpty()) return history
        return (listOf(key) + history.filter { it != key }).take(CAP)
    }
}
